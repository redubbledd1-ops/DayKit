"""Gedeelde basis-entity voor AgendaAlarm Backup entities."""
from __future__ import annotations

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo, Entity

from .const import DOMAIN, SIGNAL_UPDATE


class AgendaAlarmBackupEntity(Entity):
    """Basisklasse: groepeert alle entities onder 1 device en luistert naar hub-updates."""

    _attr_should_poll = False
    _attr_has_entity_name = True

    def __init__(self, hub, entry: ConfigEntry, key: str, name: str) -> None:
        self.hub = hub
        self._entry = entry
        self._attr_unique_id = f"{entry.entry_id}_{key}"
        self._attr_name = name
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, entry.entry_id)},
            name="AgendaAlarm Backup",
            manufacturer="AgendaAlarm",
            model="Backup alarm watchdog",
        )

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(
                self.hass,
                f"{SIGNAL_UPDATE}_{self._entry.entry_id}",
                self._handle_update,
            )
        )

    @callback
    def _handle_update(self) -> None:
        """Dispatcher-target voor SIGNAL_UPDATE (zie AlarmBackupHub._notify_update).

        MOET @callback zijn: zonder deze marker classificeert HA's dispatcher elke
        undecorated, niet-coroutine target automatisch als "Executor job"
        (get_hassjob_callable_job_type in homeassistant/core.py) en voert 'm uit via
        hass.async_add_executor_job - dus in een worker thread, niet op de event loop.
        async_write_ha_state() hierbinnen is dan een thread-safety-violation
        ("calls async_write_ha_state from a thread other than the event loop"), en de
        exception wordt stil gelogd (nooit teruggekoppeld naar de aanroeper van
        _notify_update - zie __init__.py), dus de entity-state wordt gewoon nooit
        bijgewerkt zonder dat er verder iets zichtbaar misgaat.
        """
        self.async_write_ha_state()
