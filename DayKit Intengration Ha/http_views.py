"""HTTP endpoints voor AgendaAlarm Backup.

Vervangt zowel de losse alarm_app_v1-webhook als de losse AppDaemon-geluid-app
uit de handmatige opzet. Beide endpoints vereisen hetzelfde long-lived token
dat de app al gebruikt voor alle andere Home Assistant-aanroepen (requires_auth),
dus geen apart 'local_only'-webhook-gat meer zoals voorheen.
"""
from __future__ import annotations

import base64
import json
import logging
import os
import re

from aiohttp import web

from homeassistant.core import HomeAssistant
from homeassistant.helpers.http import HomeAssistantView
from homeassistant.helpers.network import NoURLAvailableError, get_url
import homeassistant.util.dt as dt_util

from .const import CONFIG_URL, DOMAIN, EVENT_URL, PAIR_URL, QR_IMAGE_URL, SOUND_UPLOAD_URL, SOUNDS_SUBDIR
from .config_sync import async_apply_config_patch, serialize_config
from .pairing import async_create_long_lived_token, get_pairing_store
from .qr import render_qr_png

_LOGGER = logging.getLogger(__name__)

MAX_SOUND_BYTES = 20 * 1024 * 1024  # 20MB


def _hubs(hass: HomeAssistant):
    from . import AlarmBackupHub  # lazy import, voorkomt circulaire import

    for value in hass.data.get(DOMAIN, {}).values():
        if isinstance(value, AlarmBackupHub):
            yield value


class AgendaAlarmEventView(HomeAssistantView):
    """POST /api/agendaalarm_backup/event

    body:
      {"action": "arm", "fire_at": "2026-07-09T06:31:30+02:00",
       "fallback": {"speaker": "media_player.woonkamer", "volume": 70,
                    "sound_url": "...", "interval": 10},
       "battery_percent": 42, "battery_usage_per_hour": 5, "phone_ip": "192.168.1.42"}
      {"action": "start", "mobiel_betrouwbaar": true, "fallback": {...}, "phone_ip": "192.168.1.42"}
      {"action": "stop"}
      {"action": "disarm"}

    `phone_ip` is optioneel (de app stuurt 'm niet mee als er geen actief netwerkadres
    gevonden kon worden) en voedt de ingebouwde ping-detectie, zie binary_sensor.py.
    """

    url = EVENT_URL
    name = f"api:{DOMAIN}:event"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        try:
            data = await request.json()
        except Exception:  # noqa: BLE001
            return web.json_response({"error": "invalid JSON"}, status=400)

        action = data.get("action")
        hubs = list(_hubs(hass))
        if not hubs:
            return web.json_response({"error": "no configured entry"}, status=400)

        # Diagnose: onthoud elke binnenkomende, geauthenticeerde aanroep hier - ongeacht
        # welke actie het is of of de verdere verwerking hieronder slaagt (zie
        # AlarmBackupHub.record_incoming_event in __init__.py + sensor.py's
        # LastEventSensor). Eerste plek om te checken of de app HA daadwerkelijk bereikt.
        for hub in hubs:
            hub.record_incoming_event(action, data)

        if action == "arm":
            fire_at_str = data.get("fire_at")
            if not fire_at_str:
                return web.json_response({"error": "fire_at is required"}, status=400)
            fire_at = dt_util.parse_datetime(fire_at_str)
            if fire_at is None:
                return web.json_response({"error": "could not parse fire_at"}, status=400)
            for hub in hubs:
                await hub.async_arm(
                    fire_at,
                    data.get("fallback"),
                    battery_percent=data.get("battery_percent"),
                    battery_usage_per_hour=data.get("battery_usage_per_hour"),
                    phone_ip=data.get("phone_ip"),
                )
            return web.json_response({"success": True})

        if action == "start":
            reliable = bool(data.get("mobiel_betrouwbaar", True))
            for hub in hubs:
                await hub.async_report_alive(
                    reliable, data.get("fallback"), phone_ip=data.get("phone_ip")
                )
            return web.json_response({"success": True})

        if action == "stop":
            for hub in hubs:
                await hub.async_stop(reason="app_stop")
            return web.json_response({"success": True})

        if action == "disarm":
            for hub in hubs:
                await hub.async_disarm()
            return web.json_response({"success": True})

        return web.json_response({"error": f"unknown action '{action}'"}, status=400)


class AgendaAlarmSoundUploadView(HomeAssistantView):
    """POST /api/agendaalarm_backup/sound_upload

    body: {"filename": "mijn_geluid.mp3", "data_base64": "<base64 audio bytes>"}
    Slaat het bestand op onder <config>/www/agendaalarm_sounds/ (publiek bereikbaar
    via /local/agendaalarm_sounds/<bestand>) en onderhoudt manifest.json met alle
    bestandsnamen, zodat de app kan zien wat er al op HA staat.
    """

    url = SOUND_UPLOAD_URL
    name = f"api:{DOMAIN}:sound_upload"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        try:
            data = await request.json()
        except Exception:  # noqa: BLE001
            return web.json_response({"error": "invalid JSON"}, status=400)

        filename = (data.get("filename") or "").strip()
        b64data = data.get("data_base64") or ""
        if not filename or not b64data:
            return web.json_response(
                {"error": "filename and data_base64 are required"}, status=400
            )

        safe_name = re.sub(r"[^A-Za-z0-9_.-]", "_", filename)
        if not safe_name.lower().endswith(".mp3"):
            safe_name += ".mp3"

        try:
            audio_bytes = base64.b64decode(b64data)
        except Exception:  # noqa: BLE001
            return web.json_response({"error": "could not decode data_base64"}, status=400)

        if not audio_bytes:
            return web.json_response({"error": "empty file"}, status=400)
        if len(audio_bytes) > MAX_SOUND_BYTES:
            return web.json_response(
                {"error": f"file too large (max {MAX_SOUND_BYTES // (1024*1024)}MB)"},
                status=400,
            )

        sounds_dir = hass.config.path("www", SOUNDS_SUBDIR)

        def _write() -> None:
            os.makedirs(sounds_dir, exist_ok=True)
            manifest_path = os.path.join(sounds_dir, "manifest.json")
            try:
                with open(manifest_path, "r") as f:
                    manifest = json.load(f)
            except Exception:  # noqa: BLE001
                manifest = []

            with open(os.path.join(sounds_dir, safe_name), "wb") as f:
                f.write(audio_bytes)

            if safe_name not in manifest:
                manifest.append(safe_name)
                with open(manifest_path, "w") as f:
                    json.dump(sorted(set(manifest)), f)

        try:
            await hass.async_add_executor_job(_write)
        except Exception:  # noqa: BLE001
            _LOGGER.exception("Fout bij opslaan geluid %s", safe_name)
            return web.json_response({"error": "failed to save"}, status=500)

        _LOGGER.info("Alarm sound opgeslagen: %s (%d bytes)", safe_name, len(audio_bytes))
        return web.json_response(
            {"success": True, "filename": safe_name, "url": f"/local/{SOUNDS_SUBDIR}/{safe_name}"}
        )


class AgendaAlarmPairView(HomeAssistantView):
    """POST /api/agendaalarm_backup/pair - bewust ONGEAUTHENTICEERD.

    Dit IS het endpoint waarmee een telefoon zonder token er een krijgt. De
    beveiliging zit in de kortdurende, eenmalige koppelcode (zie pairing.py),
    niet in HA's normale auth - vandaar requires_auth = False. Zie ook het
    project-besluit "Kortdurende setup-code" i.p.v. het long-lived token
    rechtstreeks in de QR-code zetten.

    body: {"code": "123456"}
    response (success): {"success": true, "token": "..."}
    response (failure): {"error": "..."}
    """

    url = PAIR_URL
    name = f"api:{DOMAIN}:pair"
    requires_auth = False

    async def post(self, request: web.Request) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        try:
            data = await request.json()
        except Exception:  # noqa: BLE001
            return web.json_response({"error": "invalid JSON"}, status=400)

        code = str(data.get("code") or "").strip()
        if not code:
            return web.json_response({"error": "code is required"}, status=400)

        store = get_pairing_store(hass)
        if not store.consume(code):
            _LOGGER.warning("Koppelpoging met ongeldige/verlopen code afgewezen")
            return web.json_response({"error": "code invalid or expired"}, status=400)

        try:
            token = await async_create_long_lived_token(hass)
        except Exception as err:  # noqa: BLE001 - vang alles, anders krijgt de app een kale 500
            _LOGGER.exception("Onverwachte fout bij aanmaken long-lived token")
            return web.json_response({"error": f"internal error creating token: {err}"}, status=500)

        if token is None:
            return web.json_response(
                {"error": "could not create token (no owner account found?)"}, status=500
            )

        _LOGGER.info("Koppeling via setup-code/QR voltooid, nieuw long-lived token aangemaakt")
        return web.json_response({"success": True, "token": token})


class AgendaAlarmQrCodeView(HomeAssistantView):
    """GET /api/agendaalarm_backup/qr_code.png

    Rendert de huidige actieve koppelcode als QR-afbeelding, voor op een dashboard
    (bijv. een Picture-kaart naar deze URL). Vereist gewone HA-auth (dit is voor
    de dashboard-kant / browser-sessie - de app zelf gebruikt de code/qr_payload
    van sensor.agendaalarm_koppelcode, niet deze afbeelding-URL, om te koppelen,
    want de app heeft nog geen token als hij nog moet koppelen).
    """

    url = QR_IMAGE_URL
    name = f"api:{DOMAIN}:qr_code"
    requires_auth = True

    async def get(self, request: web.Request) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        hub = next((h for h in _hubs(hass) if h.pairing_code), None)
        if hub is None:
            return web.Response(
                status=404, text="No active pairing code - press 'Generate pairing code' first"
            )

        store = get_pairing_store(hass)
        if store.seconds_remaining(hub.pairing_code) <= 0:
            return web.Response(status=404, text="Pairing code has expired - generate a new one")

        try:
            base_url = get_url(hass, prefer_external=False, allow_internal=True)
        except NoURLAvailableError:
            return web.Response(status=500, text="No HA base URL available")

        payload = json.dumps({"base_url": base_url, "code": hub.pairing_code})

        try:
            png_bytes = await hass.async_add_executor_job(render_qr_png, payload)
        except Exception:  # noqa: BLE001
            _LOGGER.exception("Fout bij renderen QR-afbeelding")
            return web.Response(status=500, text="Could not generate QR image")

        return web.Response(body=png_bytes, content_type="image/png")


class AgendaAlarmConfigView(HomeAssistantView):
    """GET/POST /api/agendaalarm_backup/config - config-sync, app is leidend.

    De app stuurt elke keer dat er lokaal opgeslagen wordt (en bij elke (her)koppeling)
    haar volledige instellingen naar HA (POST, partiele patch toegepast op de config-entry
    options) - zie config_sync.py. De app leest dit bewust niet meer terug (GET) om de
    lokale staat te overschrijven; GET blijft beschikbaar zodat HA's eigen "Configureren"-
    scherm en eventuele diagnose de huidige stand kunnen tonen.

    GET  -> {"speaker_entity_id": ..., "speaker_mode": "BOTH", "entities": [...], ...}
    POST body: elke deelverzameling van dezelfde velden, bv.
      {"entities": ["media_player.woonkamer", "light.hoofdlamp_hue_ambiance"]}
    POST response: de volledige, bijgewerkte config (zelfde vorm als GET).
    """

    url = CONFIG_URL
    name = f"api:{DOMAIN}:config"
    requires_auth = True

    async def get(self, request: web.Request) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        return web.json_response(serialize_config(hass))

    async def post(self, request: web.Request) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        try:
            patch = await request.json()
        except Exception:  # noqa: BLE001
            return web.json_response({"error": "invalid JSON"}, status=400)

        if not isinstance(patch, dict):
            return web.json_response({"error": "body must be a JSON object"}, status=400)

        try:
            updated = await async_apply_config_patch(hass, patch)
        except Exception:  # noqa: BLE001
            _LOGGER.exception("Onverwachte fout bij toepassen config-patch")
            return web.json_response({"error": "could not update config"}, status=500)

        return web.json_response(updated)


def async_register_views(hass: HomeAssistant) -> None:
    hass.http.register_view(AgendaAlarmEventView)
    hass.http.register_view(AgendaAlarmSoundUploadView)
    hass.http.register_view(AgendaAlarmPairView)
    hass.http.register_view(AgendaAlarmQrCodeView)
    hass.http.register_view(AgendaAlarmConfigView)
