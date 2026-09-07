package com.dd.daykit

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistente forensische log voor "waarom ging het agenda-alarm niet af?".
 *
 * In tegenstelling tot [AgendaMoveDebugLog] en [AlarmRingingDebug] (logcat-only, weg zodra de
 * ringbuffer volloopt of het toestel herstart) schrijft dit object elke regel meteen naar schijf
 * en flusht de descriptor. Daardoor overleeft de log:
 *  - proces-dood (OOM-kill, force stop, battery optimizer),
 *  - een reboot,
 *  - uren wachten voordat de gebruiker het pas ontdekt.
 *
 * De volledige beslisketen van een agenda-alarm loopt door minstens vijf processen/componenten,
 * en op drie plekken kan een SLIM ALARM stil geblokkeerd worden zonder dat de gebruiker íets ziet:
 * [PreAlarmReceiver] (uit-bed-check op T-5min), [com.dd.daykit.rules.RuleEngine] STAP 3
 * (uit bed) en STAP 4 (smart-conditie). Zonder deze log is achteraf niet vast te stellen wélke
 * schakel het was — of dat het alarm überhaupt nooit gewapend stond.
 *
 * Bestand: `<externalFilesDir>/diagnostics/agenda_alarm_forensics.log` (+ één `.1` rotatie).
 */
object AgendaAlarmForensics {

    private const val TAG = "AgendaForensics"
    private const val DIR_NAME = "diagnostics"
    private const val FILE_NAME = "agenda_alarm_forensics.log"
    private const val MAX_BYTES = 1_000_000L
    private const val PREFS_NAME = "agenda_alarm_forensics"
    private const val KEY_EXPECTED_EPOCH = "expected_alarm_epoch"
    private const val KEY_EXPECTED_LABEL = "expected_alarm_label"
    private const val KEY_EXPECTED_ARMED_AT = "expected_armed_at"

    /** Categorieën — grep-baar met één woord per regel. */
    object Cat {
        const val PROCESS = "PROCESS"
        const val SCHEDULE = "SCHEDULE"
        const val ARM = "ARM"
        const val ARMED_CHECK = "ARMED_CHECK"
        const val PRE_ALARM = "PRE_ALARM"
        const val RECEIVE = "RECEIVE"
        const val RULE = "RULE"
        const val BLOCK = "BLOCK"
        const val SERVICE = "SERVICE"
        const val BOOT = "BOOT"
    }

    @Volatile
    private var appContext: Context? = null

    private val lock = Any()

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** Aanroepen in [AgendaWekkerApplication.onCreate] zodat receivers geen context hoeven door te geven. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun resolveContext(context: Context? = null): Context? =
        context?.applicationContext ?: appContext

    private fun logFile(context: Context): File? = try {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE_NAME)
    } catch (e: Exception) {
        Log.e(TAG, "logFile() failed", e)
        null
    }

    /**
     * Schrijft één regel en flusht meteen naar de fysieke descriptor. Duurder dan bufferen, maar
     * dat is precies het punt: als het proces een seconde later gekilld wordt moet de regel er staan.
     */
    fun log(cat: String, message: String, context: Context? = null) {
        val ctx = resolveContext(context) ?: return
        val line = "${stamp.format(Date())} [$cat] $message\n"
        Log.i(TAG, "[$cat] $message")
        synchronized(lock) {
            try {
                val file = logFile(ctx) ?: return
                if (file.exists() && file.length() > MAX_BYTES) {
                    val backup = File(file.parentFile, "$FILE_NAME.1")
                    if (backup.exists()) backup.delete()
                    file.renameTo(backup)
                }
                FileOutputStream(file, true).use { out ->
                    out.write(line.toByteArray())
                    out.flush()
                    out.fd.sync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "write failed", e)
            }
        }
    }

    fun wall(epochMillis: Long?): String =
        if (epochMillis == null || epochMillis <= 0L) "null"
        else "$epochMillis (${stamp.format(Date(epochMillis))})"

    fun describe(alarm: AlarmItem?): String =
        if (alarm == null) "null"
        else "id=${alarm.id} epoch=${wall(alarm.epochMillis)} trigger=${alarm.triggerId} label='${alarm.label.orEmpty().take(60)}'"

    // ---------------------------------------------------------------------------------------
    // Verwacht alarm — persistent, zodat een armed-check ook na proces-dood nog iets kan vergelijken.
    // ---------------------------------------------------------------------------------------

    fun rememberExpectedAlarm(context: Context, alarm: AlarmItem?) {
        try {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (alarm == null) {
                prefs.edit().remove(KEY_EXPECTED_EPOCH).remove(KEY_EXPECTED_LABEL)
                    .remove(KEY_EXPECTED_ARMED_AT).apply()
            } else {
                prefs.edit()
                    .putLong(KEY_EXPECTED_EPOCH, alarm.epochMillis)
                    .putString(KEY_EXPECTED_LABEL, alarm.label.orEmpty().take(60))
                    .putLong(KEY_EXPECTED_ARMED_AT, System.currentTimeMillis())
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "rememberExpectedAlarm failed", e)
        }
    }

    private fun expectedEpoch(context: Context): Long = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_EXPECTED_EPOCH, -1L)
    } catch (e: Exception) {
        -1L
    }

    /**
     * De kernvraag bij "het alarm ging gewoon niet af": hield het besturingssysteem het alarm op dat
     * moment überhaupt nog vast? [AlarmManager.getNextAlarmClock] geeft de eerstvolgende alarm-clock
     * van de gebruiker terug. Staat daar niets, of een ándere tijd dan wat wij dachten te hebben
     * gewapend, dan is het alarm tussentijds verdwenen — en niet geblokkeerd door onze eigen regels.
     *
     * Logt ook Doze/battery-saver, omdat dat de meest voorkomende reden is dat de herstellende
     * periodieke [CalendarSyncWorker] uren niet loopt.
     */
    fun snapshotArmedState(context: Context, phase: String) {
        try {
            val app = context.applicationContext
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = am.nextAlarmClock
            val expected = expectedEpoch(app)
            val osEpoch = next?.triggerTime
            val matches = when {
                expected <= 0L -> "no_expectation"
                osEpoch == null -> "OS_HAS_NO_ALARM_CLOCK"
                osEpoch == expected -> "match"
                else -> "MISMATCH_diffMs=${osEpoch - expected}"
            }

            // minSdk 26: isDeviceIdleMode / isIgnoringBatteryOptimizations zijn altijd beschikbaar.
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val idle = pm.isDeviceIdleMode
            val saver = pm.isPowerSaveMode
            val ignoringBattOpt = pm.isIgnoringBatteryOptimizations(app.packageName)
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

            log(
                Cat.ARMED_CHECK,
                "phase=$phase verdict=$matches expected=${wall(expected)} osNextAlarmClock=${wall(osEpoch)} " +
                    "inMemoryLastScheduled=${wall(AlarmScheduler.lastScheduledMainAlarm?.epochMillis)} " +
                    "canScheduleExact=$canExact deviceIdle=$idle powerSave=$saver ignoringBatteryOpt=$ignoringBattOpt",
                app
            )
        } catch (e: Exception) {
            Log.e(TAG, "snapshotArmedState failed", e)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Uitlezen / delen
    // ---------------------------------------------------------------------------------------

    /** Volledige log (oudste rotatie eerst), of null als er nog niets geschreven is. */
    fun readAll(context: Context): String? {
        return try {
            val file = logFile(context.applicationContext) ?: return null
            val backup = File(file.parentFile, "$FILE_NAME.1")
            val sb = StringBuilder()
            if (backup.exists()) sb.append(backup.readText())
            if (file.exists()) sb.append(file.readText())
            sb.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "readAll failed", e)
            null
        }
    }

    /** Absoluut pad, zodat het via USB/ADB op te halen is zonder deel-dialoog. */
    fun logPath(context: Context): String? = logFile(context.applicationContext)?.absolutePath

    /**
     * Opent de deel-dialoog met het logbestand als bijlage. Retourneert false als er nog niets
     * te delen valt. Vereist de FileProvider uit AndroidManifest (`${applicationId}.fileprovider`).
     */
    fun share(context: Context): Boolean {
        return try {
            val file = logFile(context.applicationContext) ?: return false
            if (!file.exists() || file.length() == 0L) return false
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "AgendaAlarm diagnose-log")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "Diagnose-log delen")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "share failed", e)
            false
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            try {
                val file = logFile(context.applicationContext) ?: return
                File(file.parentFile, "$FILE_NAME.1").delete()
                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "clear failed", e)
            }
        }
    }
}
