"""Camera-entiteit die automatisch een scanbare koppel-QR toont op het apparaat-
dashboard.

Les uit het teruggedraaide to-do-experiment: HA's camera-domein rendert automatisch
een beeld-tegel op het auto-gegenereerde apparaat-dashboard, geen handmatige
dashboard-kaart nodig. `async_camera_image` genereert zelf een verse koppelcode als er
nog geen geldige is - dus zelfs direct na het toevoegen van de integratie, zonder ooit
op "Genereer koppelcode" gedrukt te hebben, toont deze tegel al een scanbare QR.
"""
from __future__ import annotations

import json

from homeassistant.components.camera import Camera
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.network import NoURLAvailableError, get_url

from .const import DOMAIN, ENTITY_KEY_PAIRING_QR_CAMERA
from .entity import DayKitEntity
from .pairing import get_pairing_store
from .qr import render_qr_png


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    hub = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([PairingQrCamera(hub, entry)])


class PairingQrCamera(DayKitEntity, Camera):
    """Toont de huidige koppelcode als QR-afbeelding - zelfde payload/beveiligingsmodel
    als sensor.daykit_pairing_code's `qr_payload`-attribuut en
    /api/daykit/qr_code.png (zie pairing.py voor de uitleg van het
    kortdurende-code-model)."""

    _attr_icon = "mdi:qrcode"

    def __init__(self, hub, entry: ConfigEntry) -> None:
        # Camera.__init__ zet o.a. self.content_type en self.access_tokens (nodig voor
        # de entity_picture-URL die het dashboard gebruikt) - DayKitEntity
        # roept geen super().__init__() aan, dus dit moet hier expliciet.
        DayKitEntity.__init__(self, hub, entry, ENTITY_KEY_PAIRING_QR_CAMERA, "Pairing QR")
        Camera.__init__(self)

    async def async_camera_image(
        self, width: int | None = None, height: int | None = None
    ) -> bytes | None:
        store = get_pairing_store(self.hass)
        if not self.hub.pairing_code or store.seconds_remaining(self.hub.pairing_code) <= 0:
            # Geen (geldige) code -> automatisch een nieuwe aanmaken. Dit is precies wat
            # de "Genereer koppelcode"-knop ook doet, alleen hier getriggerd door het
            # ophalen van de camera-thumbnail i.p.v. een handmatige klik.
            self.hub.generate_pairing_code()

        try:
            base_url = get_url(self.hass, prefer_external=False, allow_internal=True)
        except NoURLAvailableError:
            return None

        payload = json.dumps({"base_url": base_url, "code": self.hub.pairing_code})
        return await self.hass.async_add_executor_job(render_qr_png, payload)
