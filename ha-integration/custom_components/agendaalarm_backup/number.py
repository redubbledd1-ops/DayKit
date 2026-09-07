"""Number entities voor DayKit (volume + herhaal-interval)."""
from __future__ import annotations

from homeassistant.components.number import NumberEntity, NumberMode
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .entity import DayKitEntity


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    hub = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FallbackVolumeNumber(hub, entry), FallbackIntervalNumber(hub, entry)])


class FallbackVolumeNumber(DayKitEntity, NumberEntity):
    _attr_icon = "mdi:volume-high"
    _attr_native_min_value = 0
    _attr_native_max_value = 100
    _attr_native_step = 1
    _attr_mode = NumberMode.SLIDER
    _attr_native_unit_of_measurement = "%"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "fallback_volume", "Fallback volume")

    @property
    def native_value(self) -> float:
        return float(self.hub.volume)

    async def async_set_native_value(self, value: float) -> None:
        self.hub.volume = int(value)
        self.hub._notify_update()


class FallbackIntervalNumber(DayKitEntity, NumberEntity):
    _attr_icon = "mdi:timer-refresh"
    _attr_native_min_value = 1
    _attr_native_max_value = 600
    _attr_native_step = 1
    _attr_mode = NumberMode.BOX
    _attr_native_unit_of_measurement = "s"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "fallback_interval", "Fallback repeat interval")

    @property
    def native_value(self) -> float:
        return float(self.hub.fallback_interval)

    async def async_set_native_value(self, value: float) -> None:
        self.hub.fallback_interval = int(value)
        self.hub._notify_update()
