package com.dd.daykit

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.ExistingPeriodicWorkPolicy
import com.dd.daykit.data.dataStore
import com.dd.daykit.rules.playedAlarmsDataStore
import com.dd.daykit.rules.triggerBehaviorDataStore
import com.dd.daykit.rules.triggerRulesDataStore
import com.dd.daykit.database.AppDatabase
import com.dd.daykit.database.CustomAlarmSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
private data class CustomSoundBackupEntry(
    val displayName: String,
    val originalFilename: String,
    val storedFilename: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val mimeType: String,
    val haSoundUrl: String? = null,
    /**
     * Het geluidsbestand zelf, base64-gecodeerd, rechtstreeks in de backup ingebed. Dit is de
     * primaire manier om een custom geluid te herstellen — zo werkt restore altijd, ongeacht of
     * er (nog) een Home Assistant-verbinding is. `haSoundUrl` blijft als terugval bewaard voor
     * oudere backups die deze embedded audio nog niet hadden. Nullable/default zodat oudere
     * backup-bestanden (van vóór dit veld bestond) nog steeds ingelezen kunnen worden.
     */
    val audioBase64: String? = null
)

/**
 * Naam+account van een kalender die ergens als agenda-trigger gebruikt wordt (agenda-alarm,
 * weer-slecht-weer-koppeling, weer-temperatuurwissel-koppeling, of losse per-trigger
 * geluid/regel-instellingen). Android's `CalendarContract.Calendars._ID` ([calendarId]) is
 * device-lokaal en niet stabiel over toestellen of na een agenda-re-sync — dit stukje laat
 * restore de juiste kalender op DIT toestel terugvinden op naam i.p.v. blind de oude ID te
 * hergebruiken. Zie [BackupManager.remapTriggerCalendarIdsAfterRestore].
 */
@Serializable
private data class CalendarTriggerNameEntry(
    val calendarId: String,
    val displayName: String,
    val accountName: String
)

@Serializable
private data class AppBackupData(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val alarms: String,
    val haSettingsJson: String,
    val triggerBehaviorEntries: Map<String, String>,
    val triggerRulesEntries: Map<String, String>,
    /**
     * Zelfde inhoud als [triggerBehaviorEntries] / [triggerRulesEntries], maar mét het
     * oorspronkelijke DataStore-type per waarde ("boolean:true", "int:5", ...). Vroeger werd
     * alleen de ongetypeerde variant bewaard en moest restore het type raden uit de naam van de
     * key; een key die niet aan het verwachte naampatroon voldeed (zoals `dd_music_linked_<id>`)
     * kwam dan als String terug onder een booleanPreferencesKey, wat élke lezer van die trigger
     * liet omvallen — met "Geen alarm" en een onbereikbaar trigger-optiescherm tot gevolg.
     *
     * Nullable met default zodat oudere backups (zonder deze velden) gewoon leesbaar blijven;
     * restore valt dan terug op de oude naam-heuristiek. De ongetypeerde velden blijven ook
     * gevuld, zodat een oudere app-versie een nieuwe backup nog kan inlezen.
     */
    val triggerBehaviorEntriesTyped: Map<String, String>? = null,
    val triggerRulesEntriesTyped: Map<String, String>? = null,
    val playedAlarmIds: List<String>,
    val snoozeStorePrefs: Map<String, String>,
    val localActivationPrefs: Map<String, String>,
    val appSettingsPrefs: Map<String, String>,
    val stopwatchPrefs: Map<String, String> = emptyMap(),
    val timerSettingsPrefs: Map<String, String> = emptyMap(),
    val timerHistoryPrefs: Map<String, String> = emptyMap(),
    val languagePrefs: Map<String, String> = emptyMap(),
    /**
     * Elk overig SharedPreferences-bestand van de app, per bestandsnaam, met getypeerde waarden.
     *
     * Hiervoor had elk prefs-bestand een eigen veld hierboven, en werd er dus stilzwijgend niets
     * meegenomen zodra er ergens een nieuw bestand bijkwam - precies waardoor o.a. de extra timers
     * ([ExtraTimerManager] -> "ExtraTimerPrefs"), de HTTP-server-instellingen
     * ([com.dd.daykit.sound.HttpServerManager] -> "http_server_settings") en de
     * geluiden-aan/uit-schakelaar ([SoundStateManager] -> "SoundPrefs") nooit in een backup
     * belandden. Deze map wordt automatisch gevuld door de map `shared_prefs` uit te lezen, dus een
     * nieuw prefs-bestand zit er voortaan vanzelf in; alleen wat écht toestel-lokaal is staat in
     * [DEVICE_LOCAL_PREFS_FILES] en blijft bewust buiten de backup.
     *
     * Bestanden die hierboven al een eigen veld hebben komen hier NIET nog eens in (zie
     * [DEDICATED_PREFS_FILES]), zodat er niets dubbel of in de verkeerde volgorde teruggezet wordt.
     */
    val otherPrefs: Map<String, Map<String, String>> = emptyMap(),
    /**
     * Alle overige sleutels uit de `home_assistant_settings`-DataStore, dus alles behalve
     * `ha_settings_json` (dat in [haSettingsJson] zit en bij terugzetten zijn eigen, voorzichtige
     * controle heeft) en de schaduwkopie `ha_settings_json_last_good` (die wordt bij restore uit
     * [haSettingsJson] afgeleid). Vangnet voor het geval er ooit een sleutel bijkomt.
     */
    val haDataStoreEntries: Map<String, String> = emptyMap(),
    val customSounds: List<CustomSoundBackupEntry> = emptyList(),
    val calendarTriggerNames: List<CalendarTriggerNameEntry> = emptyList()
)

object BackupManager {
    const val BACKUP_EXTENSION = "kalbackup"
    const val BACKUP_MIME_TYPE = "application/octet-stream"

    private const val BACKUP_VERSION: Byte = 1
    // Zelfde pad als CustomSoundManager.CUSTOM_SOUNDS_DIR (private daar, dus hier los gehouden).
    private const val CUSTOM_SOUNDS_DIR = "alarm_sounds/custom"
    private const val APP_SETTINGS_PREFS_NAME = "alarm_settings"
    private const val HA_SETTINGS_KEY = "ha_settings_json"
    // Moet gelijk blijven aan LAST_GOOD_KEY in HomeAssistantSettingsStorage.
    private const val HA_SETTINGS_LAST_GOOD_KEY = "ha_settings_json_last_good"
    private const val PLAYED_ALARMS_KEY = "played_alarm_ids"
    private const val SNOOZE_PREFS_NAME = "agenda_snooze_state"
    private const val LOCAL_ACTIVATION_PREFS_NAME = "agenda_alarm_local_activation"
    private const val BACKUP_PREFS_NAME = "BackupPrefs"
    private const val BACKUP_SEQUENCE_KEY = "backup_sequence_number"
    // Bewaart de kalendernaam-mapping van de laatste restore zodat een herkoppel-poging later
    // opnieuw gedaan kan worden (zie [reconcilePendingCalendarTriggerRemap]) — nodig omdat op een
    // gloednieuwe telefoon de agenda-permissie er tijdens de restore zelf nog niet hoeft te zijn.
    private const val PENDING_CALENDAR_TRIGGER_NAMES_KEY = "pending_calendar_trigger_names"

    /**
     * Wanneer er voor het laatst een backup is teruggezet (ms sinds epoch, 0 = nog nooit), en de
     * bestandsnamen van backups die dit toestel zélf heeft gemaakt.
     *
     * Deze twee staan bewust in [BACKUP_PREFS_NAME] en niet bij de gewone app-instellingen: die
     * laatste ([APP_SETTINGS_PREFS_NAME]) wordt bij elke restore leeggemaakt en overschreven met
     * de inhoud uit het backupbestand. Bookkeeping over backups zou daar dus telkens verdwijnen —
     * precies waarom de "backup gevonden"-melding steeds opnieuw voor dezelfde bestanden kwam.
     * `BackupPrefs` zit niet in de backup en overleeft een restore dus ongeschonden.
     */
    private const val LAST_RESTORE_AT_KEY = "last_restore_at_ms"
    private const val OWN_BACKUP_FILENAMES_KEY = "own_backup_filenames"
    private const val SNOOZE_END_MS_KEY = "snooze_end_ms"
    private const val SNOOZE_COUNT_KEY = "snooze_count"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_BITS = 256
    /**
     * SharedPreferences-bestanden die hierboven al een eigen veld in [AppBackupData] hebben. Die
     * worden apart weggeschreven én teruggezet (deels met eigen type-afhandeling, zie
     * [restoreSnoozePrefs]/[restoreLocalActivationPrefs]), dus ze horen niet óók nog eens in
     * [AppBackupData.otherPrefs] terecht te komen.
     */
    private val DEDICATED_PREFS_FILES = setOf(
        APP_SETTINGS_PREFS_NAME,
        SNOOZE_PREFS_NAME,
        LOCAL_ACTIVATION_PREFS_NAME,
        "StopwatchPrefs",
        "TimerSettings",
        "TimerHistoryPrefs",
        "LanguagePrefs"
    )

    /**
     * Prefs-bestanden die bewust NIET in de backup gaan, omdat ze bij dít toestel horen en niet bij
     * de instellingen van de gebruiker. Meeslepen zou na een restore actief schade doen: een
     * meegenomen dedup- of "al afgegaan"-stempel onderdrukt meldingen die hier nog moeten komen,
     * een meegenomen HA-sync-stempel slaat de HA-controle over, en [BACKUP_PREFS_NAME] moet een
     * restore juist overleven (zie de uitleg daar).
     */
    private val DEVICE_LOCAL_PREFS_FILES = setOf(
        BACKUP_PREFS_NAME,              // bookkeeping over backups zelf - moet restore overleven
        "ha_sync_state",                // HomeAssistantSettingsStorage: HA-herkomststempel
        "agenda_alarm_forensics",       // debug-logboek
        "alarm_cache",                  // AlarmScheduler-cache
        "agenda_popup_state",           // lopende popup-service
        "buttonless_notification_state",// lopende notificatie-service
        "alarm_service_ringing",        // "gaat op dit moment af"-vlag
        "pre_alarm_check_prefs",        // uitkomst laatste pre-alarm-check
        "pre_alarm_samples",            // meetreeks pre-alarm-check
        "out_of_bed_cache",             // RuleEngine-cache
        "weather_alert_dedup",          // "deze weermelding is al gestuurd"
        "sync_status_prefs",            // laatste agenda-sync-resultaat
        "app_migrations",               // welke eenmalige migraties hier al gedraaid hebben
        "poc_test_countdown_prefs"      // testcode
    )

    /**
     * Prefs-bestanden die niet van de app zelf zijn (Play Services, WebView, Compose enz.). Die
     * horen niet in een backup van app-instellingen en kunnen na restore juist problemen geven.
     */
    private val FOREIGN_PREFS_PREFIXES = listOf(
        "com.google.android",
        "com.android",
        "WebView",
        "androidx."
    )

    private val magic = byteArrayOf('K'.code.toByte(), 'A'.code.toByte())
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createBackup(context: Context, password: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            require(password.isNotEmpty()) { "Wachtwoord mag niet leeg zijn" }

            val triggerBehaviorPreferences = context.triggerBehaviorDataStore.data.first()
            val triggerRulesPreferences = context.triggerRulesDataStore.data.first()
            val triggerBehaviorEntries = preferencesToStringMap(triggerBehaviorPreferences)
            val triggerRulesEntries = preferencesToStringMap(triggerRulesPreferences)

            val haPreferences = context.dataStore.data.first()

            val backupData = AppBackupData(
                alarms = readAlarmsJson(context),
                // Terugvallen op de schaduwkopie als de hoofdsleutel leeg is. Zonder die terugval
                // legde een backup precies díe situatie vast waar HomeAssistantSettingsStorage
                // zich tegen indekt (lege hoofdsleutel, echte configuratie nog in "last good"):
                // de backup kwam dan met een lege HA-config uit de bus, en bij terugzetten wordt
                // die bewust genegeerd - dus álle HA-instellingen, inclusief speakers en de
                // "Uitspreken"-schakelaar, kwamen simpelweg niet mee.
                haSettingsJson = haPreferences[stringPreferencesKey(HA_SETTINGS_KEY)]
                    ?.takeIf { it.isNotBlank() }
                    ?: haPreferences[stringPreferencesKey(HA_SETTINGS_LAST_GOOD_KEY)].orEmpty(),
                triggerBehaviorEntries = triggerBehaviorEntries,
                triggerRulesEntries = triggerRulesEntries,
                triggerBehaviorEntriesTyped = preferencesToTypedStringMap(triggerBehaviorPreferences),
                triggerRulesEntriesTyped = preferencesToTypedStringMap(triggerRulesPreferences),
                playedAlarmIds = context.playedAlarmsDataStore.data.first()[stringSetPreferencesKey(PLAYED_ALARMS_KEY)]
                    ?.toList()
                    .orEmpty(),
                snoozeStorePrefs = sharedPreferencesToStringMap(
                    context.getSharedPreferences(SNOOZE_PREFS_NAME, Context.MODE_PRIVATE)
                ),
                localActivationPrefs = sharedPreferencesToStringMap(
                    context.getSharedPreferences(LOCAL_ACTIVATION_PREFS_NAME, Context.MODE_PRIVATE)
                ),
                appSettingsPrefs = sharedPreferencesToStringMap(
                    context.getSharedPreferences(APP_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                ),
                stopwatchPrefs = sharedPreferencesToStringMap(
                    context.getSharedPreferences("StopwatchPrefs", Context.MODE_PRIVATE)
                ),
                timerSettingsPrefs = sharedPreferencesToStringMap(
                    context.getSharedPreferences("TimerSettings", Context.MODE_PRIVATE)
                ),
                timerHistoryPrefs = sharedPreferencesToStringMap(
                    context.getSharedPreferences("TimerHistoryPrefs", Context.MODE_PRIVATE)
                ),
                languagePrefs = sharedPreferencesToStringMap(
                    context.getSharedPreferences("LanguagePrefs", Context.MODE_PRIVATE)
                ),
                otherPrefs = collectOtherSharedPrefs(context),
                haDataStoreEntries = preferencesToTypedStringMap(haPreferences)
                    .filterKeys { it != HA_SETTINGS_KEY && it != HA_SETTINGS_LAST_GOOD_KEY },
                customSounds = AppDatabase.getDatabase(context).customAlarmSoundDao().getAllSoundsList().map { sound ->
                    // Geluid rechtstreeks als bytes inlezen en embedden — zo hangt herstel niet af
                    // van een HA-verbinding of van het geluid ooit succesvol naar HA geüpload zijn
                    // (dat gebeurt namelijk niet altijd, bv. bij geluiden toegevoegd via de
                    // per-trigger "Ander geluid"-picker in TriggerRulesActivity). Best-effort: als
                    // het lokale bestand toch weg is, valt restore terug op haSoundUrl (zie
                    // restoreCustomSounds hieronder).
                    val soundsDir = File(context.filesDir, CUSTOM_SOUNDS_DIR)
                    val soundFile = File(soundsDir, sound.storedFilename)
                    val audioBase64 = if (soundFile.exists()) {
                        try {
                            Base64.encodeToString(soundFile.readBytes(), Base64.NO_WRAP)
                        } catch (e: Exception) {
                            android.util.Log.w("BackupManager", "Kon geluidsbestand '${sound.displayName}' niet inlezen voor backup", e)
                            null
                        }
                    } else {
                        android.util.Log.w("BackupManager", "Geluidsbestand voor '${sound.displayName}' niet gevonden op schijf (${soundFile.absolutePath})")
                        null
                    }
                    CustomSoundBackupEntry(
                        displayName = sound.displayName,
                        originalFilename = sound.originalFilename,
                        storedFilename = sound.storedFilename,
                        durationMs = sound.durationMs,
                        fileSizeBytes = sound.fileSizeBytes,
                        mimeType = sound.mimeType,
                        haSoundUrl = sound.haSoundUrl,
                        audioBase64 = audioBase64
                    )
                },
                calendarTriggerNames = collectCalendarTriggerNames(context, triggerBehaviorEntries, triggerRulesEntries)
            )

            val clearText = json.encodeToString(backupData).toByteArray(Charsets.UTF_8)
            val encryptedBytes = encryptBackup(clearText, password)
            Result.success(writeBackupToDownloads(context, encryptedBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(context: Context, uri: Uri, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(password.isNotEmpty()) { "Wachtwoord mag niet leeg zijn" }

            val encryptedBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalArgumentException("Kan backupbestand niet openen"))

            val clearBytes = try {
                decryptBackup(encryptedBytes, password)
            } catch (e: AEADBadTagException) {
                return@withContext Result.failure(IllegalArgumentException("Verkeerd wachtwoord"))
            }

            val backupData = json.decodeFromString<AppBackupData>(clearBytes.toString(Charsets.UTF_8))
            restoreBackupData(context, backupData)
            // Pas hierna: restoreBackupData overschrijft de app-instellingen volledig.
            markRestoreCompleted(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resolveFilename(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path ?: return null).name
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    /**
     * Een op het toestel gevonden backupbestand, met genoeg gegevens om het in een lijst te tonen
     * zonder dat de gebruiker zelf door een bestandskiezer hoeft. Zie [listBackups].
     */
    data class BackupFileInfo(
        val uri: Uri,
        val filename: String,
        /** Millisekonden sinds epoch. */
        val lastModifiedMs: Long,
        /** Grootte in bytes, of 0 als die niet te bepalen was. */
        val sizeBytes: Long
    )

    /**
     * Alle backupbestanden die op dit toestel te vinden zijn, nieuwste eerst. Wordt gebruikt voor
     * het backup-overzicht in het herstelscherm; [findBackupFilesInDownloads] is de smallere
     * variant die alleen de URI's teruggeeft.
     */
    fun listBackups(context: Context): List<BackupFileInfo> {
        val tag = "BackupManager"
        val found = linkedMapOf<String, BackupFileInfo>()

        // 1. Direct filesystem scan
        val dirs = buildSearchDirectories()
        dirs.forEach { dir ->
            android.util.Log.d(tag, "Filesystem scan: ${dir.path} (exists=${dir.exists()}, canRead=${dir.canRead()})")
            collectBackupsFromDir(dir, found)
        }
        android.util.Log.d(tag, "Na filesystem scan: ${found.size} gevonden")

        // 2. App-specifieke externe opslag
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
            android.util.Log.d(tag, "App-extern scan: ${dir.path} (exists=${dir.exists()})")
            collectBackupsFromDir(dir, found)
        }
        android.util.Log.d(tag, "Na app-extern scan: ${found.size} gevonden")

        // 3. MediaStore Downloads
        val msDownloads = runCatching { queryMediaStore(context) }.getOrElse { e ->
            android.util.Log.w(tag, "MediaStore.Downloads query mislukt", e)
            emptyList()
        }
        android.util.Log.d(tag, "MediaStore.Downloads: ${msDownloads.size} resultaten")
        msDownloads.forEach { info -> found.putIfAbsent(info.filename.lowercase(Locale.getDefault()), info) }

        // 4. MediaStore Files
        val msFiles = runCatching { queryMediaStoreFiles(context) }.getOrElse { e ->
            android.util.Log.w(tag, "MediaStore.Files query mislukt", e)
            emptyList()
        }
        android.util.Log.d(tag, "MediaStore.Files: ${msFiles.size} resultaten")
        msFiles.forEach { info -> found.putIfAbsent(info.filename.lowercase(Locale.getDefault()), info) }

        android.util.Log.d(tag, "Totaal unieke backups: ${found.size}")
        return found.values.sortedByDescending { it.lastModifiedMs }
    }

    fun findBackupFilesInDownloads(context: Context): List<Uri> = listBackups(context).map { it.uri }

    /**
     * Verwijdert één backupbestand. Bestanden die deze app zelf via MediaStore heeft weggeschreven
     * mogen zonder extra toestemming weg; staat een bestand op naam van iets anders, dan geeft
     * Android een [SecurityException] en melden we dat als nette fout in plaats van te crashen.
     */
    suspend fun deleteBackup(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val filename = resolveFilename(context, uri)
            val deleted = when (uri.scheme) {
                "file" -> File(uri.path ?: return@withContext Result.failure(
                    IllegalArgumentException("Kan backupbestand niet verwijderen")
                )).delete()
                else -> context.contentResolver.delete(uri, null, null) > 0
            }
            if (deleted) {
                // App-lokale kopie ook opruimen als die bestaat
                if (filename != null) {
                    try {
                        val appCopy = File(
                            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@withContext Result.success(Unit),
                            filename
                        )
                        if (appCopy.exists()) appCopy.delete()
                    } catch (_: Exception) {}
                }
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Kan backupbestand niet verwijderen"))
            }
        } catch (e: SecurityException) {
            android.util.Log.w("BackupManager", "Geen toestemming om backup te verwijderen: $uri", e)
            Result.failure(IllegalArgumentException("Kan backupbestand niet verwijderen"))
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "Verwijderen van backup mislukt: $uri", e)
            Result.failure(e)
        }
    }

    /**
     * Verwijdert meerdere backups. Gaat door als er eentje mislukt en geeft terug hoeveel er
     * daadwerkelijk weg zijn — zo blijft "Verwijder allemaal" bruikbaar als er één bestand
     * dwarsligt.
     */
    suspend fun deleteBackups(context: Context, uris: List<Uri>): Int {
        var deleted = 0
        uris.forEach { uri -> if (deleteBackup(context, uri).isSuccess) deleted++ }
        return deleted
    }

    /** Tijdstip van de laatste restore op dit toestel; 0 als er nog nooit iets is teruggezet. */
    fun getLastRestoreAtMs(context: Context): Long =
        context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(LAST_RESTORE_AT_KEY, 0L)

    /** Bestandsnamen van backups die dit toestel zelf heeft gemaakt. */
    fun getOwnBackupFilenames(context: Context): Set<String> =
        context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(OWN_BACKUP_FILENAMES_KEY, emptySet()) ?: emptySet()

    private fun markOwnBackupFilename(context: Context, filename: String) {
        val prefs = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(OWN_BACKUP_FILENAMES_KEY, emptySet()).orEmpty() + filename
        prefs.edit().putStringSet(OWN_BACKUP_FILENAMES_KEY, updated).apply()
    }

    /**
     * De backup die de gebruiker bij het openen van de app aangeboden zou moeten krijgen, of
     * `null` als er niets aan te bieden valt. Aanbieden gebeurt alleen voor een bestand dat:
     *
     * - dit toestel niet zélf heeft gemaakt — anders bood de app aan om de backup terug te zetten
     *   die je zojuist zelf aanmaakte, en dat is bij elke nieuwe backup opnieuw raak; en
     * - nieuwer is dan de laatste restore op dit toestel — wat je al eens hebt teruggezet (of wat
     *   ouder is dan de huidige staat) is geen nieuws meer.
     *
     * Is er nog nooit iets teruggezet, dan telt de tweede regel niet en wordt de nieuwste vreemde
     * backup gewoon aangeboden: precies het geval van een verse installatie op een nieuw toestel.
     */
    fun findRestoreSuggestion(context: Context): BackupFileInfo? {
        val ownFilenames = getOwnBackupFilenames(context)
        val lastRestoreAt = getLastRestoreAtMs(context)
        return listBackups(context)
            .filter { it.filename !in ownFilenames }
            .filter { lastRestoreAt <= 0L || it.lastModifiedMs > lastRestoreAt }
            .maxByOrNull { it.lastModifiedMs }
    }

    /**
     * Legt vast dat er zojuist een backup is teruggezet. Wordt bewust ná [restoreBackupData]
     * geschreven: die schrijft de app-instellingen volledig over met de inhoud van de backup, dus
     * alles wat er vóór die tijd genoteerd wordt in [APP_SETTINGS_PREFS_NAME] is daarna weg.
     */
    private fun markRestoreCompleted(context: Context) {
        context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_RESTORE_AT_KEY, System.currentTimeMillis())
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun buildSearchDirectories(): List<File> {
        val seen = mutableSetOf<String>()
        val dirs = mutableListOf<File>()

        fun tryAdd(file: File) {
            val key = runCatching { file.canonicalPath }.getOrElse { file.path }
            if (seen.add(key)) dirs.add(file)
        }

        // Public Downloads (most common location)
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let(::tryAdd)

        // Root of external storage + common Download/Downloads variants
        Environment.getExternalStorageDirectory()?.let { root ->
            tryAdd(root)
            tryAdd(File(root, "Download"))
            tryAdd(File(root, "Downloads"))
        }

        return dirs
    }

    private fun collectBackupsFromDir(dir: File, found: MutableMap<String, BackupFileInfo>) {
        if (!dir.exists() || !dir.canRead()) return

        fun addIfBackup(file: File) {
            if (!file.isFile || !file.extension.equals(BACKUP_EXTENSION, ignoreCase = true)) return
            found.putIfAbsent(
                file.name.lowercase(Locale.getDefault()),
                BackupFileInfo(
                    uri = Uri.fromFile(file),
                    filename = file.name,
                    lastModifiedMs = file.lastModified(),
                    sizeBytes = file.length()
                )
            )
        }

        dir.listFiles()?.forEach { file ->
            when {
                file.isFile -> addIfBackup(file)
                // One level deep — catches files in sub-folders like "KalenderAlarm/"
                file.isDirectory -> file.listFiles()?.forEach(::addIfBackup)
            }
        }
    }

    private fun queryMediaStore(context: Context): List<BackupFileInfo> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE
        )
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("%.$BACKUP_EXTENSION"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val results = mutableListOf<BackupFileInfo>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                results += BackupFileInfo(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    filename = name,
                    // MediaStore geeft DATE_MODIFIED in seconden, File.lastModified() in
                    // milliseconden. Zonder deze omrekening staan bestanden uit de twee bronnen
                    // door elkaar gesorteerd en lijken MediaStore-vondsten uit 1970 te komen.
                    lastModifiedMs = cursor.getLong(dateCol) * 1000L,
                    sizeBytes = cursor.getLong(sizeCol)
                )
            }
            return results
        }
        return emptyList()
    }

    private fun queryMediaStoreFiles(context: Context): List<BackupFileInfo> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE
        )
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("%.$BACKUP_EXTENSION"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val results = mutableListOf<BackupFileInfo>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                results += BackupFileInfo(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    filename = name,
                    lastModifiedMs = cursor.getLong(dateCol) * 1000L,
                    sizeBytes = cursor.getLong(sizeCol)
                )
            }
            return results
        }
        return emptyList()
    }

    private fun readAlarmsJson(context: Context): String {
        val file = File(context.filesDir, "alarms.json")
        return if (file.exists()) file.readText() else "[]"
    }

    /** Trailing cijferreeks na een underscore, bv. "check_user_at_home_1234567" -> "1234567". */
    private fun triggerIdSuffix(keyName: String): String? =
        Regex("_(\\d+)$").find(keyName)?.groupValues?.getOrNull(1)

    /**
     * Verzamelt elke kalender-ID die ergens als agenda-trigger gebruikt wordt (agenda-alarm-
     * selectie, weer-slecht-weer-koppeling, weer-temperatuurwissel-koppeling, of losse per-trigger
     * instellingen/regels) en zoekt daar de huidige naam+account bij op dit toestel — dat is wat
     * restore straks op een ander toestel gebruikt om de juiste kalender terug te vinden.
     */
    private fun collectCalendarTriggerNames(
        context: Context,
        triggerBehaviorEntries: Map<String, String>,
        triggerRulesEntries: Map<String, String>
    ): List<CalendarTriggerNameEntry> {
        val ids = mutableSetOf<String>()
        ids += SettingsManager.getTriggerCalendarIds(context)
        ids += SettingsManager.getWeatherBadWeatherCalendarIds(context)
        ids += SettingsManager.getWeatherTempChangeCalendarIds(context)
        triggerBehaviorEntries.keys.forEach { key -> triggerIdSuffix(key)?.let { ids += it } }
        triggerRulesEntries.keys.forEach { key -> triggerIdSuffix(key)?.let { ids += it } }
        if (ids.isEmpty()) return emptyList()

        val liveCalendarsById = getCalendars(context).associateBy { it.id.toString() }
        return ids.mapNotNull { id ->
            liveCalendarsById[id]?.let { cal -> CalendarTriggerNameEntry(id, cal.displayName, cal.accountName) }
        }
    }

    /**
     * Leest élk SharedPreferences-bestand van de app uit `shared_prefs` en geeft de inhoud terug
     * per bestandsnaam, behalve de bestanden die al een eigen veld hebben ([DEDICATED_PREFS_FILES]),
     * de bewust toestel-lokale ([DEVICE_LOCAL_PREFS_FILES]) en die van andere bibliotheken
     * ([FOREIGN_PREFS_PREFIXES]).
     *
     * Bewust automatisch in plaats van een handmatige opsomming: elke keer dat er ergens in de app
     * een nieuw prefs-bestand bijkwam, verdween dat stilzwijgend uit de backup zonder dat iemand
     * het merkte. Nu is "vergeten mee te nemen" de uitzondering die je expliciet moet opschrijven,
     * in plaats van de standaard.
     */
    private fun collectOtherSharedPrefs(context: Context): Map<String, Map<String, String>> {
        return try {
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            val files = sharedPrefsDir.listFiles { file -> file.isFile && file.name.endsWith(".xml") }
                ?: return emptyMap()

            files.mapNotNull { file ->
                val name = file.name.removeSuffix(".xml")
                when {
                    name in DEDICATED_PREFS_FILES -> null
                    name in DEVICE_LOCAL_PREFS_FILES -> null
                    FOREIGN_PREFS_PREFIXES.any { name.startsWith(it) } -> null
                    else -> {
                        val entries = sharedPreferencesToStringMap(
                            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                        )
                        if (entries.isEmpty()) null else name to entries
                    }
                }
            }.toMap().also {
                android.util.Log.d("BackupManager", "Overige prefs-bestanden in backup: ${it.keys}")
            }
        } catch (e: Exception) {
            // Best-effort: liever een backup zonder deze extra bestanden dan helemaal geen backup.
            android.util.Log.w("BackupManager", "Kon overige prefs-bestanden niet inlezen", e)
            emptyMap()
        }
    }

    private fun preferencesToStringMap(preferences: Preferences): Map<String, String> {
        return preferences.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }
    }

    /**
     * Als [preferencesToStringMap], maar met het type erbij ("boolean:true", "int:5", ...) zodat
     * restore niet hoeft te raden. Zie [AppBackupData.triggerBehaviorEntriesTyped].
     */
    private fun preferencesToTypedStringMap(preferences: Preferences): Map<String, String> {
        return preferences.asMap().mapKeys { it.key.name }.mapValues { encodeSharedPreferenceValue(it.value) }
    }

    private fun sharedPreferencesToStringMap(sharedPreferences: SharedPreferences): Map<String, String> {
        return sharedPreferences.all.mapValues { encodeSharedPreferenceValue(it.value) }
    }

    private fun encodeSharedPreferenceValue(value: Any?): String {
        return when (value) {
            is Boolean -> "boolean:$value"
            is Float -> "float:$value"
            // DataStore Preferences kent Double; SharedPreferences niet. Zonder eigen tak zou een
            // Double als "string:" terugkomen en na restore het verkeerde type hebben.
            is Double -> "double:$value"
            is Int -> "int:$value"
            is Long -> "long:$value"
            is String -> "string:$value"
            is Set<*> -> "string_set:${json.encodeToString(value.map { it.toString() })}"
            else -> "string:${value?.toString().orEmpty()}"
        }
    }

    private suspend fun restoreBackupData(context: Context, backupData: AppBackupData) {
        File(context.filesDir, "alarms.json").writeText(backupData.alarms)

        // HA-instellingen alleen terugzetten als de backup ze echt bevat én ze leesbaar zijn.
        // Hier werd voorheen onvoorwaardelijk geschreven: een backup die met een lege HA-config
        // gemaakt was (zie de orEmpty() bij het aanmaken) zette een lege string over een prima
        // werkende configuratie heen. Die lege string is niet null, ging dus de decoder in, faalde,
        // en belandde in de defaults — waarna de speakerinstellingen van agenda-alarm, timer en
        // weer op BOTH zonder entiteit stonden.
        val incomingHaSettings = backupData.haSettingsJson
        val haSettingsUsable = incomingHaSettings.isNotBlank() && runCatching {
            json.decodeFromString<com.dd.daykit.data.HomeAssistantSettings>(incomingHaSettings)
        }.isSuccess

        if (haSettingsUsable) {
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey(HA_SETTINGS_KEY)] = incomingHaSettings
                // Schaduwkopie meteen meenemen, anders blijft die van het vórige toestel staan en
                // zou een latere leesfout terugvallen op een configuratie die hier niet meer klopt.
                preferences[stringPreferencesKey(HA_SETTINGS_LAST_GOOD_KEY)] = incomingHaSettings
            }
        } else {
            android.util.Log.w(
                "BackupManager",
                "Backup bevat geen bruikbare HA-instellingen (leeg=${incomingHaSettings.isBlank()}) — " +
                    "bestaande instellingen blijven staan"
            )
            HaSettingsIntegrityNotifier.notify(
                context,
                "Home Assistant-instellingen niet teruggezet",
                "Deze backup bevatte geen bruikbare HA-configuratie. Je bestaande instellingen " +
                    "voor agenda-alarm, timer en weer zijn ongewijzigd gelaten."
            )
        }

        context.triggerBehaviorDataStore.edit { preferences ->
            preferences.clear()
            val typed = backupData.triggerBehaviorEntriesTyped
            if (typed != null) {
                typed.forEach { (key, value) -> putTypedPreference(preferences, key, value) }
            } else {
                // Oudere backup zonder type-informatie: type afleiden uit de naam van de key.
                backupData.triggerBehaviorEntries.forEach { (key, value) ->
                    when {
                        isTriggerBehaviorBooleanKey(key) -> preferences[booleanPreferencesKey(key)] = value.toBoolean()
                        isTriggerBehaviorIntKey(key) -> preferences[intPreferencesKey(key)] = value.toIntOrNull() ?: 0
                        else -> preferences[stringPreferencesKey(key)] = value
                    }
                }
            }
        }

        context.triggerRulesDataStore.edit { preferences ->
            preferences.clear()
            val typed = backupData.triggerRulesEntriesTyped
            if (typed != null) {
                typed.forEach { (key, value) -> putTypedPreference(preferences, key, value) }
            } else {
                backupData.triggerRulesEntries.forEach { (key, value) ->
                    preferences[stringPreferencesKey(key)] = value
                }
            }
        }

        context.playedAlarmsDataStore.edit { preferences ->
            preferences.clear()
            preferences[stringSetPreferencesKey(PLAYED_ALARMS_KEY)] = backupData.playedAlarmIds.toSet()
        }

        restoreSnoozePrefs(context, backupData.snoozeStorePrefs)
        restoreLocalActivationPrefs(context, backupData.localActivationPrefs)
        restoreGenericSharedPrefs(
            context.getSharedPreferences(APP_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE),
            backupData.appSettingsPrefs
        )
        // Vóór clearOrphanedAlarmSoundUri: als het herstelde alarm_sound_uri naar een custom
        // geluid wijst dat hieronder succesvol teruggehaald wordt, bestaat het bestand dan al
        // weer en mag de orphan-check 'm niet alsnog wegschonen.
        restoreCustomSounds(context, backupData.customSounds)
        clearOrphanedAlarmSoundUri(context)
        if (backupData.stopwatchPrefs.isNotEmpty()) {
            restoreGenericSharedPrefs(
                context.getSharedPreferences("StopwatchPrefs", Context.MODE_PRIVATE),
                backupData.stopwatchPrefs
            )
        }
        if (backupData.timerSettingsPrefs.isNotEmpty()) {
            restoreGenericSharedPrefs(
                context.getSharedPreferences("TimerSettings", Context.MODE_PRIVATE),
                backupData.timerSettingsPrefs
            )
        }
        if (backupData.timerHistoryPrefs.isNotEmpty()) {
            restoreGenericSharedPrefs(
                context.getSharedPreferences("TimerHistoryPrefs", Context.MODE_PRIVATE),
                backupData.timerHistoryPrefs
            )
        }
        if (backupData.languagePrefs.isNotEmpty()) {
            restoreGenericSharedPrefs(
                context.getSharedPreferences("LanguagePrefs", Context.MODE_PRIVATE),
                backupData.languagePrefs
            )
        }

        // Alle overige prefs-bestanden (extra timers, HTTP-server, geluiden aan/uit, en alles wat
        // er in de toekomst bijkomt - zie collectOtherSharedPrefs). De denylists worden hier
        // opnieuw toegepast: een oudere backup kan een bestand bevatten dat inmiddels als
        // toestel-lokaal is aangemerkt, en dat hoort dan alsnog niet teruggezet te worden.
        backupData.otherPrefs.forEach { (name, entries) ->
            if (name in DEDICATED_PREFS_FILES) return@forEach
            if (name in DEVICE_LOCAL_PREFS_FILES) return@forEach
            if (FOREIGN_PREFS_PREFIXES.any { name.startsWith(it) }) return@forEach
            if (entries.isEmpty()) return@forEach
            restoreGenericSharedPrefs(
                context.getSharedPreferences(name, Context.MODE_PRIVATE),
                entries
            )
        }

        // Vangnet voor eventuele extra sleutels in de HA-DataStore. Bewust NIET clear()-en: de
        // twee sleutels die er nu in zitten (ha_settings_json + de schaduwkopie) zijn hierboven al
        // met hun eigen, voorzichtige controle afgehandeld en mogen hier niet gewist worden.
        if (backupData.haDataStoreEntries.isNotEmpty()) {
            context.dataStore.edit { preferences ->
                backupData.haDataStoreEntries.forEach { (key, value) ->
                    if (key == HA_SETTINGS_KEY || key == HA_SETTINGS_LAST_GOOD_KEY) return@forEach
                    putTypedPreference(preferences, key, value)
                }
            }
        }

        // Bewaren vóór de eerste poging: als agenda-permissie nu nog niet verleend is (heel
        // normaal vlak na het instellen van een nieuwe telefoon), lukt de herkoppeling hieronder
        // nog niet — reconcilePendingCalendarTriggerRemap() probeert het dan opnieuw zodra er wel
        // weer kalenders te zien zijn (bij de eerstvolgende agenda-sync).
        savePendingCalendarTriggerNames(context, backupData.calendarTriggerNames)
        remapTriggerCalendarIdsAfterRestore(context, backupData.calendarTriggerNames)

        rescheduleBackgroundWorkAfterRestore(context)
    }

    private fun savePendingCalendarTriggerNames(context: Context, entries: List<CalendarTriggerNameEntry>) {
        val prefs = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
        if (entries.isEmpty()) {
            prefs.edit().remove(PENDING_CALENDAR_TRIGGER_NAMES_KEY).apply()
            return
        }
        prefs.edit().putString(PENDING_CALENDAR_TRIGGER_NAMES_KEY, json.encodeToString(entries)).apply()
    }

    /**
     * Herhaalt de agenda-trigger-herkoppeling (zie [remapTriggerCalendarIdsAfterRestore]) voor het
     * geval de poging tijdens de restore zelf niet lukte omdat er toen nog geen agenda-permissie
     * was, of de kalenders van het account nog niet gesynchroniseerd waren — heel gebruikelijk
     * vlak na het instellen van een nieuwe telefoon, waar een restore vaak vóór de eerste agenda-
     * sync gebeurt. Veilig om vaak aan te roepen (bv. bij elke periodieke/handmatige agenda-sync):
     * zonder pending data, of als alle ID's al kloppen, doet dit niets.
     */
    suspend fun reconcilePendingCalendarTriggerRemap(context: Context) {
        try {
            val prefs = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(PENDING_CALENDAR_TRIGGER_NAMES_KEY, null) ?: return
            val entries = json.decodeFromString<List<CalendarTriggerNameEntry>>(stored)
            remapTriggerCalendarIdsAfterRestore(context, entries)
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "Kon agenda-trigger-herkoppeling niet opnieuw proberen", e)
        }
    }

    /**
     * Kalender-ID's zijn Android's `CalendarContract.Calendars._ID` — device-lokaal en niet
     * stabiel over een nieuw toestel of een agenda-re-sync. Een backup gemaakt op de oude telefoon
     * bevat dus agenda-trigger-selecties en per-trigger-instellingen (geluid, sluimeren, smart-
     * alarm-regels, ...) die na restore op een ander toestel naar de VERKEERDE (of geen) agenda
     * kunnen wijzen. Om dat op te lossen bevat de backup ook de kalendernaam + account per ID
     * ([CalendarTriggerNameEntry]) — hier koppelen we die namen aan de kalenders die op DIT
     * toestel bestaan (eerst op exacte account+naam-match, anders op een op dit toestel unieke
     * naam) en herschrijven we alle ID-verwijzingen naar de juiste, nieuwe ID's.
     *
     * Best-effort: als een kalendernaam hier niet (eenduidig) terug te vinden is — bv. omdat dat
     * account nog niet gesynchroniseerd is — blijft die ene trigger op de oude ID staan;
     * [reconcilePendingCalendarTriggerRemap] probeert het later opnieuw.
     */
    private suspend fun remapTriggerCalendarIdsAfterRestore(context: Context, calendarTriggerNames: List<CalendarTriggerNameEntry>) {
        if (calendarTriggerNames.isEmpty()) return

        val liveCalendars = getCalendars(context)
        if (liveCalendars.isEmpty()) return // geen agenda-permissie (nog), of nog geen kalenders gesynchroniseerd

        val byAccountAndName = liveCalendars.groupBy { it.accountName to it.displayName }
        val byNameOnly = liveCalendars.groupBy { it.displayName }

        val idMap = mutableMapOf<String, String>()
        calendarTriggerNames.forEach { entry ->
            val accountMatches = byAccountAndName[entry.accountName to entry.displayName]
            val chosen = if (!accountMatches.isNullOrEmpty()) {
                accountMatches.first()
            } else {
                // Geen match op exact account+naam: alleen gebruiken als de naam op dit toestel
                // uniek is — bij meerdere gelijknamige kalenders is er te veel onzekerheid om
                // automatisch de juiste te kiezen, dan blijft deze trigger liever op de oude ID
                // staan (zichtbaar "leeg"/fout) dan stilletjes de verkeerde agenda te koppelen.
                byNameOnly[entry.displayName]?.singleOrNull()
            }
            if (chosen != null && chosen.id.toString() != entry.calendarId) {
                idMap[entry.calendarId] = chosen.id.toString()
            }
        }
        if (idMap.isEmpty()) return

        android.util.Log.i("BackupManager", "Agenda-trigger ID's herkoppeld op naam: $idMap")

        SettingsManager.saveTriggerCalendarIds(
            context,
            SettingsManager.getTriggerCalendarIds(context).map { idMap[it] ?: it }.toSet()
        )
        SettingsManager.saveWeatherBadWeatherCalendarIds(
            context,
            SettingsManager.getWeatherBadWeatherCalendarIds(context).map { idMap[it] ?: it }.toSet()
        )
        SettingsManager.saveWeatherTempChangeCalendarIds(
            context,
            SettingsManager.getWeatherTempChangeCalendarIds(context).map { idMap[it] ?: it }.toSet()
        )

        context.triggerBehaviorDataStore.edit { preferences -> renameTriggerIdKeys(preferences, idMap) }
        context.triggerRulesDataStore.edit { preferences -> renameTriggerIdKeys(preferences, idMap) }
    }

    /** Herschrijft elke preference-key met een trailing trigger-(kalender-)ID naar de nieuwe ID, met behoud van het oorspronkelijke type. */
    private fun renameTriggerIdKeys(preferences: MutablePreferences, idMap: Map<String, String>) {
        val renames = preferences.asMap().entries.mapNotNull { (key, value) ->
            val oldId = triggerIdSuffix(key.name) ?: return@mapNotNull null
            val newId = idMap[oldId] ?: return@mapNotNull null
            Triple(key, key.name.removeSuffix(oldId) + newId, value)
        }
        renames.forEach { (oldKey, newKeyName, value) ->
            preferences.remove(oldKey)
            when (value) {
                is Boolean -> preferences[booleanPreferencesKey(newKeyName)] = value
                is Int -> preferences[intPreferencesKey(newKeyName)] = value
                is Long -> preferences[longPreferencesKey(newKeyName)] = value
                is Float -> preferences[floatPreferencesKey(newKeyName)] = value
                is Double -> preferences[doublePreferencesKey(newKeyName)] = value
                is String -> preferences[stringPreferencesKey(newKeyName)] = value
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    preferences[stringSetPreferencesKey(newKeyName)] = value as Set<String>
                }
                // De oude key is hierboven al verwijderd; zonder vangnet zou een onbekend type
                // stilletjes verdwijnen bij het herkoppelen van kalender-ID's na een restore.
                else -> preferences[stringPreferencesKey(newKeyName)] = value.toString()
            }
        }
    }

    /**
     * De "Herstart app"-knop na een restore (BackupRestoreScreen.restartApp) relaunched alleen
     * MainActivity — het proces zelf blijft leven, dus AgendaWekkerApplication.onCreate() draait
     * niet opnieuw. Zonder deze stap blijven de periodieke WorkManager-jobs (agenda-sync,
     * weeralarm-check, weerlocatie-sync, dagelijkse weermeldingen) op hun oude configuratie staan
     * (of blijven helemaal uit als de backup auto-sync net aanzette) tot de gebruiker de app
     * handmatig sluit en heropent - dat is precies wat achter "agenda-instellingen worden niet
     * meegenomen door een backup" zit: de instellingen-bytes stonden er wel, maar de jobs die ze
     * uitvoeren draaiden nog op de oude staat. Zelfde reschedule-aanroepen als
     * AgendaWekkerApplication.onCreate() / BootCompletedReceiver, plus direct een nieuwe
     * eerstvolgende-alarm-berekening op de zojuist herstelde alarms.json.
     */
    private suspend fun rescheduleBackgroundWorkAfterRestore(context: Context) {
        try {
            if (SettingsManager.getAutoSyncEnabled(context)) {
                CalendarSyncWorker.schedule(context, ExistingPeriodicWorkPolicy.REPLACE)
            } else {
                CalendarSyncWorker.cancel(context)
            }
            WeatherLocationSyncWorker.syncScheduleWithSettings(context)
            WeatherAlertWorker.syncScheduleWithSettings(context)
            WeatherDailyAlertScheduler.rescheduleAll(context)
            AlarmScheduler.scheduleNextAlarm(context, "backup_restored")
        } catch (e: Exception) {
            // Best-effort: de restore zelf is al gelukt (alle data staat er) - mislukt dit
            // reschedule-stapje toch, dan lost een handmatige app-herstart het alsnog op (net als
            // vóór deze fix), dus dit mag de restore-flow niet als mislukt laten terugkomen.
            android.util.Log.w("BackupManager", "Kon achtergrondjobs niet herplannen na restore", e)
        }
    }

    /**
     * Herstelt custom alarmgeluiden (Room-rij + bestand) uit de backup. Moet vóór
     * clearOrphanedAlarmSoundUri draaien: als een geluid hier succesvol teruggehaald wordt,
     * bestaat het bijbehorende bestand weer en mag het niet alsnog als "orphaned" wegvallen.
     *
     * Primaire bron is de in de backup ingebedde audio zelf ([CustomSoundBackupEntry.audioBase64])
     * — dat werkt altijd, ongeacht Home Assistant-verbinding, en dekt ook geluiden die nooit naar
     * HA geüpload zijn (bv. toegevoegd via de per-trigger "Ander geluid"-picker, of zonder HA-
     * koppeling). Alleen bij oudere backups zonder embedded audio valt dit terug op de oude
     * HA-herdownload-route via [CustomSoundBackupEntry.haSoundUrl].
     *
     * Idempotent (slaat een entry over als storedFilename al lokaal bekend is) en per entry
     * best-effort: een kapotte embedded audio, geen HA-verbinding, een verwijderd bestand op HA,
     * of een timeout betekent gewoon dat die ene custom sound wegblijft - de rest van de restore
     * gaat door, en de bestaande sound-fallback (SettingsManager.resolvePlayableAlarmSoundUri)
     * zorgt dat een alarm dat er nog naar verwijst alsnog het standaardgeluid afspeelt.
     */
    private suspend fun restoreCustomSounds(context: Context, entries: List<CustomSoundBackupEntry>) {
        if (entries.isEmpty()) return

        val dao = AppDatabase.getDatabase(context).customAlarmSoundDao()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val soundsDir = File(context.filesDir, CUSTOM_SOUNDS_DIR).apply { mkdirs() }

        coroutineScope {
            entries.map { entry ->
                async {
                    try {
                        if (dao.getSoundByFilename(entry.storedFilename) != null) {
                            return@async // al aanwezig, niks te doen
                        }

                        val embeddedBytes = entry.audioBase64?.let { encoded ->
                            try {
                                Base64.decode(encoded, Base64.NO_WRAP)
                            } catch (e: Exception) {
                                android.util.Log.w("BackupManager", "Kon embedded audio voor '${entry.displayName}' niet decoderen", e)
                                null
                            }
                        }

                        val bytes = embeddedBytes ?: run {
                            // Terugval voor oudere backups zonder embedded audio.
                            val haSoundUrl = entry.haSoundUrl
                            if (haSoundUrl.isNullOrBlank()) {
                                android.util.Log.w("BackupManager", "Custom sound '${entry.displayName}' heeft geen embedded audio en geen haSoundUrl, kan niet herstellen")
                                return@async
                            }
                            val request = Request.Builder().url(haSoundUrl).get().build()
                            client.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                                response.body?.bytes() ?: throw Exception("Leeg antwoord")
                            }
                        }

                        File(soundsDir, entry.storedFilename).writeBytes(bytes)
                        dao.insert(
                            CustomAlarmSound(
                                displayName = entry.displayName,
                                originalFilename = entry.originalFilename,
                                storedFilename = entry.storedFilename,
                                originalUri = "",
                                durationMs = entry.durationMs,
                                fileSizeBytes = entry.fileSizeBytes,
                                mimeType = entry.mimeType,
                                haSoundUrl = entry.haSoundUrl
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("BackupManager", "Kon custom sound '${entry.displayName}' niet herstellen", e)
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * A restored `alarm_sound_uri` can point at a custom sound file/DB entry that only existed
     * on the old device and couldn't be restored above (no HA connection, file no longer on HA,
     * etc.). Playback already falls back to the default sound in that case
     * (SettingsManager.resolvePlayableAlarmSoundUri), but the pref itself would still show a
     * "selected" custom sound that no longer exists - clear it so the UI reflects what's
     * actually playable.
     */
    private fun clearOrphanedAlarmSoundUri(context: Context) {
        val prefs = context.getSharedPreferences(APP_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        val storedUri = prefs.getString("alarm_sound_uri", null) ?: return
        if (storedUri == SettingsManager.DEFAULT_ALARM_SOUND_URI) return
        val resolved = SettingsManager.resolvePlayableAlarmSoundUri(context, storedUri)
        if (resolved.toString() != storedUri) {
            SettingsManager.saveAlarmSoundUri(context, null)
        }
    }

    /**
     * Schrijft één DataStore-preference terug met het type dat in de backup is meegegeven
     * ("boolean:true", "int:5", "string_set:[...]", ...). Zie [preferencesToTypedStringMap].
     */
    private fun putTypedPreference(preferences: MutablePreferences, key: String, encodedValue: String) {
        when (val decoded = decodeSharedPreferenceValue(encodedValue)) {
            is Boolean -> preferences[booleanPreferencesKey(key)] = decoded
            is Float -> preferences[floatPreferencesKey(key)] = decoded
            is Double -> preferences[doublePreferencesKey(key)] = decoded
            is Int -> preferences[intPreferencesKey(key)] = decoded
            is Long -> preferences[longPreferencesKey(key)] = decoded
            is Set<*> -> preferences[stringSetPreferencesKey(key)] = decoded.filterIsInstance<String>().toSet()
            is String -> preferences[stringPreferencesKey(key)] = decoded
            else -> preferences[stringPreferencesKey(key)] = decoded.toString()
        }
    }

    /**
     * Alleen nog gebruikt voor backups van vóór [AppBackupData.triggerBehaviorEntriesTyped], waar
     * het type uit de naam van de key geraden moet worden. Nieuwe backups slaan het type gewoon op.
     *
     * Let op de volgorde in [restoreBackupData]: deze check gaat vóór [isTriggerBehaviorIntKey],
     * zodat `snooze_unlimited_<id>` als Boolean landt en niet als Int (beide matchen "snooze_").
     */
    private fun isTriggerBehaviorBooleanKey(key: String): Boolean {
        return key.endsWith("_enabled") ||
            key.endsWith("_fired") ||
            key.startsWith("already_fired_") ||
            key.startsWith("prevent_dismiss_") ||
            key.startsWith("check_user_") ||
            key.startsWith("vibrate_") ||
            key.startsWith("snooze_unlimited_") ||
            // Ontbrak hier, waardoor een DD Music-koppeling op een agenda-trigger als String
            // terugkwam onder een booleanPreferencesKey. Dat liet TriggerBehaviorStorage omvallen:
            // de agenda-alarmplanning gaf "Geen alarm" en de trigger-opties gingen niet open.
            key.startsWith("dd_music_linked_")
    }

    private fun isTriggerBehaviorIntKey(key: String): Boolean {
        return key.startsWith("snooze_count_") ||
            key.startsWith("snooze_") ||
            key.startsWith("volume_")
    }

    private fun restoreSnoozePrefs(context: Context, entries: Map<String, String>) {
        val editor = context.getSharedPreferences(SNOOZE_PREFS_NAME, Context.MODE_PRIVATE).edit().clear()
        entries.forEach { (key, value) ->
            val decoded = decodeSharedPreferenceValue(value)
            when (key) {
                SNOOZE_END_MS_KEY -> editor.putLong(key, decoded.toLongValue(value))
                SNOOZE_COUNT_KEY -> editor.putInt(key, decoded.toIntValue(value))
                else -> editor.putString(key, decoded.toPlainString(value))
            }
        }
        editor.commit()
    }

    private fun restoreLocalActivationPrefs(context: Context, entries: Map<String, String>) {
        val editor = context.getSharedPreferences(LOCAL_ACTIVATION_PREFS_NAME, Context.MODE_PRIVATE).edit().clear()
        entries.forEach { (key, value) ->
            editor.putStringSet(key, decodeStringSet(value))
        }
        editor.commit()
    }

    private fun restoreGenericSharedPrefs(sharedPreferences: SharedPreferences, entries: Map<String, String>) {
        val editor = sharedPreferences.edit().clear()
        entries.forEach { (key, value) ->
            when (val decoded = decodeSharedPreferenceValue(value)) {
                is Boolean -> editor.putBoolean(key, decoded)
                is Float -> editor.putFloat(key, decoded)
                is Int -> editor.putInt(key, decoded)
                is Long -> editor.putLong(key, decoded)
                is Set<*> -> editor.putStringSet(key, decoded.filterIsInstance<String>().toSet())
                is String -> editor.putString(key, decoded)
                else -> editor.putString(key, decoded.toString())
            }
        }
        editor.commit()
    }

    private fun decodeSharedPreferenceValue(value: String): Any {
        return when {
            value.startsWith("boolean:") -> value.removePrefix("boolean:").toBoolean()
            value.startsWith("float:") -> value.removePrefix("float:").toFloatOrNull() ?: 0f
            value.startsWith("double:") -> value.removePrefix("double:").toDoubleOrNull() ?: 0.0
            value.startsWith("int:") -> value.removePrefix("int:").toIntOrNull() ?: 0
            value.startsWith("long:") -> value.removePrefix("long:").toLongOrNull() ?: 0L
            value.startsWith("string:") -> value.removePrefix("string:")
            value.startsWith("string_set:") -> json.decodeFromString<List<String>>(value.removePrefix("string_set:")).toSet()
            else -> inferSharedPreferenceValue(value)
        }
    }

    private fun inferSharedPreferenceValue(value: String): Any {
        return when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            value.toIntOrNull() != null -> value.toInt()
            value.toLongOrNull() != null -> value.toLong()
            value.toFloatOrNull() != null -> value.toFloat()
            else -> value
        }
    }

    private fun Any.toLongValue(rawValue: String): Long {
        return when (this) {
            is Long -> this
            is Int -> toLong()
            is String -> toLongOrNull()
            else -> null
        } ?: rawValue.removeKnownPrefix().toLongOrNull() ?: 0L
    }

    private fun Any.toIntValue(rawValue: String): Int {
        return when (this) {
            is Int -> this
            is Long -> toInt()
            is String -> toIntOrNull()
            else -> null
        } ?: rawValue.removeKnownPrefix().toIntOrNull() ?: 0
    }

    private fun Any.toPlainString(rawValue: String): String {
        return when (this) {
            is String -> this
            else -> rawValue.removeKnownPrefix()
        }
    }

    private fun String.removeKnownPrefix(): String {
        val separatorIndex = indexOf(':')
        return if (separatorIndex > 0) substring(separatorIndex + 1) else this
    }

    private fun decodeStringSet(value: String): Set<String> {
        val decoded = decodeSharedPreferenceValue(value)
        if (decoded is Set<*>) return decoded.filterIsInstance<String>().toSet()
        val stringValue = decoded.toPlainString(value)
        return runCatching { json.decodeFromString<List<String>>(stringValue).toSet() }
            .getOrElse {
                stringValue.trim('[', ']')
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
    }

    private fun encryptBackup(clearBytes: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipherText = newCipher(Cipher.ENCRYPT_MODE, password, salt, iv).doFinal(clearBytes)
        return ByteArrayOutputStream().use { output ->
            output.write(magic)
            output.write(BACKUP_VERSION.toInt())
            output.write(salt)
            output.write(iv)
            output.write(cipherText)
            output.toByteArray()
        }
    }

    private fun decryptBackup(fileBytes: ByteArray, password: String): ByteArray {
        val headerSize = magic.size + 1 + SALT_BYTES + IV_BYTES
        if (fileBytes.size <= headerSize || fileBytes[0] != magic[0] || fileBytes[1] != magic[1]) {
            throw IllegalArgumentException("Ongeldig bestandsformaat")
        }
        if (fileBytes[2] != BACKUP_VERSION) {
            throw IllegalArgumentException("Niet-ondersteunde backupversie")
        }

        val saltStart = magic.size + 1
        val ivStart = saltStart + SALT_BYTES
        val cipherTextStart = ivStart + IV_BYTES
        val salt = fileBytes.copyOfRange(saltStart, ivStart)
        val iv = fileBytes.copyOfRange(ivStart, cipherTextStart)
        val cipherText = fileBytes.copyOfRange(cipherTextStart, fileBytes.size)
        return newCipher(Cipher.DECRYPT_MODE, password, salt, iv).doFinal(cipherText)
    }

    private fun newCipher(mode: Int, password: String, salt: ByteArray, iv: ByteArray): Cipher {
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, deriveKey(password, salt), GCMParameterSpec(128, iv))
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun writeBackupToDownloads(context: Context, bytes: ByteArray): Uri {
        val sequenceNumber = nextBackupSequenceNumber(context)
        val filename = "${backupFilePrefix(context)}_${sequenceNumber}_${timestamp()}.$BACKUP_EXTENSION"
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeBackupWithMediaStore(context, filename, bytes)
        } else {
            writeBackupDirectly(filename, bytes)
        }
        // Onthouden dat dit bestand van dit toestel komt, zodat de "backup gevonden"-melding bij
        // het openen van de app niet aanbiedt om je eigen zojuist gemaakte backup terug te zetten.
        markOwnBackupFilename(context, filename)
        // Kopie in app-specifieke externe opslag: altijd leesbaar door de app, ongeacht
        // Android-versie of opslagpermissies. Vangt het geval op dat MediaStore de backup
        // niet teruggeeft (bv. na herinstallatie of bij scoped-storage-beperkingen).
        try {
            val appBackupDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appBackupDir != null) {
                appBackupDir.mkdirs()
                File(appBackupDir, filename).writeBytes(bytes)
            }
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "Kon app-lokale kopie van backup niet schrijven", e)
        }
        return uri
    }

    private fun nextBackupSequenceNumber(context: Context): Int {
        val prefs = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
        val next = prefs.getInt(BACKUP_SEQUENCE_KEY, 0) + 1
        prefs.edit().putInt(BACKUP_SEQUENCE_KEY, next).commit()
        return next
    }

    private fun writeBackupWithMediaStore(context: Context, filename: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, BACKUP_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Kan backupbestand niet aanmaken")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Kan backupbestand niet schrijven")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun writeBackupDirectly(filename: String, bytes: ByteArray): Uri {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, filename)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun backupFilePrefix(context: Context): String {
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        return label.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "KalenderAlarm" }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
    }
}
