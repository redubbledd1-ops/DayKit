"""Binary sensor voor de ingebouwde ping-detectie (zie __init__.py's last_known_phone_ip).

Losstaand van sensor.py's OutOfBedSensor/HomeLiveSensor (die bewust van binary_sensor naar
sensor verplaatst zijn voor een nette True/False-weergave, zie sensor.py's moduledocstring):
deze sensor moet wél een binary_sensor zijn, want hij is bedoeld om zelf als presence_entity_id
gekozen (handmatig, of automatisch geadopteerd door de app) te worden - AlarmBackupHub.is_user_home()
en de presence-picker in config_flow.py verwachten daar een device_tracker/person/binary_sensor-
achtige on/off-state, geen losse tekstsensor.

Gebruikt icmplib (zelfde dependency + versie als HA core's eigen ping-integratie,
homeassistant/components/ping, icmplib==3.0.4 - zie manifest.json) i.p.v. een eigen
ping-implementatie te verzinnen. HA's eigen ping-component telt zelf geen mislukkingen over
meerdere update-cycli heen (alleen meerdere ICMP-pakketten binnen 1 check via count=) - dat
beschermt niet tegen een hele cyclus radio-slaap op de telefoon, dus hier wel expliciet een
consecutive-failure-teller (zie PING_CONSECUTIVE_FAILURES_BEFORE_UNAVAILABLE in const.py).
"""
from __future__ import annotations

import logging
from datetime import timedelta

from homeassistant.components.binary_sensor import BinarySensorDeviceClass, BinarySensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.event import async_track_time_interval
import homeassistant.util.dt as dt_util

from .const import (
    DOMAIN,
    ENTITY_KEY_PHONE_REACHABLE,
    PING_CONSECUTIVE_FAILURES_BEFORE_UNAVAILABLE,
    PING_INTERVAL_SECONDS,
    PING_TIMEOUT_SECONDS,
)
from .entity import AgendaAlarmBackupEntity

_LOGGER = logging.getLogger(__name__)

# Auto-gedetecteerde icmplib-privilege-modus, 1x bepaald en hergebruikt voor de levensduur van
# het HA-proces (zelfde aanpak als HA core's eigen ping-integratie se
# _can_use_icmp_lib_with_privilege) - niet per ping opnieuw uitzoeken. Losse "computed"-vlag
# nodig omdat None zelf ook een geldige uitkomst is (geen van beide modi werkt).
_privileged_mode_computed = False
_privileged_ping_mode: bool | None = None


async def _async_detect_privileged_mode() -> bool | None:
    """True = raw socket (root/CAP_NET_RAW) werkt, False = unprivileged (Linux ping-groep) werkt,
    None = geen van beide - ping-detectie kan dan niet werken op dit systeem."""
    global _privileged_mode_computed, _privileged_ping_mode
    if _privileged_mode_computed:
        return _privileged_ping_mode

    from icmplib import SocketPermissionError, async_ping

    try:
        await async_ping("127.0.0.1", count=0, timeout=0, privileged=True)
        _privileged_ping_mode = True
    except SocketPermissionError:
        try:
            await async_ping("127.0.0.1", count=0, timeout=0, privileged=False)
            _privileged_ping_mode = False
        except SocketPermissionError:
            _LOGGER.warning(
                "Kan geen ICMP-ping versturen (geen raw-socket-permissie) - "
                "de telefoon-bereikbaarheid-sensor blijft unavailable"
            )
            _privileged_ping_mode = None
    _privileged_mode_computed = True
    return _privileged_ping_mode


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    hub = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([PhoneReachableBinarySensor(hub, entry)])


class PhoneReachableBinarySensor(AgendaAlarmBackupEntity, BinarySensorEntity):
    """Pingt hub.last_known_phone_ip (door de app zelf gerapporteerd via arm/report_alive,
    zie __init__.py) - geen handmatig ingesteld IP-adres nodig.

    Onbeschikbaar (niet True/False) zolang er nog geen IP bekend is (bv. vlak na installatie,
    vóór de eerste arm/report-alive-call) - nooit een valse "niet thuis" tonen zonder data.
    """

    _attr_device_class = BinarySensorDeviceClass.CONNECTIVITY
    _attr_icon = "mdi:cellphone-wireless"
    # Geen automatische HA-polling: deze entity ververst zichzelf al via de eigen
    # async_track_time_interval-lus hierboven (PING_INTERVAL_SECONDS). should_poll=False
    # voorkomt dat het toevoegen van async_update() hieronder een TWEEDE, overlappende
    # poll-lus aanzet via HA's generieke entity-platform-scheduler. async_update() werkt
    # ondanks should_poll=False nog steeds via een expliciete homeassistant.update_entity-
    # service-call (force_refresh negeert should_poll) - dat is precies waar 'm voor is.
    _attr_should_poll = False

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, ENTITY_KEY_PHONE_REACHABLE, "Phone reachable (ping)")
        self._consecutive_failures = 0
        self._is_reachable: bool | None = None
        self._pinged_ip: str | None = None
        self._last_checked = None
        self._unsub_interval = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub_interval = async_track_time_interval(
            self.hass, self._async_ping, timedelta(seconds=PING_INTERVAL_SECONDS)
        )
        self.async_on_remove(self._async_remove_interval)
        # Meteen een eerste check doen i.p.v. tot de eerste interval-tick te wachten.
        await self._async_ping(None)

    @callback
    def _async_remove_interval(self) -> None:
        if self._unsub_interval:
            self._unsub_interval()
            self._unsub_interval = None

    async def async_update(self) -> None:
        """Maakt deze sensor forceerbaar via de homeassistant.update_entity-service, zodat de
        app (AlarmOutputDecisionEngine.isUserAtHome, via
        HomeAssistantRepository.forceUpdateEntity) vlak vóór een "niet thuis"-beslissing 1x een
        directe herping kan afdwingen i.p.v. te moeten wachten op de eerstvolgende
        PING_INTERVAL_SECONDS-tick. Roept dezelfde pinglogica aan als de periodieke lus."""
        await self._async_ping(None)

    async def _async_ping(self, now) -> None:
        ip = self.hub.last_known_phone_ip
        self._pinged_ip = ip
        self._last_checked = dt_util.utcnow()

        if not ip:
            self._is_reachable = None
            self._consecutive_failures = 0
            self.async_write_ha_state()
            return

        privileged = await _async_detect_privileged_mode()
        if privileged is None:
            self._is_reachable = None
            self.async_write_ha_state()
            return

        try:
            from icmplib import async_ping

            result = await async_ping(
                ip, count=1, timeout=PING_TIMEOUT_SECONDS, privileged=privileged
            )
            alive = result.is_alive
        except Exception:  # noqa: BLE001 - netwerk-/icmplib-fout mag de sensor niet crashen
            _LOGGER.debug("Ping naar %s mislukt met een fout", ip, exc_info=True)
            alive = False

        if alive:
            self._consecutive_failures = 0
            self._is_reachable = True
        else:
            self._consecutive_failures += 1
            if self._consecutive_failures >= PING_CONSECUTIVE_FAILURES_BEFORE_UNAVAILABLE:
                self._is_reachable = False
            # Anders: hou de vorige state aan totdat de teller de drempel haalt (bescherming
            # tegen een losse gemiste ping i.p.v. na 1x meteen "niet thuis" te concluderen).

        self.async_write_ha_state()

    @property
    def available(self) -> bool:
        return self._is_reachable is not None

    @property
    def is_on(self) -> bool | None:
        return self._is_reachable

    @property
    def extra_state_attributes(self) -> dict:
        return {
            "pinged_ip": self._pinged_ip,
            "consecutive_failures": self._consecutive_failures,
            "last_checked": self._last_checked,
        }
