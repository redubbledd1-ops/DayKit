package com.dd.daykit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dd.daykit.data.HaPlayMediaResult
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.WeatherRepository
import com.dd.daykit.network.HomeAssistantClient
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Beoordeelt periodiek (elke 15 min) de event-gebonden weermeldingen: "Melding voor agenda item"
 * voor zowel slecht weer als temperatuurwissel — triggers die meebewegen met agenda-afspraken en
 * dus niet op een vast kloktijdstip te plannen zijn. Beide werken volledig onafhankelijk (eigen
 * agenda-selectie, eigen aan/uit, eigen "aantal meldingen per dag"-limiet) en kunnen elk voor
 * meerdere agenda-items op 1 dag afgaan; alleen als ze toevallig voor dezelfde afspraak op
 * hetzelfde moment afgaan, worden ze samengevoegd tot 1 melding.
 *
 * De vaste-kloktijd-triggers ("Dag ervoor", "Zelfde dag", "Dag van tevoren") lopen NIET meer via
 * deze worker maar via [WeatherDailyAlertScheduler] met exacte AlarmManager-alarmen (zelfde
 * precisie als de wekker) — een 15-min poll was daarvoor te grof.
 */
class WeatherAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "weather_alert_periodic"
        const val INTERVAL_MINUTES = 15L

        /**
         * Genade-marge (in minuten) NÁ het trigger-moment. Zonder dit sluit het venster keihard
         * op het exacte trigger-moment (bv. bij "0 min van tevoren" is dat het exacte begin van de
         * afspraak) - een net-te-laat gedraaide 15-min-poll (Doze/batterijbesparing kan die
         * makkelijk uitstellen) of een "Test nu"-druk vlak ná dat moment miste de melding dan voor
         * altijd, ook al is de afspraak nog maar net begonnen.
         *
         * BELANGRIJK: dit venster loopt alleen NÁ het trigger-moment, nooit ervoor (zie
         * collectBadWeatherFires/collectTempChangeFires: `minutesUntilTrigger !in
         * -TRIGGER_PAST_GRACE_MINUTES..0`). Er bestond hiervoor ook een "TIME_MATCH_WINDOW_MINUTES"
         * die tot 20 min VOOR het trigger-moment al liet afgaan (nodig toen er alleen een grove
         * 15-min-poll was) - nu [WeatherEventAlertScheduler] een exact AlarmManager-alarm precies op
         * het trigger-moment plant, zou die pre-tolerantie alleen nog voor te vroege meldingen zorgen
         * zodra de 15-min-poll (die als vangnet blijft bestaan) een afspraak toevallig al vroeg genoeg
         * zag aankomen - dus verwijderd.
         */
        const val TRIGGER_PAST_GRACE_MINUTES = 10

        /** Sleutel-prefixen voor de per-dag-tellers van de "aantal meldingen per dag"-limiet. */
        private const val BADWEATHER_COUNT_PREFIX = "badweather_beforeevent"
        private const val TEMPCHANGE_COUNT_PREFIX = "tempchange_firstevent"

        /** Zelfde prefs-bestand als de instance-property [dedupPrefs] hieronder — 1 bron van waarheid. */
        private const val DEDUP_PREFS_NAME = "weather_alert_dedup"

        const val CHANNEL_ID = "weather_alerts"
        private const val NOTIFICATION_ID_BASE = 9200

        private const val TAG = "WeatherAlertWorker"

        fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
            Log.i(TAG, "Scheduled every ${INTERVAL_MINUTES}min policy=${policy.name}")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Periodic weather alert check cancelled")
        }

        /**
         * TEST: wist alle "al afgegaan"-status voor de event-gebonden meldingen (zowel de
         * per-afspraak-vlaggen als de dag-tellers voor de max-per-dag-limiet). Zonder dit blijft
         * dezelfde test-afspraak de rest van de dag overgeslagen — dit maakt een test-afspraak dus
         * opnieuw bruikbaar zonder tot middernacht te wachten of 'm te hoeven verplaatsen.
         */
        fun resetTestDedup(context: Context) {
            context.applicationContext.getSharedPreferences(DEDUP_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply()
            Log.i(TAG, "Test-dedup gewist - agenda-meldingen mogen weer afgaan")
        }

        /**
         * TEST: forceert direct 1 check van de event-gebonden meldingen (slecht weer + temp-
         * wissel, "Melding voor agenda item"), i.p.v. te wachten op de eerstvolgende 15-min-poll.
         * Wist ALTIJD eerst de dedup-status ([resetTestDedup]) — dit is een expliciete test-actie,
         * geen automatische periodieke check, dus mag (en moet) telkens opnieuw kunnen afgaan zonder
         * dat je los op "Reset test-status" hoeft te drukken. Draait normaal binnen enkele seconden
         * (zelfde constraints als de periodieke poll, dus geen netwerk-eis).
         */
        fun runOnceNow(context: Context) {
            resetTestDedup(context)
            val request = OneTimeWorkRequestBuilder<WeatherAlertWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
            Log.i(TAG, "Handmatige test-check ingepland (direct, dedup gereset)")
        }

        /** Plant/annuleert op basis van de "Meldingen inschakelen"-instelling. */
        fun syncScheduleWithSettings(context: Context) {
            if (SettingsManager.getWeatherNotificationsEnabled(context)) {
                schedule(context, ExistingPeriodicWorkPolicy.KEEP)
            } else {
                cancel(context)
            }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                LanguageManager.getString("weather_channel_name"),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = LanguageManager.getString("weather_channel_description")
            }
            nm.createNotificationChannel(channel)
        }

        /**
         * Levert de melding af volgens 3 los in te stellen, onafhankelijke kanalen (allemaal
         * tegelijk mogelijk): "Melding" (gewone stille pushmelding), "Pop-up" (volledig scherm met
         * geluid, zoals de wekker) en "Uitspreken" (HA-speaker met telefoon-tts-terugval, zie
         * speakWeatherAlertIfEnabled). `suspend` omdat de tts-call een netwerkverzoek is — alle
         * aanroepers (WeatherAlertWorker.evaluateEventAlerts, WeatherDailyAlertReceiver.handleTrigger)
         * draaien al in een coroutine.
         *
         * Let op: Pop-up leunt technisch op Android's full-screen-intent-mechanisme, wat alleen
         * werkt als het aan een notificatie hangt - staat Pop-up aan (ook als "Melding" zelf uit
         * staat), dan verschijnt er dus toch een notificatie-item als drager. Dat is een
         * platformbeperking, geen losse "Melding"-instelling die dat kan voorkomen.
         */
        suspend fun deliverAlert(context: Context, title: String, message: String, notificationKey: Int) {
            val notifyEnabled = SettingsManager.getWeatherNotifyEnabled(context)
            val popupEnabled = SettingsManager.getWeatherPopupEnabled(context)

            if (notifyEnabled || popupEnabled) {
                ensureChannel(context)
                val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Log.w(TAG, "Geen notificatiepermissie — melding/pop-up overgeslagen: $title")
                } else {
                    val openAppIntent = PendingIntent.getActivity(
                        context,
                        NOTIFICATION_ID_BASE + notificationKey,
                        Intent(context, WeatherActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.clockgood)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setAutoCancel(true)
                        .setContentIntent(openAppIntent)

                    if (popupEnabled) {
                        val popupIntent = Intent(context, WeatherAlertPopupActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(WeatherAlertPopupActivity.EXTRA_TITLE, title)
                            putExtra(WeatherAlertPopupActivity.EXTRA_MESSAGE, message)
                        }
                        val popupPi = PendingIntent.getActivity(
                            context,
                            NOTIFICATION_ID_BASE + notificationKey + 1,
                            popupIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.setFullScreenIntent(popupPi, true)
                    }

                    try {
                        androidx.core.app.NotificationManagerCompat.from(context)
                            .notify(NOTIFICATION_ID_BASE + notificationKey, builder.build())
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Notify geweigerd (permissie ingetrokken?)", e)
                    }
                }
            }

            speakWeatherAlertIfEnabled(context, title, message)
        }

        /**
         * Spreekt [title] + [message] uit, als "Weeralarm uitspreken" aan staat. De spraak wordt
         * altijd lokaal gegenereerd (Android TextToSpeech, zie [synthesizeToFile]) - er is geen
         * HA tts-platform meer nodig. Is er een bereikbare weer-speaker geconfigureerd, dan wordt
         * het gegenereerde audiobestand naar HA geupload en daar afgespeeld via
         * media_player.play_media (zie [playOnHaSpeaker]); anders (of bij falen) valt dit terug
         * op de eigen tekst-naar-spraak van de telefoon zelf - zo wordt de melding altijd hardop
         * voorgelezen, ook zonder (werkende) HA-koppeling. Staat de speaker-modus (zie
         * SpeakerModal.kt) op "Beide", dan spreekt dit ook daadwerkelijk beide tegelijk uit i.p.v.
         * alleen HA-of-telefoon. Best-effort in alle gevallen: de popup/notificatie hierboven is
         * al afgeleverd en moet nooit van deze stap afhangen.
         */
        private suspend fun speakWeatherAlertIfEnabled(context: Context, title: String, message: String) {
            val fullMessage = "$title. $message"
            try {
                val storage = HomeAssistantSettingsStorage(context.applicationContext)
                val repository = HomeAssistantRepository(HomeAssistantClient, storage)
                val settings = repository.getSettings()
                if (!settings.weatherTtsEnabled) {
                    return
                }
                // Sinds de speaker-splitsing heeft Weer zijn eigen speaker-keuze (weatherSpeaker),
                // los van het wek-/backup-alarm.
                val speakerEntity = settings.weatherSpeaker.entityId

                val haSpeakerUsable = !speakerEntity.isNullOrBlank() &&
                    isSpeakerReachable(repository, speakerEntity)
                val alsoPhone = settings.weatherSpeaker.mode == com.dd.daykit.data.ExternalSpeakerMode.BOTH

                if (haSpeakerUsable) {
                    val speaker = settings.weatherSpeaker
                    val played = playOnHaSpeaker(
                        context.applicationContext, repository, speakerEntity!!, fullMessage,
                        speaker.volume, speaker.skipVolume
                    )
                    if (!played) {
                        Log.w(TAG, "Weeralarm-tts (HA) mislukt - val terug op telefoon")
                        speakOnPhone(context.applicationContext, fullMessage)
                    } else if (alsoPhone) {
                        Log.d(TAG, "Weeralarm-tts: modus 'Beide' - spreek ook uit via telefoon")
                        speakOnPhone(context.applicationContext, fullMessage)
                    }
                } else {
                    Log.d(TAG, "Weeralarm-tts: geen (bereikbare) HA-speaker geconfigureerd, spreek uit via telefoon")
                    speakOnPhone(context.applicationContext, fullMessage)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Weeralarm-tts overgeslagen door onverwachte fout", e)
            }
        }

        /**
         * Genereert [message] lokaal (Android TextToSpeech, zie [synthesizeToFile]), uploadt het
         * resultaat naar HA (weather_tts_upload) en speelt het af op [speakerEntity] via
         * media_player.play_media - vervangt de eerdere tts.speak-aanroep op een los in HA
         * geconfigureerd tts-platform (dat concept bestaat niet meer, zie
         * HaSettingsViewModel/WeatherActivity). Respecteert settings.weatherSpeaker.volume/
         * skipVolume net als een gewoon alarm (zie ExternalSpeakerHelper.playAlarmOnSpeaker).
         * Ruimt het tijdelijke bestand altijd op, ongeacht of het uploaden/afspelen lukte.
         */
        private suspend fun playOnHaSpeaker(
            context: Context,
            repository: HomeAssistantRepository,
            speakerEntity: String,
            message: String,
            volume: Int,
            skipVolume: Boolean
        ): Boolean {
            val file = synthesizeToFile(context, message) ?: return false
            return try {
                val audioBytes = file.readBytes()
                val url = repository.uploadWeatherTtsAndGetUrl(audioBytes)
                if (url == null) {
                    Log.w(TAG, "Weeralarm-tts: upload naar HA mislukt")
                    return false
                }

                if (!skipVolume) {
                    val volumeResult = repository.setSpeakerVolume(speakerEntity, volume.coerceIn(0, 100))
                    if (volumeResult is HaPlayMediaResult.Error) {
                        Log.w(TAG, "Weeralarm-tts: setSpeakerVolume mislukt: ${volumeResult.message}")
                    }
                }

                val result = repository.playMediaOnSpeaker(
                    entityId = speakerEntity,
                    mediaContentId = url,
                    mediaContentType = "music"
                )
                when (result) {
                    is HaPlayMediaResult.Success -> true
                    is HaPlayMediaResult.Error -> {
                        Log.w(TAG, "Weeralarm-tts: play_media mislukt: ${result.message}")
                        false
                    }
                }
            } finally {
                file.delete()
            }
        }

        /**
         * Rendert [message] naar een tijdelijk WAV-bestand via Android's eigen tekst-naar-spraak
         * (los van Home Assistant), voor het uploaden naar de HA-speaker (zie [playOnHaSpeaker]).
         * Zelfde taal-resolutie als [speakOnPhone] (app-taal, terugval op systeemtaal). Het
         * bestand komt in de cache-map te staan - de aanroeper ruimt het zelf op na gebruik.
         * Best-effort: geen geïnstalleerde/werkende tts-engine resulteert alleen in `null`.
         */
        private suspend fun synthesizeToFile(context: Context, message: String): File? {
            return try {
                withTimeoutOrNull(8000) {
                    kotlinx.coroutines.suspendCancellableCoroutine<File?> { cont ->
                        var tts: android.speech.tts.TextToSpeech? = null
                        val outFile = File(context.cacheDir, "weather_tts_${System.currentTimeMillis()}.wav")
                        tts = android.speech.tts.TextToSpeech(context) { status ->
                            if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                                Log.w(TAG, "Telefoon-tts (voor HA-speaker) init mislukt (status=$status)")
                                tts?.shutdown()
                                if (cont.isActive) cont.resume(null)
                                return@TextToSpeech
                            }
                            val appLocale = com.dd.daykit.LanguageManager.currentLanguage.value.locale
                            val supportResult = tts?.isLanguageAvailable(appLocale)
                            tts?.language = if (supportResult != null &&
                                supportResult >= android.speech.tts.TextToSpeech.LANG_AVAILABLE
                            ) appLocale else java.util.Locale.getDefault()

                            val utteranceId = "weather_tts_file_${System.currentTimeMillis()}"
                            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(id: String?) {}
                                override fun onDone(id: String?) {
                                    tts?.shutdown()
                                    val ok = outFile.exists() && outFile.length() > 0
                                    if (cont.isActive) cont.resume(if (ok) outFile else null)
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onError(id: String?) {
                                    Log.w(TAG, "Telefoon-tts (voor HA-speaker) synthesizeToFile-fout")
                                    tts?.shutdown()
                                    if (cont.isActive) cont.resume(null)
                                }
                            })
                            val queued = tts?.synthesizeToFile(message, null, outFile, utteranceId)
                            if (queued != android.speech.tts.TextToSpeech.SUCCESS) {
                                Log.w(TAG, "Telefoon-tts synthesizeToFile() gaf geen SUCCESS terug")
                                tts?.shutdown()
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                        cont.invokeOnCancellation { tts?.shutdown() }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "synthesizeToFile overgeslagen door fout", e)
                null
            }
        }

        /**
         * Zelfde probleem als bij het wek-/backup-alarm (zie ExternalSpeakerHelper.playAlarmOnSpeaker):
         * tts.speak geeft ook bij een niet-bestaande/offline speaker-entiteit gewoon HTTP 200
         * terug, dus zonder deze check "lukt" de aanroep altijd terwijl er niets wordt
         * uitgesproken. GET /api/states/<entity_id> geeft wél een 404/foutstatus terug.
         */
        private suspend fun isSpeakerReachable(repository: HomeAssistantRepository, speakerEntity: String): Boolean {
            val check = withTimeoutOrNull(3000) {
                try {
                    repository.getEntityState(speakerEntity)
                } catch (e: Exception) {
                    Log.w(TAG, "Weeralarm-tts: speaker $speakerEntity niet gevonden/bereikbaar: ${e.message}")
                    null
                }
            }
            if (check == null) return false
            if (check.state.equals("unavailable", ignoreCase = true) ||
                check.state.equals("unknown", ignoreCase = true)
            ) {
                Log.w(TAG, "Weeralarm-tts: speaker $speakerEntity is offline (state=${check.state})")
                return false
            }
            return true
        }

        /**
         * Publieke ingang voor de "Test weermelding op telefoon"-knop in het weerscherm (zie
         * SpeakerModal.kt/WeatherActivity.kt): spreekt een korte testzin uit via de eigen tts van
         * de telefoon, volledig los van Home Assistant - er wordt geen geluidsbestand afgespeeld
         * en geen HA-speaker aangesproken, alleen [speakOnPhone] hieronder.
         */
        suspend fun testWeatherSpeechOnPhone(context: Context) {
            speakOnPhone(context.applicationContext, LanguageManager.getString("weather_test_speech_message"))
        }

        /**
         * Publieke ingang voor de weer-testknop wanneer de gekozen speaker-modus de HA-speaker
         * gebruikt ("Standaard"/"Beide", zie SpeakerModal.kt/HaSettingsViewModel.testWeatherSpeaker):
         * spreekt dezelfde testzin uit via [playOnHaSpeaker], op de nog-niet-opgeslagen
         * speaker/volume-instellingen uit de modal i.p.v. de opgeslagen settings (die kunnen nog
         * afwijken zolang de modal niet is opgeslagen). Geeft, anders dan de telefoon-tts-test,
         * wel een succes/fout terug omdat er een echte HA-aanroep (upload + play_media) achter zit.
         */
        suspend fun testWeatherSpeechOnHaSpeaker(
            context: Context,
            speakerEntityId: String,
            volume: Int,
            skipVolume: Boolean
        ): Boolean {
            return try {
                val storage = HomeAssistantSettingsStorage(context.applicationContext)
                val repository = HomeAssistantRepository(HomeAssistantClient, storage)
                val message = LanguageManager.getString("weather_test_speech_message")
                playOnHaSpeaker(context.applicationContext, repository, speakerEntityId, message, volume, skipVolume)
            } catch (e: Exception) {
                Log.w(TAG, "testWeatherSpeechOnHaSpeaker overgeslagen door fout", e)
                false
            }
        }

        /**
         * Leest [message] hardop voor via de eigen tekst-naar-spraak van de telefoon (los van
         * Home Assistant). Gebruikt de taal die in de app is ingesteld (LanguageManager), met
         * terugval op de systeemtaal als het tts-engine die taal niet ondersteunt. Best-effort:
         * geen geïnstalleerde/werkende tts-engine op het toestel resulteert alleen in een
         * gelogde waarschuwing, nooit een crash.
         */
        private suspend fun speakOnPhone(context: Context, message: String) {
            try {
                withTimeoutOrNull(8000) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                        var tts: android.speech.tts.TextToSpeech? = null
                        tts = android.speech.tts.TextToSpeech(context) { status ->
                            if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                                Log.w(TAG, "Telefoon-tts init mislukt (status=$status)")
                                tts?.shutdown()
                                if (cont.isActive) cont.resume(Unit)
                                return@TextToSpeech
                            }
                            val appLocale = com.dd.daykit.LanguageManager.currentLanguage.value.locale
                            val supportResult = tts?.isLanguageAvailable(appLocale)
                            tts?.language = if (supportResult != null &&
                                supportResult >= android.speech.tts.TextToSpeech.LANG_AVAILABLE
                            ) appLocale else java.util.Locale.getDefault()

                            val utteranceId = "weather_alert_${System.currentTimeMillis()}"
                            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(id: String?) {}
                                override fun onDone(id: String?) {
                                    tts?.shutdown()
                                    if (cont.isActive) cont.resume(Unit)
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onError(id: String?) {
                                    Log.w(TAG, "Telefoon-tts spreekfout")
                                    tts?.shutdown()
                                    if (cont.isActive) cont.resume(Unit)
                                }
                            })
                            val spoken = tts?.speak(message, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                            if (spoken != android.speech.tts.TextToSpeech.SUCCESS) {
                                Log.w(TAG, "Telefoon-tts speak() gaf geen SUCCESS terug")
                                tts?.shutdown()
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }
                        cont.invokeOnCancellation { tts?.shutdown() }
                    }
                } ?: Log.w(TAG, "Telefoon-tts timeout (8s)")
            } catch (e: Exception) {
                Log.w(TAG, "Telefoon-tts overgeslagen door fout", e)
            }
        }
    }

    private val dedupPrefs by lazy {
        applicationContext.getSharedPreferences(DEDUP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** True als [key] vandaag al is afgegaan (leest alleen, vuurt niets af). */
    private fun hasFiredToday(key: String, today: LocalDate): Boolean {
        return dedupPrefs.getString(key, null) == today.toString()
    }

    /** Markeert [key] als afgegaan voor vandaag — voorkomt dat dezelfde afspraak nogmaals afgaat. */
    private fun markFiredToday(key: String, today: LocalDate) {
        dedupPrefs.edit().putString(key, today.toString()).apply()
    }

    /** Aantal keer dat [prefix] vandaag al is afgegaan — voor de "aantal meldingen per dag"-limiet. */
    private fun countFiredToday(prefix: String, today: LocalDate): Int {
        val storedDate = dedupPrefs.getString("${prefix}_count_date", null)
        if (storedDate != today.toString()) return 0
        return dedupPrefs.getInt("${prefix}_count", 0)
    }

    /** Verhoogt de dagteller voor [prefix] (reset vanzelf zodra de datum wisselt). */
    private fun incrementFiredCount(prefix: String, today: LocalDate) {
        val current = countFiredToday(prefix, today)
        dedupPrefs.edit()
            .putString("${prefix}_count_date", today.toString())
            .putInt("${prefix}_count", current + 1)
            .apply()
    }

    override suspend fun doWork(): Result {
        if (!SettingsManager.getWeatherNotificationsEnabled(applicationContext)) {
            Log.w(TAG, "doWork(): 'Meldingen inschakelen' staat uit - hele check overgeslagen (ook Test nu doet dan niks)")
            WeatherEventAlertScheduler.cancel(applicationContext)
            return Result.success()
        }
        val result = try {
            evaluate()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weather alert evaluation failed", e)
            Result.retry()
        }
        // Plant het exacte wake-alarm voor de eerstvolgende event-gebonden melding opnieuw, na
        // ELKE run (periodieke poll, "Test nu" of het exacte wake-alarm zelf) - zie
        // WeatherEventAlertScheduler voor waarom dit nodig is naast de 15-min-poll hierboven.
        // Onafhankelijk van success/retry: rescheduleNext() leunt alleen op agenda + instellingen.
        WeatherEventAlertScheduler.rescheduleNext(applicationContext)
        return result
    }

    private suspend fun evaluate() {
        val context = applicationContext
        val repo = WeatherRepository()
        val lat = SettingsManager.getWeatherLatitude(context)
        val lon = SettingsManager.getWeatherLongitude(context)
        val model = SettingsManager.getWeatherModel(context)
        // Ook nodig voor de "hele dag"-variant van de event-gebonden meldingen (whole-day scan
        // i.p.v. het exacte tijdstip van het agenda-item) — de gecachete repo maakt dit vrijwel
        // gratis voor de sub-checks die 'm hierna nogmaals opvragen.
        val forecastResult = repo.getForecast(lat, lon, model)
        val forecast = forecastResult.getOrNull()
        if (forecast == null) {
            Log.w(TAG, "evaluate(): geen forecast opgehaald (lat=$lat lon=$lon model=$model) - ${forecastResult.exceptionOrNull()} - stop")
            return
        }

        val now = System.currentTimeMillis()
        val today = LocalDateTime.now(ZoneId.systemDefault()).toLocalDate()

        // Slecht weer en temperatuurwissel hebben elk hun eigen agenda-selectie (Slecht weer >
        // Agenda items / Grote temperatuurverandering > Agenda items) en werken volledig
        // onafhankelijk van elkaar (eigen aan/uit, eigen "aantal meldingen per dag"-limiet) — de
        // enige plek waar ze elkaar raken is de samenvoeging in [evaluateEventAlerts], voor het
        // geval ze toevallig voor dezelfde afspraak op hetzelfde moment afgaan. Als "Koppelen aan
        // agenda-afspraken" uit staat, worden deze event-gebonden triggers helemaal overgeslagen.
        val linkToCalendar = SettingsManager.getWeatherLinkToCalendar(context)
        val badWeatherAlertEnabled = SettingsManager.getWeatherAlertBeforeEventEnabled(context)
        val badWeatherEvents = if (!linkToCalendar || !badWeatherAlertEnabled) {
            emptyList()
        } else {
            // WEATHER_EVENT_SCAN (i.p.v. NEXT_7_DAYS) laat een net-gestarte afspraak nog binnen -
            // de precieze "hoort dit nog te triggeren"-beslissing gebeurt hieronder via
            // minutesUntilTrigger, dus hier NIET nogmaals hard op `epochMillis > now` filteren
            // (dat sneed voorheen exact de "0 min van tevoren / bij aanvang"-testcase eruit).
            getUpcomingWakeUpEvents(context, CalendarView.WEATHER_EVENT_SCAN, SettingsManager.getWeatherBadWeatherCalendarIds(context))
        }
        Log.i(
            TAG,
            "evaluate(): linkToCalendar=$linkToCalendar badWeatherAlertEnabled=$badWeatherAlertEnabled " +
                "badWeatherCalendarIds=${SettingsManager.getWeatherBadWeatherCalendarIds(context)} " +
                "badWeatherEvents=${badWeatherEvents.map { it.label + "@" + it.epochMillis }}"
        )
        val tempChangeEnabled = SettingsManager.getWeatherTempChangeEnabled(context)
        val tempChangeFirstEventEnabled = SettingsManager.getWeatherTempChangeAlertFirstEventEnabled(context)
        val tempChangeEvents = if (!linkToCalendar || !tempChangeEnabled || !tempChangeFirstEventEnabled) {
            emptyList()
        } else {
            getUpcomingWakeUpEvents(context, CalendarView.WEATHER_EVENT_SCAN, SettingsManager.getWeatherTempChangeCalendarIds(context))
        }
        Log.i(
            TAG,
            "evaluate(): tempChangeEnabled=$tempChangeEnabled firstEventEnabled=$tempChangeFirstEventEnabled " +
                "tempChangeCalendarIds=${SettingsManager.getWeatherTempChangeCalendarIds(context)} " +
                "tempChangeEvents=${tempChangeEvents.map { it.label + "@" + it.epochMillis }}"
        )

        evaluateEventAlerts(context, repo, forecast, lat, lon, model, now, today, badWeatherEvents, tempChangeEvents)
    }

    // ── Event-gebonden meldingen: "Melding voor agenda item" (slecht weer + temperatuurwissel) ──

    private data class BadWeatherFire(val event: AlarmItem, val labels: List<WeatherReason>, val rainProbability: Int? = null)
    private data class TempChangeFire(val event: AlarmItem, val diff: Double, val threshold: Int, val maxTemp: Double)

    /**
     * Verzamelt welke aankomende agenda-items daadwerkelijk een melding verdienen (elk begrensd
     * door zijn eigen "aantal meldingen per dag"), en levert ze af — samengevoegd tot 1 melding
     * als slecht-weer en temperatuurwissel toevallig voor dezelfde afspraak op hetzelfde moment
     * afgaan, anders gewoon los.
     */
    private suspend fun evaluateEventAlerts(
        context: Context,
        repo: WeatherRepository,
        forecast: WeatherForecast,
        lat: Double,
        lon: Double,
        model: String,
        now: Long,
        today: LocalDate,
        badWeatherEvents: List<AlarmItem>,
        tempChangeEvents: List<AlarmItem>
    ) {
        val badWeatherFires = collectBadWeatherFires(context, repo, forecast, lat, lon, model, now, today, badWeatherEvents)
        val tempChangeFires = collectTempChangeFires(context, repo, forecast, lat, lon, model, now, today, tempChangeEvents)
        val usedTempChangeIndices = mutableSetOf<Int>()

        badWeatherFires.forEach { fire ->
            val badLabel = fire.labels.joinToString(", ") { it.render(capitalizeFirst = false) }
            val badKey = "badweather_beforeevent_${fire.event.id}_${fire.event.epochMillis}"
            // Een melding "X minuten voor vertrek" kan best de avond ervoor binnenkomen voor een
            // afspraak van morgenochtend. Er stond dan nergens bij over welke dag het ging - deze
            // meldingen gingen altijd uit van vandaag.
            val badDay = weatherAlertDayFor(fire.event.epochMillis, now)
            val matchIndex = tempChangeFires.indexOfFirst {
                it.event.id == fire.event.id && it.event.epochMillis == fire.event.epochMillis
            }

            if (matchIndex >= 0 && matchIndex !in usedTempChangeIndices) {
                usedTempChangeIndices.add(matchIndex)
                val tempFire = tempChangeFires[matchIndex]
                val tempKey = "tempchange_firstevent_${tempFire.event.id}_${tempFire.event.epochMillis}"
                markFiredToday(badKey, today)
                markFiredToday(tempKey, today)
                incrementFiredCount(BADWEATHER_COUNT_PREFIX, today)
                incrementFiredCount(TEMPCHANGE_COUNT_PREFIX, today)
                val combined = "$badLabel, ${formatTempChangeMessage(tempFire.diff, tempFire.threshold)}"
                val message = LanguageManager.getString("weather_around_event").replace("{combined}", combined).replace("{event}", fire.event.label)
                WeatherAlertWorker.deliverAlert(
                    context,
                    withTomorrowPrefix(LanguageManager.getString("weather_notification_title"), badDay),
                    message,
                    notificationKey = 10
                )
            } else {
                markFiredToday(badKey, today)
                incrementFiredCount(BADWEATHER_COUNT_PREFIX, today)
                // Puur regen: titel = de kwalitatieve tekst zelf ("Kleine kans op regen"), bericht =
                // het percentage. Anders (meerdere/andere redenen): titel = reden, bericht = kans.
                val (title, chanceText) = if (fire.labels.size == 1 && fire.rainProbability != null) {
                    // toRainNotificationParts zet "Morgen" zelf voorop, dus die titel is al compleet.
                    fire.labels.first().toRainNotificationParts(isTomorrow = badDay == WeatherAlertDay.TOMORROW)
                } else {
                    val (t, c) = combineWeatherReasonsForNotification(fire.labels)
                    withTomorrowPrefix(t, badDay) to c
                }
                val message = LanguageManager.getString("weather_around_event").replace("{combined}", chanceText).replace("{event}", fire.event.label)
                WeatherAlertWorker.deliverAlert(context, title, message, notificationKey = 10)
            }
        }

        tempChangeFires.forEachIndexed { index, fire ->
            if (index in usedTempChangeIndices) return@forEachIndexed
            val tempKey = "tempchange_firstevent_${fire.event.id}_${fire.event.epochMillis}"
            markFiredToday(tempKey, today)
            incrementFiredCount(TEMPCHANGE_COUNT_PREFIX, today)
            // De dag van het agenda-item bepaalt de tekst; hier stond altijd de "morgen"-variant,
            // ook bij een afspraak van vandaag.
            val title = formatTempChangeTitle(context, fire.maxTemp, weatherAlertDayFor(fire.event.epochMillis, now))
            val message = LanguageManager.getString("weather_around_event").replace("{combined}", formatTempChangeMessage(fire.diff, fire.threshold)).replace("{event}", fire.event.label)
            WeatherAlertWorker.deliverAlert(context, title, message, notificationKey = 22)
        }
    }

    /**
     * Slecht weer — zelfde detectie als de startscherm-kaart: basis regen/onweer plus alle opt-in
     * "Extra weersomstandigheden". Doorloopt de agenda-items chronologisch en stopt zodra de
     * "aantal meldingen per dag"-limiet bereikt is (inclusief wat vandaag al is afgegaan).
     *
     * "Hele dag"-instelling uit (standaard): kijkt puur naar het weer op het exacte tijdstip van
     * het agenda-item. Aan: kijkt naar de algemene voorspelling van de hele kalenderdag waarop het
     * agenda-item valt (zelfde scan als Dag ervoor/Zelfde dag) — de melding blijft in beide
     * gevallen getimed rond het agenda-item zelf.
     */
    private suspend fun collectBadWeatherFires(
        context: Context,
        repo: WeatherRepository,
        forecast: WeatherForecast,
        lat: Double,
        lon: Double,
        model: String,
        now: Long,
        today: LocalDate,
        events: List<AlarmItem>
    ): List<BadWeatherFire> {
        if (events.isEmpty()) return emptyList()
        val rainEnabled = SettingsManager.getWeatherRainAlarmEnabled(context)
        val rainThreshold = SettingsManager.getWeatherRainThreshold(context)
        val rainMinutesBefore = SettingsManager.getWeatherRainMinutesBefore(context)
        // TEST: "Geen maximum"-toggle negeert de dag-limiet tijdelijk, zodat tijdens testen niet
        // onopgemerkt tegen de cap aan gelopen wordt.
        val maxPerDay = if (SettingsManager.getWeatherAlertBeforeEventNoMax(context)) {
            Int.MAX_VALUE
        } else {
            SettingsManager.getWeatherAlertBeforeEventMaxPerDay(context)
        }
        val wholeDay = SettingsManager.getWeatherAlertScopeWholeDay(context)
        // "Bereik rond tijdstip": 1 (standaard) = ongewijzigd, puur het exacte uur. >1 = kijkt ook
        // naar het weer tot dit ver vóór/ná het agenda-item (zelfde scan-techniek als "Hele dag",
        // maar met een instelbaar venster i.p.v. de volledige kalenderdag).
        val rangeMinutes = SettingsManager.getWeatherAlertBeforeEventRangeMinutes(context)
        val zone = ZoneId.systemDefault()

        var fired = countFiredToday(BADWEATHER_COUNT_PREFIX, today)
        val result = mutableListOf<BadWeatherFire>()

        for (event in events) {
            if (fired >= maxPerDay) {
                Log.d(TAG, "collectBadWeatherFires: maxPerDay ($maxPerDay) al bereikt, stop")
                break
            }
            // Punt-in-tijd zoals bij temp-verandering (zie collectTempChangeFires): "Controleer X
            // min voor vertrek" is de gewenste voorsprong t.o.v. de afspraak (bv. reistijd + buffer),
            // dus het trigger-moment is exact event - X min. Alleen ná dat moment (tot
            // TRIGGER_PAST_GRACE_MINUTES later) vuurt dit af - NOOIT ervoor: WeatherEventAlertScheduler
            // plant een exact AlarmManager-alarm precies op dat moment, dus er is geen "vroeg
            // vuren"-tolerantie meer nodig (die gaf anders tot 20 min te vroege meldingen zodra de
            // 15-min-poll een afspraak toevallig al binnen bereik zag komen).
            val triggerAt = event.epochMillis - rainMinutesBefore * 60_000L
            val minutesUntilTrigger = (triggerAt - now) / 60_000L
            Log.d(
                TAG,
                "collectBadWeatherFires: event='${event.label}' minutesUntilTrigger=$minutesUntilTrigger " +
                    "venster=[-$TRIGGER_PAST_GRACE_MINUTES, 0] rainEnabled=$rainEnabled rainThreshold=$rainThreshold"
            )
            if (minutesUntilTrigger !in -TRIGGER_PAST_GRACE_MINUTES..0) continue
            if (hasFiredToday("badweather_beforeevent_${event.id}_${event.epochMillis}", today)) {
                Log.d(TAG, "collectBadWeatherFires: event='${event.label}' al afgegaan vandaag, overslaan")
                continue
            }

            val labels: List<WeatherReason>
            val rainProbability: Int?
            if (wholeDay) {
                val eventDate = Instant.ofEpochMilli(event.epochMillis).atZone(zone).toLocalDate()
                val dayStart = eventDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = eventDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val scan = scanBadWeatherWindow(forecast, dayStart, dayEnd)
                labels = activeWeatherAlertsForScan(context, scan, rainEnabled, rainThreshold, forecast)
                rainProbability = rainAlertProbability(scan, rainEnabled, rainThreshold)
            } else if (rangeMinutes > 1) {
                val rangeMs = rangeMinutes * 60_000L
                val scan = scanBadWeatherWindow(forecast, event.epochMillis - rangeMs, event.epochMillis + rangeMs)
                labels = activeWeatherAlertsForScan(context, scan, rainEnabled, rainThreshold, forecast)
                rainProbability = rainAlertProbability(scan, rainEnabled, rainThreshold)
                Log.d(TAG, "collectBadWeatherFires: event='${event.label}' bereik=±${rangeMinutes}min labels=$labels rainProbability=$rainProbability")
            } else {
                // Het uurblok waarin de afspraak valt, niet het dichtstbijzijnde uurstempel: neerslag
                // en neerslagkans beschrijven bij Open-Meteo het uur vóór het stempel, dus voor een
                // afspraak om 14:20 keek dit voorheen naar de regen tussen 13:00 en 14:00. Zie
                // WeatherRepository.getPrecipitationHourForTime.
                val eventWeather = repo.getPrecipitationHourForTime(lat, lon, event.epochMillis, model).getOrNull()
                if (eventWeather == null) {
                    Log.w(TAG, "collectBadWeatherFires: event='${event.label}' geen weerdata voor dat tijdstip, overslaan")
                    continue
                }
                // Ook bij "kijk puur naar het exacte tijdstip" hoort een tijdvak in de melding: dat
                // ene uur zegt niets over hoe lang de bui aanhoudt, dus wordt die vanaf daar
                // doorgemeten. Alleen aan de regen-redenen hangen - onweer of hagel in dezelfde
                // melding hebben hun eigen tijd.
                val rainPeriod = rainPeriodAround(forecast, eventWeather.timestamp, rainThreshold)
                labels = activeWeatherAlerts(
                    context, eventWeather.weatherCode, eventWeather.condition, eventWeather.temperature,
                    eventWeather.precipitationProbability, eventWeather.precipitation, eventWeather.windGusts,
                    rainEnabled, rainThreshold
                ).map { alert ->
                    if (alert.reason.type.isRainLike) alert.reason.withPeriod(rainPeriod) else alert.reason
                }
                rainProbability = rainAlertProbabilityForHour(
                    eventWeather.condition, eventWeather.precipitationProbability, eventWeather.precipitation,
                    rainEnabled, rainThreshold
                )
                Log.d(
                    TAG,
                    "collectBadWeatherFires: event='${event.label}' weerCode=${eventWeather.weatherCode} " +
                        "condition=${eventWeather.condition} precipProb=${eventWeather.precipitationProbability} " +
                        "precip=${eventWeather.precipitation} labels=$labels rainProbability=$rainProbability"
                )
            }
            if (labels.isEmpty()) {
                Log.d(TAG, "collectBadWeatherFires: event='${event.label}' geen labels, geen melding")
                continue
            }
            result.add(BadWeatherFire(event, labels, rainProbability))
            fired++
        }
        return result
    }

    /**
     * Temperatuurwissel — "Melding voor agenda item" (vroeger "Tijdens eerste afspraak"), nu voor
     * meerdere agenda-items tegelijk instelbaar via dezelfde "aantal meldingen per dag"-limiet.
     *
     * "Hele dag"-instelling uit (standaard): vergelijkt puur het exacte tijdstip van het
     * agenda-item met 24u later. Aan: vergelijkt de max. temperatuur van de hele kalenderdag van
     * het agenda-item met de dag erna (zelfde vergelijking als Dag van tevoren/Dag zelf).
     */
    private suspend fun collectTempChangeFires(
        context: Context,
        repo: WeatherRepository,
        forecast: WeatherForecast,
        lat: Double,
        lon: Double,
        model: String,
        now: Long,
        today: LocalDate,
        events: List<AlarmItem>
    ): List<TempChangeFire> {
        if (events.isEmpty()) return emptyList()
        val threshold = SettingsManager.getWeatherTempChangeThreshold(context)
        val minutesBefore = SettingsManager.getWeatherTempChangeAlertFirstEventMinutesBefore(context)
        // TEST: "Geen maximum"-toggle negeert de dag-limiet tijdelijk, zodat tijdens testen niet
        // onopgemerkt tegen de cap aan gelopen wordt.
        val maxPerDay = if (SettingsManager.getWeatherTempChangeAlertFirstEventNoMax(context)) {
            Int.MAX_VALUE
        } else {
            SettingsManager.getWeatherTempChangeAlertFirstEventMaxPerDay(context)
        }
        val wholeDay = SettingsManager.getWeatherTempChangeAlertFirstEventWholeDay(context)
        val zone = ZoneId.systemDefault()

        var fired = countFiredToday(TEMPCHANGE_COUNT_PREFIX, today)
        val result = mutableListOf<TempChangeFire>()

        for (event in events) {
            if (fired >= maxPerDay) {
                Log.d(TAG, "collectTempChangeFires: maxPerDay ($maxPerDay) al bereikt, stop")
                break
            }
            val triggerAt = event.epochMillis - minutesBefore * 60_000L
            val minutesUntilTrigger = (triggerAt - now) / 60_000L
            Log.d(
                TAG,
                "collectTempChangeFires: event='${event.label}' minutesUntilTrigger=$minutesUntilTrigger " +
                    "venster=[-$TRIGGER_PAST_GRACE_MINUTES, 0] threshold=$threshold"
            )
            if (minutesUntilTrigger !in -TRIGGER_PAST_GRACE_MINUTES..0) continue
            if (hasFiredToday("tempchange_firstevent_${event.id}_${event.epochMillis}", today)) {
                Log.d(TAG, "collectTempChangeFires: event='${event.label}' al afgegaan vandaag, overslaan")
                continue
            }

            val (diff, maxTemp) = if (wholeDay) {
                val eventDate = Instant.ofEpochMilli(event.epochMillis).atZone(zone).toLocalDate()
                val maxToday = computeMaxTempForDate(forecast, eventDate)
                val maxTomorrow = computeMaxTempForDate(forecast, eventDate.plusDays(1))
                if (maxToday == null || maxTomorrow == null) continue
                (maxTomorrow - maxToday) to maxTomorrow
            } else {
                val tomorrowMillis = event.epochMillis + 24 * 60 * 60 * 1000L
                val d = repo.getTemperatureChange(lat, lon, event.epochMillis, tomorrowMillis, model).getOrNull() ?: continue
                val temp = repo.getWeatherForTime(lat, lon, tomorrowMillis, model).getOrNull()?.temperature ?: continue
                d to temp
            }
            Log.d(TAG, "collectTempChangeFires: event='${event.label}' diff=$diff maxTemp=$maxTemp threshold=$threshold")
            if (kotlin.math.abs(diff) < threshold) {
                Log.d(TAG, "collectTempChangeFires: event='${event.label}' diff onder drempel, geen melding")
                continue
            }
            result.add(TempChangeFire(event, diff, threshold, maxTemp))
            fired++
        }
        return result
    }
}
