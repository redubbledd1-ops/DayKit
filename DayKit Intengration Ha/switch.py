"""Switch entities voor AgendaAlarm Backup."""
from __future__ import annotations

from typing import Any

from homeassistant.components.switch import SwitchEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN
from .entity import AgendaAlarmBackupEntity


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    hub = hass.data[DOMAIN][entry.entry_id]

    entities: list[SwitchEntity] = [
        AlarmActiefSwitch(hub, entry),
        MobielBetrouwbaarSwitch(hub, entry),
    ]

    # Alleen aanmaken als er geen echte presence-entity gekozen is in de setup -
    # anders wordt aanwezigheid live van die entity afgelezen (geen los switch nodig).
    if not hub.presence_entity_id:
        entities.append(GebruikerThuisSwitch(hub, entry))
    else:
        # Als de switch een vorige keer wél is aangemaakt (nog geen presence_entity_id
        # toen) en nu niet meer, blijft 'm anders als grijze/unavailable entiteit hangen -
        # HA verwijdert zelf niets wat een integratie niet langer aanmaakt.
        _async_remove_orphaned_gebruiker_thuis_switch(hass, entry)

    async_add_entities(entities)


def _async_remove_orphaned_gebruiker_thuis_switch(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Verwijdert een eerder geregistreerde GebruikerThuisSwitch uit het entity-register,
    zodat 'm niet als niet-schakelbare "unavailable" entiteit achterblijft. Stille no-op
    als 'm nooit heeft bestaan (bv. presence_entity_id stond al vanaf het begin ingesteld)."""
    entity_registry = er.async_get(hass)
    unique_id = f"{entry.entry_id}_gebruiker_thuis"
    existing_entity_id = entity_registry.async_get_entity_id("switch", DOMAIN, unique_id)
    if existing_entity_id:
        entity_registry.async_remove(existing_entity_id)


class AlarmActiefSwitch(AgendaAlarmBackupEntity, SwitchEntity):
    """Weerspiegelt/bestuurt of het alarm (incl. eventuele fallback) actief is.

    Vooral handig om handmatig te testen (aan = alsof het alarm net gestart is,
    respecteert dezelfde 'betrouwbaar + thuis'-conditie als de echte watchdog).
    """

    _attr_icon = "mdi:alarm-light"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "alarm_actief", "Alarm active")

    @property
    def is_on(self) -> bool:
        return self.hub.alarm_actief

    async def async_turn_on(self, **kwargs: Any) -> None:
        self.hub.alarm_actief = True
        if not self.hub.mobiel_betrouwbaar and self.hub.is_user_home():
            await self.hub.async_start_fallback()
        else:
            self.hub._notify_update()

    async def async_turn_off(self, **kwargs: Any) -> None:
        await self.hub.async_stop(reason="switch_off")


class MobielBetrouwbaarSwitch(AgendaAlarmBackupEntity, SwitchEntity):
    """True = telefoon meldt zich zelf (geen backup nodig), False = watchdog mag ingrijpen."""

    _attr_icon = "mdi:cellphone-check"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "mobiel_betrouwbaar", "Phone reliable")

    @property
    def is_on(self) -> bool:
        return self.hub.mobiel_betrouwbaar

    async def async_turn_on(self, **kwargs: Any) -> None:
        self.hub.mobiel_betrouwbaar = True
        self.hub._notify_update()

    async def async_turn_off(self, **kwargs: Any) -> None:
        self.hub.mobiel_betrouwbaar = False
        self.hub._notify_update()


class GebruikerThuisSwitch(AgendaAlarmBackupEntity, SwitchEntity):
    """Alleen aanwezig als er geen person./device_tracker.-entity is gekozen in de setup."""

    _attr_icon = "mdi:home-account"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, "gebruiker_thuis", "User home")

    @property
    def is_on(self) -> bool:
        return self.hub.gebruiker_thuis_fallback

    async def async_turn_on(self, **kwargs: Any) -> None:
        self.hub.gebruiker_thuis_fallback = True
        self.hub._notify_update()

    async def async_turn_off(self, **kwargs: Any) -> None:
        self.hub.gebruiker_thuis_fallback = False
        self.hub._notify_update()
