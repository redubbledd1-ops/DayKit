package com.dd.daykit

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.dd.daykit.database.AppDatabase
import com.dd.daykit.database.CustomAlarmSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manager for custom alarm sounds
 * Handles file operations, validation, and database management
 */
class CustomSoundManager(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.customAlarmSoundDao()
    
    companion object {
        private const val TAG = "CustomSoundManager"
        private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
        private const val CUSTOM_SOUNDS_DIR = "alarm_sounds/custom"
        
        private val SUPPORTED_MIME_TYPES = setOf(
            "audio/mpeg",      // MP3
            "audio/mp3",       // MP3 alternative
            "audio/wav",       // WAV
            "audio/x-wav",     // WAV alternative
            "audio/ogg",       // OGG
            "audio/vorbis",    // OGG Vorbis
            "audio/mp4",       // M4A
            "audio/aac"        // AAC
        )
        
        private val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "wav", "ogg", "m4a", "aac"
        )
    }
    
    /**
     * Result of adding a custom sound
     */
    sealed class AddSoundResult {
        data class Success(val sound: CustomAlarmSound) : AddSoundResult()
        data class Error(val message: String) : AddSoundResult()
    }
    
    /**
     * Get the directory for custom sounds
     */
    private fun getCustomSoundsDirectory(): File {
        val dir = File(context.filesDir, CUSTOM_SOUNDS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Get all custom sounds from database
     */
    suspend fun getAllSounds(): List<CustomAlarmSound> = withContext(Dispatchers.IO) {
        dao.getAllSoundsList()
    }
    
    /**
     * Get sound by ID
     */
    suspend fun getSoundById(id: Long): CustomAlarmSound? = withContext(Dispatchers.IO) {
        dao.getSoundById(id)
    }
    
    /**
     * Get file for a custom sound
     */
    fun getFileForSound(sound: CustomAlarmSound): File {
        return File(getCustomSoundsDirectory(), sound.storedFilename)
    }
    
    /**
     * Get URI for a custom sound file
     */
    fun getUriForSound(sound: CustomAlarmSound): Uri {
        return Uri.fromFile(getFileForSound(sound))
    }
    
    /**
     * Add a custom sound from URI with SAF support
     * Handles persistable URI permissions, validation, and file copying
     */
    suspend fun addCustomSound(uri: Uri): AddSoundResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Adding custom sound from URI: $uri")
            
            // Step 1: Take persistable URI permission (SAF requirement for Android 11+)
            val permissionGranted = takePersistablePermission(uri)
            if (!permissionGranted) {
                Log.w(TAG, "Could not take persistable permission, continuing anyway")
            }
            
            // Step 2: Get and validate file info
            val fileInfo = getFileInfo(uri) ?: return@withContext AddSoundResult.Error(
                "Kan bestandsinformatie niet ophalen. Controleer of het bestand toegankelijk is."
            )
            
            Log.d(TAG, "File info: name=${fileInfo.displayName}, size=${fileInfo.size}, mime=${fileInfo.mimeType}")
            
            // Step 3: Validate file size (must be under 10MB)
            if (fileInfo.size <= 0) {
                return@withContext AddSoundResult.Error(
                    "Bestand is leeg of grootte kan niet worden bepaald"
                )
            }
            
            if (fileInfo.size > MAX_FILE_SIZE_BYTES) {
                val sizeMB = fileInfo.size / 1024.0 / 1024.0
                return@withContext AddSoundResult.Error(
                    "Bestand is te groot (${String.format("%.1f", sizeMB)} MB). Maximum is ${MAX_FILE_SIZE_BYTES / 1024 / 1024} MB"
                )
            }
            
            // Step 4: Validate MIME type
            val mimeType = fileInfo.mimeType.lowercase()
            if (mimeType !in SUPPORTED_MIME_TYPES) {
                return@withContext AddSoundResult.Error(
                    "Bestandstype '$mimeType' niet ondersteund. Gebruik MP3, WAV, OGG, M4A of AAC"
                )
            }
            
            // Step 5: Validate file extension
            val extension = fileInfo.displayName.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) {
                return@withContext AddSoundResult.Error(
                    "Bestand heeft geen extensie. Gebruik een geldig audiobestand"
                )
            }
            
            if (extension !in SUPPORTED_EXTENSIONS) {
                return@withContext AddSoundResult.Error(
                    "Bestandsextensie '.$extension' niet ondersteund. Gebruik: ${SUPPORTED_EXTENSIONS.joinToString(", ")}"
                )
            }
            
            // Step 6: Generate unique filename and prepare destination
            val storedFilename = "${UUID.randomUUID()}.$extension"
            val destFile = File(getCustomSoundsDirectory(), storedFilename)
            
            Log.d(TAG, "Copying file to: ${destFile.absolutePath}")
            
            // Step 7: Copy file to app directory with progress tracking
            val bytesCopied = copyFileWithValidation(uri, destFile, fileInfo.size)
            if (bytesCopied != fileInfo.size) {
                // Cleanup partial file
                destFile.delete()
                return@withContext AddSoundResult.Error(
                    "Bestand kopiëren mislukt. Verwacht ${fileInfo.size} bytes, gekopieerd $bytesCopied bytes"
                )
            }
            
            // Step 8: Verify file was copied correctly
            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext AddSoundResult.Error(
                    "Bestand kopiëren mislukt. Bestand niet gevonden na kopiëren"
                )
            }
            
            Log.d(TAG, "File copied successfully: ${destFile.length()} bytes")
            
            // Step 9: Get audio duration
            val duration = getDuration(destFile)
            if (duration <= 0) {
                Log.w(TAG, "Could not determine audio duration")
            }
            
            // Step 10: Create database entry with all metadata
            val displayName = fileInfo.displayName.substringBeforeLast('.')
            val sound = CustomAlarmSound(
                displayName = displayName,
                originalFilename = fileInfo.displayName,
                storedFilename = storedFilename,
                originalUri = uri.toString(),
                durationMs = duration,
                fileSizeBytes = destFile.length(), // Use actual copied file size
                mimeType = fileInfo.mimeType
            )
            
            // Step 11: Persist to database
            val id = dao.insert(sound)
            val savedSound = sound.copy(id = id)
            
            Log.d(TAG, "Successfully added custom sound: ${savedSound.displayName} (ID: $id)")
            AddSoundResult.Success(savedSound)
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception adding custom sound", e)
            AddSoundResult.Error("Geen toegang tot bestand. Probeer opnieuw en geef toegang.")
        } catch (e: java.io.FileNotFoundException) {
            Log.e(TAG, "File not found exception", e)
            AddSoundResult.Error("Bestand niet gevonden. Controleer of het bestand nog bestaat.")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IO exception adding custom sound", e)
            AddSoundResult.Error("Fout bij lezen/schrijven bestand: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error adding custom sound", e)
            AddSoundResult.Error("Onverwachte fout: ${e.message}")
        }
    }
    
    /**
     * Voegt een geluid toe dat al als bytes is gedownload (bv. van Home Assistant),
     * i.p.v. via een content:// URI. Slaat het bestand lokaal op en registreert het
     * in de database, net als een normale import.
     */
    suspend fun addDownloadedSound(
        displayName: String,
        originalFilename: String,
        bytes: ByteArray
    ): AddSoundResult = withContext(Dispatchers.IO) {
        try {
            if (bytes.isEmpty()) {
                return@withContext AddSoundResult.Error("Leeg bestand ontvangen")
            }

            // Behoud de echte extensie/mimetype van het gedownloade bestand i.p.v. altijd
            // ".mp3"/"audio/mpeg" aan te nemen - HA kan sinds de extensie-fix ook wav/ogg/m4a/aac
            // serveren (zie SoundHaSync.safeUploadedFilename), en een verkeerd label hier zou
            // hetzelfde afspeelprobleem terugbrengen dat die fix juist oploste.
            val extension = originalFilename.substringAfterLast('.', "mp3").lowercase()
                .let { if (it in SUPPORTED_EXTENSIONS) it else "mp3" }
            val mimeType = when (extension) {
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                else -> "audio/mpeg"
            }

            val storedFilename = "${UUID.randomUUID()}.$extension"
            val destFile = File(getCustomSoundsDirectory(), storedFilename)
            FileOutputStream(destFile).use { it.write(bytes) }

            val duration = getDuration(destFile)

            val sound = CustomAlarmSound(
                displayName = displayName,
                originalFilename = originalFilename,
                storedFilename = storedFilename,
                originalUri = "",
                durationMs = duration,
                fileSizeBytes = destFile.length(),
                mimeType = mimeType
            )

            val id = dao.insert(sound)
            val savedSound = sound.copy(id = id)
            Log.d(TAG, "Downloaded sound opgeslagen: ${savedSound.displayName} (ID: $id)")
            AddSoundResult.Success(savedSound)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding downloaded sound", e)
            AddSoundResult.Error("Fout bij opslaan gedownload geluid: ${e.message}")
        }
    }

    /**
     * Check of er al een geluid met deze originalFilename bestaat (voorkomt dubbele
     * downloads van hetzelfde HA-bestand).
     */
    suspend fun hasSoundWithOriginalFilename(filename: String): Boolean = withContext(Dispatchers.IO) {
        dao.getAllSoundsList().any { it.originalFilename == filename }
    }

    /**
     * Delete a custom sound
     */
    suspend fun deleteSound(sound: CustomAlarmSound): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete file
            val file = getFileForSound(sound)
            if (file.exists()) {
                file.delete()
            }
            
            // Delete from database
            dao.delete(sound)
            
            Log.d(TAG, "Successfully deleted custom sound: ${sound.displayName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting custom sound", e)
            false
        }
    }
    
    /**
     * Take persistable URI permission for SAF (Storage Access Framework)
     * Required for Android 11+ to maintain access to user-selected files
     */
    private fun takePersistablePermission(uri: Uri): Boolean {
        return try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Persistable permission granted for: $uri")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not take persistable permission (may not be available): ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error taking persistable permission: ${e.message}")
            false
        }
    }
    
    /**
     * Copy file from URI to destination with validation
     * Returns number of bytes copied
     */
    private fun copyFileWithValidation(uri: Uri, destFile: File, expectedSize: Long): Long {
        var bytesCopied = 0L
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    
                    // Safety check: prevent copying files larger than expected
                    if (bytesCopied > expectedSize + 1024) { // Allow 1KB tolerance
                        Log.w(TAG, "File size exceeded expected size during copy")
                        break
                    }
                }
                
                output.flush()
            }
        } ?: throw java.io.IOException("Could not open input stream from URI")
        
        return bytesCopied
    }
    
    /**
     * Get file info from URI using SAF
     * Works with content:// URIs from file picker
     */
    private fun getFileInfo(uri: Uri): FileInfo? {
        return try {
            Log.d(TAG, "Querying file info for URI: $uri")
            
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    
                    val displayName = if (displayNameIndex >= 0) {
                        cursor.getString(displayNameIndex) ?: "unknown_audio.mp3"
                    } else {
                        "unknown_audio.mp3"
                    }
                    
                    val size = if (sizeIndex >= 0) {
                        cursor.getLong(sizeIndex)
                    } else {
                        // Try to get size from input stream if cursor doesn't have it
                        try {
                            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not determine file size", e)
                            0L
                        }
                    }
                    
                    // Get MIME type from ContentResolver
                    val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: "audio/mpeg"
                    
                    Log.d(TAG, "File info retrieved: name=$displayName, size=$size, mime=$mimeType")
                    FileInfo(displayName, size, mimeType)
                } else {
                    Log.e(TAG, "Cursor is empty for URI: $uri")
                    null
                }
            } ?: run {
                Log.e(TAG, "Query returned null for URI: $uri")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting file info", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file info", e)
            null
        }
    }
    
    /**
     * Get duration of audio file
     */
    private fun getDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting duration", e)
            0L
        }
    }
    
    /**
     * Format duration for display
     */
    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes > 0) {
            "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
        } else {
            "${remainingSeconds}s"
        }
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }
    
    private data class FileInfo(
        val displayName: String,
        val size: Long,
        val mimeType: String
    )
}
