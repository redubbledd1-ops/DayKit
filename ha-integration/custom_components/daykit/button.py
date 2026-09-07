"""Button entity voor DayKit: genereert een kortdurende koppelcode."""
from __future__ import annotations

from homeassistant.components.button import ButtonEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, ENTITY_KEY_GENERATE_PAIRING_CODE
from .entity import DayKitEntity


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    hub = hass.data[DOMAIN][entry.entry_id]

    # Opruimen van een eerder geregistreerde UnpairButton - hoorde bij de inmiddels
    # volledig verwijderde is_paired-tracking (die functioneel toch niks gate'te - HA's
    # eigen requires_auth deed het echte werk), zou anders als "unavailable, not
    # provided by integration" blijven hangen.
    _async_remove_orphaned_unpair_button(hass, entry)

    async_add_entities([GeneratePairingCodeButton(hub, entry)])


def _async_remove_orphaned_unpair_button(hass: HomeAssistant, entry: ConfigEntry) -> None:
    entity_registry = er.async_get(hass)
    unique_id = f"{entry.entry_id}_unpair"
    existing_entity_id = entity_registry.async_get_entity_id("button", DOMAIN, unique_id)
    if existing_entity_id:
        entity_registry.async_remove(existing_entity_id)


class GeneratePairingCodeButton(DayKitEntity, ButtonEntity):
    """Genereert een nieuwe koppelcode (5 minuten geldig, 1x te gebruiken) voor het
    koppelen van de telefoon-app via QR-code of handmatige code-invoer. Zie
    sensor.daykit_pairing_code voor de code zelf + de QR-afbeelding.

    Altijd aanwezig - druk 'm gewoon nogmaals in voor een 2e toestel of om een
    verlopen code te vervangen."""

    _attr_icon = "mdi:qrcode-plus"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        super().__init__(hub, entry, ENTITY_KEY_GENERATE_PAIRING_CODE, "Generate pairing code")

    async def async_press(self) -> None:
        code = self.hub.generate_pairing_code()
        self.hub._notify_update()
        # Ook zichtbaar voor gebruikers die de sensor-entity niet in beeld hebben.
        try:
            await self.hass.services.async_call(
                "persistent_notification",
                "create",
                {
                    "title": "DayKit pairing code",
                    "message": (
                        f"Code: **{code}** (valid for 5 minutes, single use).\n\n"
                        "Scan the QR code (sensor.daykit_pairing_code) with the "
                        "DayKit app, or enter this code manually under "
                        "'Pair' in the app settings."
                    ),
                    "notification_id": f"{DOMAIN}_pairing_code",
                },
                blocking=False,
            )
        except Exception:  # noqa: BLE001
            pass
