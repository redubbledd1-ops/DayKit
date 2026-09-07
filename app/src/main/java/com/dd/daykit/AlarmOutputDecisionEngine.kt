package com.dd.daykit

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.dd.daykit.data.ExternalSpeakerMode
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettings
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.SpeakerContext
import com.dd.daykit.data.speakerFor
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * Engine voor het bepalen waar een alarm moet worden afgespeeld
 */
object AlarmOutputDecisionEngine {
    
    private const val TAG = "AlarmOutputDecision"
    
    /**
     * Bepaalt waar het alarm moet worden afgespeeld
     * @param context Android context
     * @param nextAlarmTimeMillis Tijd van het volgende alarm in milliseconden
     * @param speakerContext welk onderdeel dit alarm is (ALARM/TIMER/WEATHER) - bepaalt welke
     *   speaker/modus-instelling gebruikt wordt, sinds de speaker-splitsing (zie SpeakerContext).
     * @return AlarmOutput met de beslissing waar het alarm moet spelen
     */
    suspend fun determineAlarmOutput(
        context: Context,
        repository: HomeAssistantRepository,
        nextAlarmTimeMillis: Long,
        speakerContext: SpeakerContext
    ): AlarmOutput {
        try {
            val settingsStorage = HomeAssistantSettingsStorage(context)
            val settings = settingsStorage.settingsFlow.firstOrNull() ?: return AlarmOutput.PhoneOnly
            val speaker = settings.speakerFor(speakerContext)

            // Als externe speaker niet geconfigureerd is, gebruik telefoon
            if (speaker.entityId.isNullOrBlank() ||
                speaker.mode == ExternalSpeakerMode.DISABLED) {
                Log.d(TAG, "Externe speaker niet geconfigureerd of uitgeschakeld")
                return AlarmOutput.PhoneOnly
            }

            val speakerEntityId = speaker.entityId

            when (speaker.mode) {
                ExternalSpeakerMode.DEFAULT -> {
                    // Standaard speaker: gebruik als gebruiker thuis is
                    val userIsHome = isUserAtHome(repository, settings)
                    
                    // Sync thuisstatus naar Home Assistant
                    syncUserHomeStatus(repository, userIsHome)
                    
                    return if (userIsHome) {
                        Log.d(TAG, "Gebruiker is thuis, gebruik externe speaker als standaard")
                        AlarmOutput.ExternalDefault(speakerEntityId)
                    } else {
                        Log.d(TAG, "Gebruiker is niet thuis, gebruik telefoon")
                        AlarmOutput.PhoneOnly
                    }
                }
                
                ExternalSpeakerMode.BACKUP_ONLY -> {
                    // Backup speaker: alleen gebruiken als batterij te laag is EN gebruiker is thuis
                    val willSurvive = willBatterySurviveNextAlarm(context, nextAlarmTimeMillis)
                    val userIsHome = isUserAtHome(repository, settings)
                    
                    // Sync thuisstatus naar Home Assistant
                    syncUserHomeStatus(repository, userIsHome)
                    
                    return if (!willSurvive && userIsHome) {
                        // HA speaker fallback ALLEEN als mobiel het niet redt EN gebruiker thuis is
                        Log.d(TAG, "Batterij te laag EN gebruiker thuis, gebruik externe speaker als backup")
                        AlarmOutput.ExternalBackup(speakerEntityId)
                    } else if (!willSurvive && !userIsHome) {
                        // Gebruiker niet thuis: NOOIT HA speaker gebruiken, ook niet als backup
                        Log.d(TAG, "Batterij te laag MAAR gebruiker niet thuis, geen HA speaker (alleen telefoon)")
                        AlarmOutput.PhoneOnly
                    } else {
                        Log.d(TAG, "Batterij voldoende, gebruik telefoon")
                        AlarmOutput.PhoneOnly
                    }
                }
                
                ExternalSpeakerMode.BOTH -> {
                    // Beide speakers: alarm op externe speaker én mobiel
                    val userIsHome = isUserAtHome(repository, settings)
                    
                    // Sync thuisstatus naar Home Assistant
                    syncUserHomeStatus(repository, userIsHome)
                    
                    return if (userIsHome) {
                        Log.d(TAG, "Gebruiker is thuis, gebruik beide speakers")
                        AlarmOutput.PhoneAndExternal(speakerEntityId)
                    } else {
                        // Gebruiker niet thuis: NOOIT HA speaker gebruiken
                        Log.d(TAG, "Gebruiker is niet thuis, gebruik alleen telefoon (geen HA speaker)")
                        AlarmOutput.PhoneOnly
                    }
                }
                
                ExternalSpeakerMode.DISABLED -> {
                    return AlarmOutput.PhoneOnly
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij bepalen alarm output", e)
            return AlarmOutput.PhoneOnly
        }
    }
    
    /**
     * Sync de thuisstatus naar Home Assistant input_boolean.gebruiker_thuis.
     * Best-effort met korte timeout zodat het nooit het alarm blokkeert als de
     * entity niet bestaat of HA traag reageert.
     */
    private suspend fun syncUserHomeStatus(repository: HomeAssistantRepository, isHome: Boolean) {
        try {
            withTimeoutOrNull(1500) {
                repository.setUserHomeStatus(isHome)
                Log.d(TAG, "Thuisstatus gesynchroniseerd naar HA: $isHome")
            } ?: Log.w(TAG, "Timeout (1.5s) bij syncUserHomeStatus - overgeslagen")
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij synchroniseren thuisstatus naar HA", e)
        }
    }
    
    /**
     * Controleert of de gebruiker thuis is op basis van de presence entity.
     * Internal ipv private: hergebruikt door AlarmService/TimeEngineService voor de
     * script-presence-gate, i.p.v. deze fail-open logica te dupliceren.
     *
     * Bevat één automatische retry-met-forced-refresh als de EERSTE check een bevestigde
     * (dus geen timeout/exception - die blijven zoals altijd fail-open naar true) "niet thuis"
     * oplevert: dat kan een stale lezing zijn (bv. de ingebouwde ping-sensor die nog niet
     * opnieuw gepingd heeft na een HA-reload, of één gemiste ping), niet per se een echte
     * afwezigheid. Zie [HomeAssistantRepository.forceUpdateEntity].
     */
    internal suspend fun isUserAtHome(
        repository: HomeAssistantRepository,
        settings: HomeAssistantSettings
    ): Boolean {
        val presenceEntityId = settings.presenceEntityId
        if (presenceEntityId.isNullOrBlank()) {
            Log.d(TAG, "Geen presence entity geconfigureerd, neem aan dat gebruiker thuis is")
            return true // Default: als niet geconfigureerd, aannemen dat gebruiker thuis is
        }

        val firstCheck = checkPresenceOnce(repository, presenceEntityId)
        if (firstCheck != false) {
            // true (echt thuis) of null (timeout/exception, al fail-open) -> geen retry nodig.
            return firstCheck ?: true
        }

        // Eerste check zegt bevestigd "niet thuis" - probeer 1x een auto-fix: forceer een
        // verse update van de entity (bij de ingebouwde ping-sensor: een directe herping i.p.v.
        // wachten op de volgende PING_INTERVAL_SECONDS-tick) en check daarna nog één keer,
        // vóórdat we deze "niet thuis"-beslissing definitief maken en het alarm alleen op de
        // telefoon laten spelen.
        Log.w(TAG, "Presence check zegt 'niet thuis' - probeer 1x auto-fix (forced refresh) en check opnieuw")
        try {
            withTimeoutOrNull(2000) {
                val result = repository.forceUpdateEntity(presenceEntityId)
                if (result is com.dd.daykit.data.HaUpdateResult.Error) {
                    Log.w(TAG, "Auto-fix forceUpdateEntity mislukt: ${result.message}")
                }
            } ?: Log.w(TAG, "Auto-fix forceUpdateEntity timeout (2s)")
        } catch (e: Exception) {
            Log.w(TAG, "Auto-fix forceUpdateEntity exception", e)
        }
        // Korte marge zodat de (ge)forceerde update (bv. de ping, max PING_TIMEOUT_SECONDS aan
        // HA-kant) server-side kans krijgt af te ronden vóór de herhaalde state-check.
        kotlinx.coroutines.delay(800)

        val secondCheck = checkPresenceOnce(repository, presenceEntityId)
        val finalIsHome = secondCheck ?: true // timeout/exception op de retry: fail-open naar true
        if (secondCheck == true) {
            Log.i(TAG, "Auto-fix geslaagd: presence check is na de retry alsnog 'thuis'")
        } else if (secondCheck == false) {
            Log.w(TAG, "Auto-fix hielp niet: presence blijft 'niet thuis' na retry, alarm gaat alleen op telefoon")
        }
        return finalIsHome
    }

    /**
     * Eén losse presence-check, zonder retry-logica. Retourneert null bij timeout/exception
     * (onbeslist - de aanroeper bepaalt dan zelf de fail-open/retry-strategie), anders het
     * daadwerkelijke thuis/niet-thuis-resultaat.
     */
    private suspend fun checkPresenceOnce(
        repository: HomeAssistantRepository,
        presenceEntityId: String
    ): Boolean? {
        return try {
            val entityState = withTimeoutOrNull(2000) {
                repository.getEntityState(presenceEntityId)
            }

            if (entityState == null) {
                Log.w(TAG, "Timeout (2s) bij ophalen presence state")
                return null
            }

            val expectedState = com.dd.daykit.data.deriveExpectedPresenceState(presenceEntityId)
            val isHome = entityState.state.equals(expectedState, ignoreCase = true)
            Log.d(TAG, "Presence check: ${entityState.state} == $expectedState -> $isHome")
            isHome
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij ophalen presence state", e)
            null
        }
    }
    
    /**
     * Leest het huidige batterijpercentage uit via ACTION_BATTERY_CHANGED.
     * Retourneert null als de waarde niet bepaald kan worden (geen intent, ongeldige level/scale).
     * Wordt gebruikt om het batterijniveau mee te sturen naar de HA-watchdog.
     */
    internal fun getBatteryPercent(context: Context): Int? {
        return try {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (batteryStatus == null) return null
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) (level.toFloat() / scale.toFloat() * 100).toInt() else null
        } catch (e: Exception) {
            Log.w(TAG, "Kon batterijpercentage niet uitlezen", e)
            null
        }
    }

    /**
     * Bepaalt het huidige lokale IP-adres van de telefoon op het actieve netwerk, voor de
     * ingebouwde ping-detectie aan de HA-kant (binary_sensor.py) - zo hoeft de gebruiker geen
     * IP-adres handmatig in te vullen. Gebruikt bewust ConnectivityManager.getLinkProperties
     * i.p.v. WifiManager.getConnectionInfo (SSID/BSSID): dat laatste vereist sinds Android 8
     * ACCESS_FINE_LOCATION, het eigen toegewezen IP-adres opvragen niet.
     * Retourneert null (best-effort, mag arm/report-alive nooit laten falen) als er geen
     * bruikbaar IPv4-adres gevonden wordt.
     */
    internal fun getPhoneIpAddress(context: Context): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return null
            val network = cm.activeNetwork ?: return null
            val linkProperties = cm.getLinkProperties(network) ?: return null
            linkProperties.linkAddresses
                .map { it.address }
                .firstOrNull { addr ->
                    addr is java.net.Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress
                }
                ?.hostAddress
        } catch (e: Exception) {
            Log.w(TAG, "Kon telefoon-IP niet bepalen", e)
            null
        }
    }

    /**
     * Controleert of de batterij het volgende alarm zal halen.
     * Internal ipv private: AlarmService gebruikt dit ook om te bepalen of de telefoon
     * "betrouwbaar" is voor de HA-watchdog (batterij als extra trigger naast dropout-detectie).
     */
    internal fun willBatterySurviveNextAlarm(context: Context, nextAlarmTimeMillis: Long): Boolean {
        try {
            val batteryStatus: Intent? = context.registerReceiver(
                null, 
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            
            if (batteryStatus == null) {
                Log.w(TAG, "Kan batterij status niet ophalen")
                return true // Bij twijfel, aannemen dat het wel lukt
            }
            
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPercent = if (level >= 0 && scale > 0) {
                (level.toFloat() / scale.toFloat() * 100).toInt()
            } else {
                100 // Bij twijfel, aannemen dat het vol is
            }
            
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            
            // Als aan het opladen, altijd OK
            if (isCharging) {
                Log.d(TAG, "Telefoon is aan het opladen, batterij is OK")
                return true
            }
            
            val currentTimeMillis = System.currentTimeMillis()
            val timeUntilAlarmMinutes = max(0, (nextAlarmTimeMillis - currentTimeMillis) / 60000)

            // Gebruik het door de gebruiker ingestelde ontladingspercentage per uur
            // (Instellingen > Externe speaker > batterijverbruik), i.p.v. een vaste aanname.
            val dischargePerHour = SettingsManager.getBatteryUsagePerHour(context)
            val estimatedDischargePercent = (timeUntilAlarmMinutes / 60.0 * dischargePerHour).toInt()
            val estimatedBatteryAtAlarm = batteryPercent - estimatedDischargePercent
            
            // Veiligheidsmarge: als geschatte batterij onder 15% komt, gebruik backup
            val willSurvive = estimatedBatteryAtAlarm >= 15
            
            Log.d(TAG, "Batterij check: huidige=$batteryPercent%, tijd tot alarm=$timeUntilAlarmMinutes min, " +
                      "geschat bij alarm=$estimatedBatteryAtAlarm%, zal overleven=$willSurvive")
            
            return willSurvive
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij batterij check", e)
            return true // Bij fout, veilig aannemen dat het wel lukt
        }
    }
}

/**
 * Sealed class die aangeeft waar het alarm moet worden afgespeeld
 */
sealed class AlarmOutput {
    /** Alleen op de telefoon afspelen */
    data object PhoneOnly : AlarmOutput()
    
    /** Op externe speaker als standaard (gebruiker is thuis) */
    data class ExternalDefault(val speakerEntityId: String) : AlarmOutput()
    
    /** Op externe speaker als backup (batterij te laag) */
    data class ExternalBackup(val speakerEntityId: String) : AlarmOutput()
    
    /** Beide: telefoon EN externe speaker (optioneel voor toekomstige uitbreiding) */
    data class PhoneAndExternal(val speakerEntityId: String) : AlarmOutput()
}
