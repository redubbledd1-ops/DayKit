package com.dd.daykit.homeassistant

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.dd.daykit.CustomSoundManager
import com.dd.daykit.data.HaUpdateResult
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.sound.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Synchroniseert custom alarmgeluiden tussen telefoon en Home Assistant.
 *
 * Upload (telefoon -> HA): stuurt het bestand als base64 naar de daykit-
 * custom-integratie (/api/daykit/sound_upload — zelfde HA-verbinding/poort
 * als de rest van de app, geen apart AppDaemon-endpoint meer nodig), die het opslaat
 * onder /config/www/daykit_sounds/. Zo heeft HA een eigen lokale kopie - nodig voor
 * de backup-speaker, want die moet ook werken als de telefoon zelf niet (meer) bereikbaar is.
 *
 * Download (HA -> telefoon): leest /local/daykit_sounds/manifest.json (door HA zelf
 * statisch geserveerd, geen extra config nodig) en haalt ontbrekende bestanden op.
 */
object SoundHaSync {
    private const val TAG = "SoundHaSync"
    private const val SOUNDS_SUBDIR = HaPaths.SOUNDS_SUBDIR

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Ondersteunde audio-extensies bij upload/download - zelfde lijst als
     * CustomSoundManager.SUPPORTED_EXTENSIONS (het toevoegscherm accepteert deze al lokaal).
     */
    private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac")

    /**
     * Berekent de bestandsnaam die [sound] op de HA-kant krijgt bij upload - gecentraliseerd
     * zodat elke plek die de afspeel-URL van een al geuploade sound moet reconstrueren
     * (HomeAssistantRepository.uploadAndGetSoundUrlOrError, ExternalSpeakerHelper,
     * AlarmScheduler) exact dezelfde naam+extensie berekent als waarmee daadwerkelijk geupload
     * is. Behoudt de echte extensie (mp3/wav/ogg/m4a/aac) i.p.v. altijd ".mp3" te forceren -
     * dat laatste labelde bijv. een wav-bestand fout, wat afspelen op de HA-speaker kon laten
     * mislukken ook al was de upload zelf gelukt.
     */
    fun safeUploadedFilename(sound: Sound): String {
        val extension = File(sound.filePath).extension.lowercase().let {
            if (it in SUPPORTED_EXTENSIONS) it else "mp3"
        }
        val safeBase = sound.name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return "$safeBase.$extension"
    }

    /**
     * Leest de audio-bytes voor [sound]. Voor echte custom (zelf-geimporteerde) geluiden
     * staat in filePath een gewoon bestandspad op de telefoon. Voor bundled/systeem-geluiden
     * (zie SoundRepository.getSystemRingtones()) staat er i.p.v. een pad juist een
     * content-/resource-URI in filePath ("android.resource://..." voor het meegeleverde
     * standaardgeluid, of "content://..." voor een systeem-ringtone) - die kan niet via
     * File() geopend worden, alleen via de ContentResolver. Zonder deze fallback faalde de
     * upload met "Lokaal bestand niet gevonden" voor vrijwel elk geluid, behalve toevallig
     * een custom sound met een echt bestandspad.
     */
    private fun readSoundBytes(context: Context, sound: Sound): ByteArray {
        val path = sound.filePath
        return if (path.contains("://")) {
            val uri = Uri.parse(path)
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("Kon geluid niet openen: $path")
        } else {
            val file = File(path)
            if (!file.exists()) {
                throw Exception("Lokaal bestand niet gevonden: $path")
            }
            file.readBytes()
        }
    }

    /**
     * Upload een lokaal custom geluid naar Home Assistant. Best-effort: mislukken hiervan
     * mag het toevoegen van het geluid op de telefoon zelf nooit blokkeren.
     */
    suspend fun uploadSoundToHa(context: Context, sound: Sound): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bytes = try {
                readSoundBytes(context, sound)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val safeFilename = safeUploadedFilename(sound)

            val settingsStorage = HomeAssistantSettingsStorage(context)
            val repository = HomeAssistantRepository(HomeAssistantClient, settingsStorage)
            val result = repository.uploadSoundToHomeAssistant(safeFilename, base64Data)

            if (result is HaUpdateResult.Error) {
                Log.e(TAG, "Upload mislukt: ${result.message}")
                Result.failure(Exception(result.message))
            } else {
                Log.i(TAG, "Sound geupload naar HA: $safeFilename")

                // Onthoud de HA-URL op de Room-rij zodat een backup/restore dit bestand later
                // van HA kan terugdownloaden i.p.v. de upload steeds opnieuw te moeten doen
                // (zie BackupManager.kt) - hiervoor eerder alleen gelogd, nergens opgeslagen.
                val baseUrl = settingsStorage.settingsFlow.firstOrNull()?.activeBaseUrl?.trimEnd('/')
                if (!baseUrl.isNullOrBlank()) {
                    try {
                        val haUrl = "$baseUrl/local/$SOUNDS_SUBDIR/$safeFilename"
                        com.dd.daykit.database.AppDatabase.getDatabase(context)
                            .customAlarmSoundDao()
                            .updateHaSoundUrl(sound.id, haUrl)
                    } catch (e: Exception) {
                        Log.w(TAG, "Kon haSoundUrl niet opslaan voor sound ${sound.id}", e)
                    }
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij uploaden geluid naar HA", e)
            Result.failure(e)
        }
    }

    /**
     * Haal geluiden op die al op HA staan en download degene die nog niet lokaal bekend zijn.
     * @return aantal nieuw gedownloade geluiden
     */
    suspend fun downloadSoundsFromHa(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val settingsStorage = HomeAssistantSettingsStorage(context)
            val haSettings = settingsStorage.settingsFlow.firstOrNull()
            val baseUrl = haSettings?.activeBaseUrl?.trimEnd('/')
            if (baseUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Geen actieve Home Assistant URL"))
            }

            val manifestUrl = "$baseUrl/local/$SOUNDS_SUBDIR/manifest.json"
            val manifestRequest = Request.Builder().url(manifestUrl).get().build()

            val filenames: List<String> = client.newCall(manifestRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Kon manifest niet ophalen: HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "[]"
                try {
                    json.decodeFromString<List<String>>(bodyStr)
                } catch (e: Exception) {
                    Log.e(TAG, "Manifest kon niet gelezen worden: $bodyStr", e)
                    emptyList()
                }
            }

            val manager = CustomSoundManager(context)
            var downloaded = 0
            for (filename in filenames) {
                if (manager.hasSoundWithOriginalFilename(filename)) continue
                try {
                    val fileUrl = "$baseUrl/local/$SOUNDS_SUBDIR/$filename"
                    val fileRequest = Request.Builder().url(fileUrl).get().build()
                    val bytes = client.newCall(fileRequest).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                        resp.body?.bytes() ?: throw Exception("Leeg antwoord")
                    }
                    // Strip de daadwerkelijke extensie (kan sinds de extensie-fix ook
                    // .wav/.ogg/.m4a/.aac zijn, niet meer altijd .mp3) i.p.v. blind ".mp3" te
                    // verwijderen - anders blijft bijv. "regen.wav" onterecht heten "regen.wav".
                    val displayName = filename.substringBeforeLast('.', filename)
                    val result = manager.addDownloadedSound(displayName, filename, bytes)
                    if (result is CustomSoundManager.AddSoundResult.Success) {
                        downloaded++
                    } else if (result is CustomSoundManager.AddSoundResult.Error) {
                        Log.w(TAG, "Downloaden van $filename opgeslagen mislukt: ${result.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fout bij downloaden van geluid $filename", e)
                }
            }
            Result.success(downloaded)
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij downloaden geluiden van HA", e)
            Result.failure(e)
        }
    }
}
