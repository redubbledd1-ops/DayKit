"""Gedeelde QR-PNG-rendering.

Gebruikt door zowel camera.py (PairingQrCamera) als http_views.py's
AgendaAlarmQrCodeView - 1 plek voor de qrcode.make(...)-aanroep i.p.v. 'm twee keer
te laten bestaan.
"""
from __future__ import annotations

import io


def render_qr_png(payload: str) -> bytes:
    """Rendert een JSON-payload-string als QR-code PNG-bytes.

    Blocking (de qrcode-library doet CPU-werk) - dus aanroepen via
    `hass.async_add_executor_job(render_qr_png, payload)`, nooit direct awaiten.
    """
    import qrcode

    img = qrcode.make(payload)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()
