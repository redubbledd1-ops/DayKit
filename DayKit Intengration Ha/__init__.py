"""AgendaAlarm Backup - native dead-man's-switch backup alarm integration.

Vervangt het handmatig opgezette geheel van input_helpers + YAML-automations +
losse AppDaemon-app door één integratie:
- Maakt zelf de benodigde entities aan (switch/number/sensor), geen handmatige
  helpers meer nodig.
- De horloge-logica (Supervisor/Fallback start-stop-loop/veiligheidsklep) draait
  native in Python via async_track_point_in_time / async_call_later i.p.v. een
  input_datetime + time-trigger + losse "laatste minuut check"-automation (die
  in de handmatige opzet gevoelig bleek voor ontbrekende helpers en tijd-precisie).
- Aanwezigheid wordt, als je een person./device_tracker.-entity kiest in de
  setup, live uitgelezen i.p.v. via een los input_boolean dat de telefoon moet
  bijhouden - dat voorkomt de "stale gebruiker_thuis"-bug uit de handmatige opzet.
- Eigen HTTP-endpoints vervangen zowel de alarm_app_v1-webhook als de losse
  AppDaemon-geluid-upload-app - geen AppDaemon-add-on meer nodig.
"""
from __future__ import annotations

import logging
import shutil
from datetime import datetime, timedelta
from pathlib import Path

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import (
    async_call_later,
    async_track_point_in_time,
    async_track_time_interval,
)
import homeassistant.util.dt as dt_util

from .const import (
    CONF_DEFAULT_INTERVAL,
    CONF_DEFAULT_SOUND_URL,
    CONF_DEFAULT_VOLUME,
    CONF_NOTIFY_SERVICE,
    CONF_OUT_OF_BED_ENTITY,
    CONF_OUT_OF_BED_EXPECTED_VALUE,
    CONF_PRESENCE_ENTITY,
    CONF_PRESENCE_EXPECTED_STATE,
    CONF_SAFETY_TIMEOUT,
    CONF_SPEAKER_ENTITY,
    DEFAULT_OUT_OF_BED_EXPECTED_VALUE,
    DEFAULT_PRESENCE_EXPECTED_STATE,
    DEFAULT_SAFETY_TIMEOUT,
    DEFAULT_SOUND_FILENAME,
    DEFAULT_SOUND_PATH,
    DOMAIN,
    SIGNAL_UPDATE,
    SOUNDS_SUBDIR,
)
from .pairing import get_pairing_store

_LOGGER = logging.getLogger(__name__)

PLATFORMS = ["switch", "number", "sensor", "button", "camera", "binary_sensor"]


def _default_local_sound_url(hass: HomeAssistant) -> str:
    """Absolute URL naar het gebundelde fallback-geluid, zie _copy_default_sound_if_needed."""
    from homeassistant.helpers.network import get_url

    try:
        return f"{get_url(hass, prefer_external=False, allow_internal=True)}{DEFAULT_SOUND_PATH}"
    except Exception:  # noqa: BLE001 - geen URL beschikbaar (zeer ongebruikelijke HA-config)
        _LOGGER.warning("Kon geen lokale HA-URL bepalen voor het standaard fallback-geluid")
        return ""


def _copy_default_sound_if_needed(hass: HomeAssistant) -> None:
    """Kopieert het bij de integratie gebundelde discoAlarmBackupAlarm.mp3 (assets/) naar
    <config>/www/agendaalarm_sounds/ zodat het publiek bereikbaar is via /local/... - zelfde map
    die http_views.py's AgendaAlarmSoundUploadView voor door de app geuploade geluiden gebruikt.
    Idempotent (alleen kopiëren als bestand ontbreekt of van grootte verschilt) en synchroon
    bestandswerk, dus altijd via async_add_executor_job aanroepen (niet in de event loop)."""
    source = Path(__file__).parent / "assets" / DEFAULT_SOUND_FILENAME
    if not source.exists():
        _LOGGER.warning("Gebundeld fallback-geluid niet gevonden op %s", source)
        return

    dest_dir = Path(hass.config.path("www")) / SOUNDS_SUBDIR
    dest = dest_dir / DEFAULT_SOUND_FILENAME

    if dest.exists() and dest.stat().st_size == source.stat().st_size:
        return

    dest_dir.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)
    _LOGGER.info("Standaard fallback-geluid gekopieerd naar %s", dest)


class AlarmBackupHub:
    """Houdt de status bij en voert de dead-man's-switch-logica uit voor 1 config entry."""

    def __init__(self, hass: HomeAssistant, entry: ConfigEntry) -> None:
        self.hass = hass
        self.entry = entry

        opts = {**entry.data, **entry.options}
        self.presence_entity_id: str | None = opts.get(CONF_PRESENCE_ENTITY) or None
        self.presence_expected_state: str = opts.get(
            CONF_PRESENCE_EXPECTED_STATE, DEFAULT_PRESENCE_EXPECTED_STATE
        )
        self.notify_service: str | None = opts.get(CONF_NOTIFY_SERVICE) or None
        self.speaker_entity_id: str | None = opts.get(CONF_SPEAKER_ENTITY) or None
        # Alleen gebruikt als read-only statusspiegel (binary_sensor.py) - de daadwerkelijke
        # uit-bed-beslissing zit in de app (RuleEngine.kt/PreAlarmCheckResult.kt).
        self.out_of_bed_entity_id: str | None = opts.get(CONF_OUT_OF_BED_ENTITY) or None
        self.out_of_bed_expected_value: str = opts.get(
            CONF_OUT_OF_BED_EXPECTED_VALUE, DEFAULT_OUT_OF_BED_EXPECTED_VALUE
        )

        # Runtime state
        self.alarm_actief: bool = False
        self.fallback_actief: bool = False
        self.mobiel_betrouwbaar: bool = True
        # Alleen gebruikt als er geen presence_entity_id is gekozen in de setup.
        self.gebruiker_thuis_fallback: bool = True
        # Eerstvolgende geplande alarmtijd, zoals meegegeven door de app via
        # POST /api/agendaalarm_backup/event (action=arm) - alleen onthouden/tonen
        # (sensor.py's VolgendAlarmSensor), geen eigen logica op dit veld.
        self.next_alarm_fire_at: datetime | None = None

        # Diagnose: laatste binnenkomende, geauthenticeerde aanroep op
        # POST /api/agendaalarm_backup/event, ongeacht actie of of de verwerking slaagde
        # (zie record_incoming_event hieronder + sensor.py's LastEventSensor) - het eerste
        # waar je naar kijkt om te zien of de app HA daadwerkelijk bereikt.
        self.last_event_received_at: datetime | None = None
        self.last_event_payload: dict | None = None

        # Batterij-telemetrie: lift mee op elke arm-aanroep (geen apart periodiek mechanisme,
        # zie async_arm hieronder + is_battery_likely_dead()). Runtime-telemetrie, geen
        # config-instelling, dus geen eigen CONF_*-constante in const.py.
        self.last_known_battery_percent: int | None = None
        self.last_battery_report_at: datetime | None = None
        self.battery_usage_per_hour: int = 5

        # Laatst gerapporteerde lokale IP van de telefoon (liftte net als batterij mee op
        # arm/report_alive, zie async_arm/async_report_alive) - gebruikt door de ingebouwde
        # ping-detectie (binary_sensor.py) zodat er geen handmatig IP-adres ingesteld hoeft
        # te worden. Runtime-telemetrie, geen config-instelling.
        self.last_known_phone_ip: str | None = None

        self.volume: int = int(opts.get(CONF_DEFAULT_VOLUME, 70))
        self.fallback_interval: int = int(opts.get(CONF_DEFAULT_INTERVAL, 10))
        # `or` i.p.v. .get(..., default): een expliciet lege string in options (bv. via een
        # oudere config-sync patch) mag hier evenmin als een missende key tot "" leiden -
        # anders blijft de fallback-speaker stil zonder foutmelding (zie _play_sound).
        self.sound_url: str = opts.get(CONF_DEFAULT_SOUND_URL) or _default_local_sound_url(hass)
        # Veiligheidsklep: stopt de fallback sowieso na dit aantal seconden als niemand
        # 'm handmatig uitzet (zie async_start_fallback/_auto_stop hieronder).
        self.safety_timeout_seconds: int = int(opts.get(CONF_SAFETY_TIMEOUT, DEFAULT_SAFETY_TIMEOUT))

        self._watchdog_unsub = None
        self._loop_unsub = None
        self._timeout_unsub = None
        self._last_played: datetime | None = None

        # Setup-code / QR-koppeling (zie pairing.py) - alleen het huidige actieve
        # code-cijfer wordt hier bijgehouden voor weergave (sensor + QR-afbeelding);
        # de validatie/verbruik zelf loopt via de gedeelde PairingStore.
        self.pairing_code: str | None = None

    # ------------------------------------------------------------------ #
    # Setup-code / QR-koppeling
    # ------------------------------------------------------------------ #
    def generate_pairing_code(self) -> str:
        """Genereert een nieuwe kortdurende koppelcode en onthoudt 'm voor weergave
        (sensor.agendaalarm_koppelcode + QR-afbeelding). Overschrijft een eventuele
        vorige nog-niet-gebruikte code."""
        store = get_pairing_store(self.hass)
        self.pairing_code = store.generate_code()
        self._notify_update()
        return self.pairing_code

    def record_incoming_event(self, action: str, payload: dict) -> None:
        """Diagnose: onthoudt de laatste binnenkomende, geauthenticeerde aanroep op
        POST /api/agendaalarm_backup/event (sensor.py's LastEventSensor) - puur voor
        zichtbaarheid, geen eigen logica op deze velden. Aangeroepen door
        http_views.py's AgendaAlarmEventView.post ongeacht welke actie het is of of de
        verdere verwerking slaagt, zodat ook een falende aanroep hier zichtbaar wordt."""
        self.last_event_received_at = dt_util.utcnow()
        self.last_event_payload = {"action": action, **payload}
        self._notify_update()

    # ------------------------------------------------------------------ #
    # Aanwezigheid
    # ------------------------------------------------------------------ #
    def is_user_home(self) -> bool:
        """Live check: gebruikt de gekozen presence-entity indien geconfigureerd (vergeleken
        met het configureerbare presence_expected_state, niet meer een hardcoded
        "home"/"on"), anders de interne fallback-switch (die de app of gebruiker zelf
        bedient)."""
        if self.presence_entity_id:
            state = self.hass.states.get(self.presence_entity_id)
            if state is None:
                _LOGGER.warning(
                    "Presence entity %s niet gevonden, neem aan dat gebruiker thuis is",
                    self.presence_entity_id,
                )
                return True
            return state.state == self.presence_expected_state
        return self.gebruiker_thuis_fallback

    def is_out_of_bed(self) -> bool | None:
        """None = geen out-of-bed-entity geconfigureerd of state niet beschikbaar (onbekend,
        niet hetzelfde als "bevestigd uit bed" - zie _async_watchdog_check)."""
        if not self.out_of_bed_entity_id:
            return None
        state = self.hass.states.get(self.out_of_bed_entity_id)
        if state is None:
            return None
        return state.state != self.out_of_bed_expected_value

    def is_battery_likely_dead(self) -> bool:
        """Schat in of de telefoon inmiddels waarschijnlijk leeg/uitgevallen is, op basis van
        het laatst gerapporteerde percentage + ingesteld verbruik per uur + verstreken tijd
        sinds die laatste rapportage (zie async_arm). Geen data -> kan het niet aannemen,
        val terug op aanwezigheid/uit-bed-logica (fail-safe: nooit stilzwijgend negeren)."""
        if self.last_known_battery_percent is None or self.last_battery_report_at is None:
            return False
        hours_elapsed = (dt_util.utcnow() - self.last_battery_report_at).total_seconds() / 3600
        estimated = self.last_known_battery_percent - hours_elapsed * self.battery_usage_per_hour
        return estimated <= 0

    # ------------------------------------------------------------------ #
    # Wapenen (schedule time) / rapporteren (alarm fire time)
    # ------------------------------------------------------------------ #
    async def async_arm(
        self,
        fire_at: datetime,
        fallback: dict | None = None,
        battery_percent: int | None = None,
        battery_usage_per_hour: int | None = None,
        phone_ip: str | None = None,
    ) -> None:
        """Wordt aangeroepen als de telefoon een nieuw alarm plant. Reset de status
        naar 'onbetrouwbaar totdat het tegendeel bewezen is' en plant de watchdog
        op het exacte moment (alarmtijd + marge) i.p.v. te leunen op een
        input_datetime + time-trigger.

        [battery_percent]/[battery_usage_per_hour]/[phone_ip] liften mee op deze toch al
        regelmatige aanroep (elke scheduleNextAlarm-cyclus aan de app-kant, dus ook de
        periodieke 30-minuten-sync) - zie is_battery_likely_dead() / binary_sensor.py."""
        await self.async_stop(reason="rearm")

        self.next_alarm_fire_at = fire_at
        self.mobiel_betrouwbaar = False
        if fallback:
            self.speaker_entity_id = fallback.get("speaker") or self.speaker_entity_id
            if fallback.get("volume") is not None:
                self.volume = int(fallback["volume"])
            if fallback.get("sound_url"):
                self.sound_url = fallback["sound_url"]
            if fallback.get("interval") is not None:
                self.fallback_interval = int(fallback["interval"])
        if battery_percent is not None:
            self.last_known_battery_percent = int(battery_percent)
            self.last_battery_report_at = dt_util.utcnow()
        if battery_usage_per_hour is not None:
            self.battery_usage_per_hour = int(battery_usage_per_hour)
        if phone_ip:
            self.last_known_phone_ip = phone_ip
        self._notify_update()

        if self._watchdog_unsub:
            self._watchdog_unsub()
        self._watchdog_unsub = async_track_point_in_time(
            self.hass, self._watchdog_fired, fire_at
        )
        _LOGGER.debug("Watchdog gewapend voor %s", fire_at)

    async def async_disarm(self) -> None:
        """Wordt aangeroepen als de telefoon een gepland alarm annuleert/uitzet, vóórdat
        het is afgegaan. Voorkomt dat de watchdog later alsnog een backup-alarm start voor
        een alarm dat niet meer bestaat. Veilig/no-op als er toch niks gewapend stond."""
        if self._watchdog_unsub:
            self._watchdog_unsub()
            self._watchdog_unsub = None
        self.mobiel_betrouwbaar = True
        if hasattr(self, "next_alarm_fire_at"):
            self.next_alarm_fire_at = None
        self._notify_update()

    async def async_report_alive(
        self,
        reliable: bool,
        fallback: dict | None = None,
        phone_ip: str | None = None,
    ) -> None:
        """Wordt aangeroepen zodra het alarm daadwerkelijk afgaat op de telefoon."""
        self.alarm_actief = True
        self.mobiel_betrouwbaar = reliable
        if fallback:
            self.speaker_entity_id = fallback.get("speaker") or self.speaker_entity_id
            if fallback.get("volume") is not None:
                self.volume = int(fallback["volume"])
            if fallback.get("sound_url"):
                self.sound_url = fallback["sound_url"]
        if phone_ip:
            self.last_known_phone_ip = phone_ip
        self._notify_update()

        if not reliable and self.is_user_home():
            await self.async_start_fallback()

    @callback
    def _watchdog_fired(self, now) -> None:
        self._watchdog_unsub = None
        self.hass.async_create_task(self._async_watchdog_check())

    async def _async_watchdog_check(self) -> None:
        """Beslisvolgorde (fail-safe: bij twijfel altijd alarmeren, alleen onderdrukken als
        je zeker weet dat het niet nodig is):

        1. Telefoon al betrouwbaar gemeld -> geen actie.
        2. Anders: batterij waarschijnlijk leeg (topprioriteit) -> backup starten, aanwezigheid
           en uit-bed genegeerd (een telefoon-afhankelijke aanwezigheidsdetectie is zelf ook
           onbetrouwbaar geworden als de telefoon dood is).
        3. Anders: niet bevestigd thuis (echt niet-thuis, of geen presence-entity ingesteld)
           -> backup starten (fail-safe: presence-detectie kan zelf ook fout zitten).
        4. Anders (bevestigd thuis): niet bevestigd uit bed (nog in bed, of geen
           out-of-bed-entity ingesteld) -> backup starten. Wel bevestigd uit bed -> geen actie.
        """
        if self.mobiel_betrouwbaar:
            _LOGGER.debug("Watchdog: telefoon meldde zich betrouwbaar, geen actie")
            return

        if self.is_battery_likely_dead():
            _LOGGER.info(
                "Watchdog: batterij waarschijnlijk leeg -> backup starten "
                "(aanwezigheid/bed genegeerd)"
            )
            self.alarm_actief = True
            await self.async_start_fallback()
            return

        if not self.is_user_home():
            _LOGGER.info("Watchdog: niet bevestigd thuis -> backup starten (fail-safe)")
            self.alarm_actief = True
            await self.async_start_fallback()
            return

        if self.is_out_of_bed() is True:
            _LOGGER.debug("Watchdog: thuis en bevestigd uit bed, geen actie")
            return

        _LOGGER.info("Watchdog: thuis maar niet bevestigd uit bed -> backup starten")
        self.alarm_actief = True
        await self.async_start_fallback()

    # ------------------------------------------------------------------ #
    # Fallback afspelen / stoppen
    # ------------------------------------------------------------------ #
    async def async_start_fallback(self) -> None:
        if self.fallback_actief:
            return
        if not self.speaker_entity_id:
            _LOGGER.warning("Geen speaker geconfigureerd, kan fallback niet starten")
            return

        self.fallback_actief = True
        self.alarm_actief = True
        self._notify_update()

        await self._play_sound()

        interval = timedelta(seconds=max(1, self.fallback_interval))
        self._loop_unsub = async_track_time_interval(
            self.hass, self._loop_tick, interval
        )
        self._timeout_unsub = async_call_later(
            self.hass, self.safety_timeout_seconds, self._auto_stop
        )
        await self._notify_start()

    async def _loop_tick(self, now) -> None:
        if not (self.alarm_actief and self.fallback_actief):
            return
        await self._play_sound()

    async def _auto_stop(self, now) -> None:
        _LOGGER.info(
            "Veiligheidsklep: fallback automatisch gestopt na %s seconden",
            self.safety_timeout_seconds,
        )
        await self.async_stop(reason="safety_timeout")

    async def _play_sound(self) -> None:
        if not self.speaker_entity_id or not self.sound_url:
            _LOGGER.warning(
                "Fallback-geluid niet afgespeeld: speaker_entity_id=%s sound_url=%s",
                self.speaker_entity_id,
                self.sound_url,
            )
            return
        try:
            await self.hass.services.async_call(
                "media_player",
                "volume_set",
                {"entity_id": self.speaker_entity_id, "volume_level": self.volume / 100},
                blocking=False,
            )
            await self.hass.services.async_call(
                "media_player",
                "play_media",
                {
                    "entity_id": self.speaker_entity_id,
                    "media_content_id": self.sound_url,
                    "media_content_type": "music",
                },
                blocking=False,
            )
            self._last_played = dt_util.utcnow()
        except Exception:  # noqa: BLE001
            _LOGGER.exception("Fout bij afspelen fallback geluid")

    async def async_stop(self, reason: str = "manual") -> None:
        was_active = self.alarm_actief or self.fallback_actief
        self.alarm_actief = False
        self.fallback_actief = False
        self._notify_update()

        if self._loop_unsub:
            self._loop_unsub()
            self._loop_unsub = None
        if self._timeout_unsub:
            self._timeout_unsub()
            self._timeout_unsub = None

        if was_active and self.speaker_entity_id:
            try:
                await self.hass.services.async_call(
                    "media_player",
                    "media_stop",
                    {"entity_id": self.speaker_entity_id},
                    blocking=False,
                )
            except Exception:  # noqa: BLE001
                _LOGGER.exception("Fout bij stoppen speaker")

        if was_active:
            await self._notify_stop()
        _LOGGER.debug("Alarm gestopt (reden: %s)", reason)

    # ------------------------------------------------------------------ #
    # Notificaties
    # ------------------------------------------------------------------ #
    async def _notify_start(self) -> None:
        if not self.notify_service:
            return
        domain, _, service = self.notify_service.partition(".")
        try:
            await self.hass.services.async_call(
                domain or "notify",
                service or self.notify_service,
                {
                    "title": "Alarm active",
                    "message": "Alarm has started!",
                    "data": {
                        "persistent": True,
                        "tag": f"{DOMAIN}_alarm",
                        "actions": [{"action": f"{DOMAIN}_STOP", "title": "Stop alarm"}],
                    },
                },
                blocking=False,
            )
        except Exception:  # noqa: BLE001
            _LOGGER.exception("Fout bij versturen start-notificatie")

    async def _notify_stop(self) -> None:
        if not self.notify_service:
            return
        domain, _, service = self.notify_service.partition(".")
        try:
            await self.hass.services.async_call(
                domain or "notify",
                service or self.notify_service,
                {"message": "clear_notification", "data": {"tag": f"{DOMAIN}_alarm"}},
                blocking=False,
            )
        except Exception:  # noqa: BLE001
            _LOGGER.exception("Fout bij opruimen notificatie")

    # ------------------------------------------------------------------ #
    def _notify_update(self) -> None:
        async_dispatcher_send(self.hass, f"{SIGNAL_UPDATE}_{self.entry.entry_id}")


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    await hass.async_add_executor_job(_copy_default_sound_if_needed, hass)

    hass.data.setdefault(DOMAIN, {})
    hub = AlarmBackupHub(hass, entry)
    hass.data[DOMAIN][entry.entry_id] = hub

    # Herstelt een wapening/actieve fallback die net vóór deze (re)load bewaard is door
    # async_unload_entry - anders zou elke config-sync (Configureren, of de app die
    # instellingen pusht via /api/agendaalarm_backup/config) een lopende wapening/fallback
    # stilletjes tenietdoen, omdat de hub hierboven weer he-le-maal vers begint.
    await _restore_pending_rearm(hass, entry, hub)

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)

    # Views maar 1x globaal registreren, ongeacht hoeveel config entries er zijn.
    if not hass.data[DOMAIN].get("_views_registered"):
        from .http_views import async_register_views

        async_register_views(hass)
        hass.data[DOMAIN]["_views_registered"] = True

    # Luister naar de STOP_ALARM notificatie-actie (mobile_app_notification_action event)
    async def _handle_notification_action(event) -> None:
        if event.data.get("action") == f"{DOMAIN}_STOP":
            for h in hass.data[DOMAIN].values():
                if isinstance(h, AlarmBackupHub):
                    await h.async_stop(reason="notification_action")

    entry.async_on_unload(
        hass.bus.async_listen("mobile_app_notification_action", _handle_notification_action)
    )

    # Herlaad de entry zodra options wijzigen - of dat nu via de options-flow in de HA UI
    # gebeurt, of via een POST /api/agendaalarm_backup/config vanuit de app (config_sync.py).
    # Reload is de eenvoudigste manier om te garanderen dat de hub (en dus alle entities)
    # altijd de nieuwste opties gebruiken, zonder dubbele "opties toepassen"-logica bij te
    # houden naast AlarmBackupHub.__init__.
    entry.async_on_unload(entry.add_update_listener(_async_options_updated))

    return True


async def _async_options_updated(hass: HomeAssistant, entry: ConfigEntry) -> None:
    await hass.config_entries.async_reload(entry.entry_id)


def _save_pending_rearm(hass: HomeAssistant, entry: ConfigEntry, hub: AlarmBackupHub) -> None:
    """Bewaart een actieve wapening en/of een actief spelende fallback vlak vóórdat de hub
    wordt afgebroken, zodat een reload (config-wijziging via Configureren of vanuit de app,
    zie _async_options_updated) 'm niet stilletjes tenietdoet - zie _restore_pending_rearm.

    Twee onafhankelijke gevallen, want de watchdog-timer en "fallback speelt al" zijn niet
    hetzelfde moment: als de watchdog zelf al gevuurd is en de fallback is gestart, staat
    hub._watchdog_unsub al op None (_watchdog_fired wist 'm meteen) terwijl fallback_actief
    dan juist True is - dus beide onafhankelijk checken, niet alleen de watchdog-conditie.

    Telemetrie (batterij, telefoon-IP, laatst-ontvangen-event) wordt altijd bewaard, ongeacht
    of er op dit moment iets gewapend staat of een fallback speelt - dat was eerder niet zo
    (er zat een vroege "if not pending: return" vóór deze velden, dus ze werden alleen bewaard
    als er toevallig ook een wapening/fallback actief was). Sinds de app bij vrijwel elke
    instellingen-opslag/app-start/koppeling een config-patch pusht (zie _async_options_updated),
    gebeurt een reload nu heel vaak zonder dat er iets gewapend staat - dat is juist de normale
    situatie. Zonder deze fix verloor last_known_phone_ip dus bij bijna elke reload zijn waarde,
    waardoor de ping-detectie (binary_sensor.py) en de "Last event received"-diagnose
    (sensor.py) telkens terugvielen op "onbekend"/unavailable, ook al was er allang een IP/event
    bekend - en dat weer verklaarde waarom presence-gebaseerde speaker-modi (Standaard/Beide)
    de gebruiker onterecht als "niet thuis" beoordeelden.
    """
    pending: dict = {}
    armed_or_fallback = False

    if hub._watchdog_unsub and hub.next_alarm_fire_at and hub.next_alarm_fire_at > dt_util.utcnow():
        pending["fire_at"] = hub.next_alarm_fire_at
        pending["mobiel_betrouwbaar"] = hub.mobiel_betrouwbaar
        armed_or_fallback = True

    if hub.fallback_actief:
        pending["fallback_actief"] = True
        armed_or_fallback = True

    if armed_or_fallback:
        pending["fallback"] = {
            "speaker": hub.speaker_entity_id,
            "volume": hub.volume,
            "sound_url": hub.sound_url,
            "interval": hub.fallback_interval,
        }

    # Batterij-telemetrie - altijd bewaren (zie docstring), anders zou een reload 'm
    # terugzetten naar "onbekend" (fresh hub, zie AlarmBackupHub.__init__) totdat de app
    # toevallig weer een arm-aanroep doet, en zou is_battery_likely_dead() in de tussentijd
    # stil de verkeerde (want te optimistische) kant op vallen.
    pending["last_known_battery_percent"] = hub.last_known_battery_percent
    pending["last_battery_report_at"] = hub.last_battery_report_at
    pending["battery_usage_per_hour"] = hub.battery_usage_per_hour
    # Telefoon-IP en laatst-ontvangen-event - zelfde reden, ook altijd bewaren.
    pending["last_known_phone_ip"] = hub.last_known_phone_ip
    pending["last_event_received_at"] = hub.last_event_received_at
    pending["last_event_payload"] = hub.last_event_payload

    hass.data.setdefault(DOMAIN, {}).setdefault("_pending_rearm", {})[entry.entry_id] = pending
    _LOGGER.debug("Reload: state bewaard voor herstel na setup (%s)", pending)


async def _restore_pending_rearm(hass: HomeAssistant, entry: ConfigEntry, hub: AlarmBackupHub) -> None:
    """Herstelt wat _save_pending_rearm net vóór de reload bewaarde, op de kersverse hub."""
    pending = hass.data.setdefault(DOMAIN, {}).setdefault("_pending_rearm", {}).pop(entry.entry_id, None)
    if not pending:
        return

    fallback = pending.get("fallback")
    fire_at = pending.get("fire_at")

    # Batterij-telemetrie eerst herstellen (async_arm hieronder krijgt geen battery_percent
    # mee, dus laat deze velden met rust op de kersverse hub - alleen wij zetten ze terug).
    if "last_known_battery_percent" in pending:
        hub.last_known_battery_percent = pending["last_known_battery_percent"]
        hub.last_battery_report_at = pending["last_battery_report_at"]
        hub.battery_usage_per_hour = pending.get("battery_usage_per_hour", hub.battery_usage_per_hour)

    # .get(...) met de huidige (verse) hub-waarde als default: robuust tegen een oudere
    # pending-payload van vóór deze velden bestonden (bv. een reload die halverwege een eerdere
    # update plaatsvond).
    hub.last_known_phone_ip = pending.get("last_known_phone_ip", hub.last_known_phone_ip)
    hub.last_event_received_at = pending.get("last_event_received_at", hub.last_event_received_at)
    hub.last_event_payload = pending.get("last_event_payload", hub.last_event_payload)

    if fire_at and fire_at > dt_util.utcnow():
        await hub.async_arm(fire_at, fallback)
        # async_arm() zet mobiel_betrouwbaar altijd op False ("onbetrouwbaar totdat het
        # tegendeel bewezen is") - dat overschrijft hier de net bewaarde, mogelijk al
        # bevestigde waarde (bv. de telefoon had zich al gemeld vóór de reload). Terugzetten
        # naar precies wat 'm was, zodat een reload geen valse "onbetrouwbaar" veroorzaakt.
        if "mobiel_betrouwbaar" in pending:
            hub.mobiel_betrouwbaar = pending["mobiel_betrouwbaar"]
        _LOGGER.info("Reload: watchdog opnieuw gewapend voor %s (hersteld na reload)", fire_at)
    elif fire_at:
        _LOGGER.debug("Reload: bewaarde fire_at %s ligt al in het verleden, niet herwapend", fire_at)

    if pending.get("fallback_actief"):
        if fallback:
            hub.speaker_entity_id = fallback.get("speaker") or hub.speaker_entity_id
            if fallback.get("volume") is not None:
                hub.volume = int(fallback["volume"])
            if fallback.get("sound_url"):
                hub.sound_url = fallback["sound_url"]
            if fallback.get("interval") is not None:
                hub.fallback_interval = int(fallback["interval"])
        hub.alarm_actief = True
        await hub.async_start_fallback()
        _LOGGER.info("Reload: actief spelende fallback hervat na reload")


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    hub_before_unload: AlarmBackupHub | None = hass.data.get(DOMAIN, {}).get(entry.entry_id)
    if hub_before_unload is not None:
        _save_pending_rearm(hass, entry, hub_before_unload)

    unload_ok = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unload_ok:
        hub: AlarmBackupHub = hass.data[DOMAIN].pop(entry.entry_id)
        if hub._watchdog_unsub:
            hub._watchdog_unsub()
        if hub._loop_unsub:
            hub._loop_unsub()
        if hub._timeout_unsub:
            hub._timeout_unsub()
    return unload_ok
