"""Sensor entities voor AgendaAlarm Backup (alleen-lezen zichtbaarheid van huidige fallback-config).

OutOfBedSensor/HomeLiveSensor woonden voorheen in binary_sensor.py (BinarySensorEntity,
"Aan"/"Uit"-weergave) - verplaatst naar hier als gewone tekst-sensoren ("True"/"False",
gevraagd i.p.v. HA's standaard Aan/Uit) zodat ze in dezelfde structuur staan als de rest.
De unique_id-sleutels ("uit_bed"/"aanwezigheid_live") zijn bewust ongewijzigd, maar het
domein wisselt (binary_sensor.* -> sensor.*) - HA's entity-registry sleutelt op
(domein, platform, unique_id) samen, dus een domeinwissel laat de oude binary_sensor.*
entiteit als verweesd/unavailable achter tenzij expliciet opgeruimd. Zie
_async_remove_orphaned_binary_sensor hieronder (zelfde patroon als switch.py's
_async_remove_orphaned_gebruiker_thuis_switch).
"""
from __future__ import annotations

import json

from homeassistant.components.sensor import SensorDeviceClass, SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import EntityCategory
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.event import async_track_state_change_event
from homeassistant.helpers.network import NoURLAvailableError, get_url

from .const import DOMAIN, ENTITY_KEY_PAIRING_CODE_SENSOR, QR_IMAGE_URL
from .entity import AgendaAlarmBackupEntity
from .pairing import PAIRING_CODE_TTL_SECONDS, get_pairing_store


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    hub = hass.data[DOMAIN][entry.entry_id]

    # Opruimen van de oude binary_sensor.*-versies van deze twee entiteiten (zie
    # moduledocstring) - stille no-op als ze nooit als binary_sensor hebben bestaan
    # (bv. gloednieuwe installatie).
    _async_remove_orphaned_binary_sensor(hass, entry, "uit_bed")
    _async_remove_orphaned_binary_sensor(hass, entry, "aanwezigheid_live")

    # Opruimen van een eerder geregistreerde PairedSensor - hoorde bij de inmiddels
    # volledig verwijderde is_paired-tracking (die functioneel toch niks gate'te - HA's
    # eigen requires_auth deed het echte werk), zou anders als "unavailable, not
    # provided by integration" blijven hangen.
    _async_remove_orphaned_paired_sensor(hass, entry)

    async_add_entities([
        SpeakerEntitySensor(hub, entry),
        SoundUrlSensor(hub, entry),
        PairingCodeSensor(hub, entry),
        VolgendAlarmSensor(hub, entry),
        LastEventSensor(hub, entry),
        WatchdogStatusSensor(hub, entry),
        OutOfBedSensor(hub, entry),
        HomeLiveSensor(hub, entry),
    ])


def _async_remove_orphaned_binary_sensor(hass: HomeAssistant, entry: ConfigEntry, unique_id: str) -> None:
    """Verwijdert een eerder geregistreerde binary_sensor.*-entiteit met deze unique_id uit
    het entity-register, zodat 'm niet als verweesde/unavailable entiteit achterblijft na de
    domeinwissel naar sensor.*. Stille no-op als 'm nooit als binary_sensor heeft bestaan."""
    entity_registry = er.async_get(hass)
    existing_entity_id = entity_registry.async_get_entity_id("binary_sensor", DOMAIN, unique_id)
    if existing_entity_id:
        entity_registry.async_remove(existing_entity_id)


def _async_remove_orphaned_paired_sensor(hass: HomeAssistant, entry: ConfigEntry) -> None:
    entity_registry = er.async_get(hass)
    unique_id = f"{entry.entry_id}_paired"
    existing_entity_id = entity_registry.async_get_entity_id("sensor", DOMAIN, unique_id)
    if existing_entity_id:
        entity_registry.async_remove(existing_entity_id)


class SpeakerEntitySensor(AgendaAlarmBackupEntity, SensorEntity):
    """Toont welke speaker momenteel als fallback is ingesteld (door de app of de setup-wizard)."""

    _attr_icon = "mdi:speaker"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "speaker_entity", "Fallback speaker")

    @property
    def native_value(self) -> str:
        return self.hub.speaker_entity_id or "none"


class SoundUrlSensor(AgendaAlarmBackupEntity, SensorEntity):
    """Toont welk geluid momenteel als fallback is ingesteld."""

    _attr_icon = "mdi:music-note"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "sound_url", "Fallback sound")

    @property
    def native_value(self) -> str:
        url = self.hub.sound_url or "none"
        # Sensor-state heeft een max lengte (255) - toon iets leesbaars i.p.v. de volledige URL.
        return url if len(url) <= 100 else url[:97] + "..."

    @property
    def extra_state_attributes(self) -> dict:
        return {"url": self.hub.sound_url}


class PairingCodeSensor(AgendaAlarmBackupEntity, SensorEntity):
    """Toont de huidige koppelcode (gegenereerd via de 'Generate pairing code'-knop).

    De state is de 6-cijferige code zelf (handmatig over te typen in de app als
    scannen niet lukt). Het attribuut `qr_payload` bevat de JSON die als QR-code
    gescand moet worden {"base_url": ..., "code": ...} - bewust GEEN token erin,
    zie pairing.py voor de uitleg van dit beveiligingsmodel. `qr_image_url` wijst
    naar een door deze integratie gegenereerde QR-afbeelding (zelfde code) die je
    op een dashboard kan zetten (bijv. met een Picture-kaart).

    Altijd aanwezig, ook als er al gekoppeld is (voor het koppelen van een 2e
    toestel via de altijd-aanwezige "Generate pairing code"-knop, zie button.py).
    native_value toont "No active code" i.p.v. een oude/verlopen code zodra er
    niks actiefs is."""

    _attr_icon = "mdi:qrcode"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, ENTITY_KEY_PAIRING_CODE_SENSOR, "Pairing code")

    @property
    def native_value(self) -> str:
        return self.hub.pairing_code or "No active code"

    @property
    def extra_state_attributes(self) -> dict:
        code = self.hub.pairing_code
        if not code:
            return {"active": False}

        store = get_pairing_store(self.hass)
        seconds_remaining = store.seconds_remaining(code)
        if seconds_remaining <= 0:
            return {"active": False}

        try:
            base_url = get_url(self.hass, prefer_external=False, allow_internal=True)
        except NoURLAvailableError:
            base_url = None

        attrs: dict = {
            "active": True,
            "expires_in_seconds": seconds_remaining,
            "validity_seconds": PAIRING_CODE_TTL_SECONDS,
        }
        if base_url:
            attrs["qr_payload"] = json.dumps({"base_url": base_url, "code": code})
            attrs["qr_image_url"] = f"{base_url}{QR_IMAGE_URL}"
        return attrs


class VolgendAlarmSensor(AgendaAlarmBackupEntity, SensorEntity):
    """Toont het eerstvolgende geplande alarm-tijdstip, zoals meegegeven door de app via
    POST /api/agendaalarm_backup/event (action=arm) -> AlarmBackupHub.async_arm()."""

    _attr_icon = "mdi:alarm"
    _attr_device_class = SensorDeviceClass.TIMESTAMP

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "next_alarm", "1. Next alarm")

    @property
    def native_value(self):
        return self.hub.next_alarm_fire_at


class LastEventSensor(AgendaAlarmBackupEntity, SensorEntity):
    """Diagnose: toont wanneer/wat de laatste binnenkomende, geauthenticeerde aanroep op
    POST /api/agendaalarm_backup/event was - ongeacht of die actie slaagde (zie
    AlarmBackupHub.record_incoming_event in __init__.py). Eerste plek om te checken of
    de app HA daadwerkelijk bereikt, zonder verder te hoeven gokken."""

    _attr_icon = "mdi:web-sync"
    _attr_device_class = SensorDeviceClass.TIMESTAMP
    _attr_entity_category = EntityCategory.DIAGNOSTIC

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "last_event", "Last event received")

    @property
    def native_value(self):
        return self.hub.last_event_received_at

    @property
    def extra_state_attributes(self) -> dict:
        return {"payload": self.hub.last_event_payload}


class WatchdogStatusSensor(AgendaAlarmBackupEntity, SensorEntity):
    """Diagnostische samenvatting van de hub-status - geen nieuwe logica, alleen bestaande
    hub-state gebundeld zichtbaar zodat in één oogopslag te zien is waarom een fallback
    wel/niet aansloeg (bv. direct zichtbaar als speaker_configured: false de reden was).
    """

    _attr_icon = "mdi:shield-alert-outline"
    _attr_entity_category = EntityCategory.DIAGNOSTIC

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "watchdog_status", "Watchdog status")

    @property
    def native_value(self) -> str:
        hub = self.hub
        if hub.fallback_actief:
            return "Phone unreliable - fallback started"
        if hub.alarm_actief:
            if hub.mobiel_betrouwbaar:
                return "Phone reliable"
            # Onbetrouwbaar gemeld maar de fallback is toch niet gestart - meestal omdat
            # async_start_fallback() vroegtijdig stopte (zie extra_state_attributes
            # hieronder, bv. speaker_configured: false) of omdat de gebruiker niet
            # thuis is (zie user_home).
            return "Phone unreliable - fallback not started"
        if hub._watchdog_unsub is not None:
            return "Armed, waiting for alarm time"
        return "Not armed"

    @property
    def extra_state_attributes(self) -> dict:
        hub = self.hub
        return {
            "phone_reliable": hub.mobiel_betrouwbaar,
            "user_home": hub.is_user_home(),
            "speaker_configured": bool(hub.speaker_entity_id),
            "sound_configured": bool(hub.sound_url),
            "last_played": hub._last_played,
            # Nieuwe beslisvolgorde (batterij -> aanwezigheid -> uit bed, zie
            # AlarmBackupHub._async_watchdog_check) - hiermee is achteraf precies te zien
            # welke van de stappen de doorslag gaf.
            "battery_percentage": hub.last_known_battery_percent,
            "battery_last_reported": hub.last_battery_report_at,
            "battery_likely_dead": hub.is_battery_likely_dead(),
            "out_of_bed": hub.is_out_of_bed(),
        }


class OutOfBedSensor(AgendaAlarmBackupEntity, SensorEntity):
    """Read-only spiegel van de uit-bed-config: "True" zodra de live state van de gekozen
    entiteit afwijkt van out_of_bed_expected_value. Maakt de check alleen zichtbaar in HA -
    de daadwerkelijke beslissing (telt dit mee vóór een alarm) zit al in de app
    (RuleEngine.kt/PreAlarmCheckResult.kt) en blijft ongewijzigd.

    Gewone tekst-sensor (native_value "True"/"False") i.p.v. binary_sensor: HA rendert een
    binary_sensor altijd als Aan/Uit ongeacht device_class, en hier is letterlijk True/False
    gewenst."""

    _attr_icon = "mdi:bed-empty"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "uit_bed", "3. Out of bed")

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        if self.hub.out_of_bed_entity_id:
            self.async_on_remove(
                async_track_state_change_event(
                    self.hass, [self.hub.out_of_bed_entity_id], self._handle_source_update
                )
            )

    @callback
    def _handle_source_update(self, event) -> None:
        self.async_write_ha_state()

    @property
    def available(self) -> bool:
        if not self.hub.out_of_bed_entity_id:
            return False
        return self.hass.states.get(self.hub.out_of_bed_entity_id) is not None

    @property
    def native_value(self) -> str | None:
        if not self.available:
            return None
        state = self.hass.states.get(self.hub.out_of_bed_entity_id)
        return "True" if state.state != self.hub.out_of_bed_expected_value else "False"


class HomeLiveSensor(AgendaAlarmBackupEntity, SensorEntity):
    """Altijd-aanwezige, altijd-actuele weergave van hub.is_user_home() - ongeacht welk
    onderliggend mechanisme actief is (gekozen presence-entity, of anders de interne
    fallback-switch). Bewust een andere naam dan de "User home"-switch (switch.py), die
    alleen als handmatige schakelaar bestaat zolang er geen presence-entity gekozen is.

    Gewone tekst-sensor (native_value "True"/"False"), zelfde reden als OutOfBedSensor
    hierboven."""

    _attr_icon = "mdi:home-account"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "aanwezigheid_live", "2. Home (live)")

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        if self.hub.presence_entity_id:
            self.async_on_remove(
                async_track_state_change_event(
                    self.hass, [self.hub.presence_entity_id], self._handle_source_update
                )
            )

    @callback
    def _handle_source_update(self, event) -> None:
        self.async_write_ha_state()

    @property
    def native_value(self) -> str:
        return "True" if self.hub.is_user_home() else "False"
