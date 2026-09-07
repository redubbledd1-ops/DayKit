"""Kortdurende setup-code -> long-lived token uitwisseling.

Gekozen beveiligingsmodel (project-keuze: "Kortdurende setup-code" i.p.v. het
long-lived token direct in de QR-code zetten):

1. De gebruiker vraagt in HA een koppelcode aan (druk op de knop-entity
   "Genereer koppelcode"). Dat geeft een 6-cijferige code, geldig voor
   PAIRING_CODE_TTL_SECONDS en maar 1x te gebruiken.
2. De QR-code (sensor.daykit_pairing_code, attribuut qr_payload) bevat
   {"base_url": "...", "code": "123456"} - GEEN token. De 6-cijferige code
   kan ook handmatig overgetypt worden in de app als scannen niet lukt.
3. De app POST't de code naar het (bewust ongeauthenticeerde) /pair-endpoint.
   Pas op dat moment - en alleen bij een geldige, niet-verlopen, niet eerder
   gebruikte code - maakt HA een echt long-lived access token aan en geeft dat
   terug. De code zelf is daarna meteen verbruikt (eenmalig).
4. Een fout gokken van de code kost een poging; na MAX_ATTEMPTS pogingen of na
   de TTL wordt de code ongeldig, ook als hij nooit goed geraden is.

Dit is bewust in-memory (geen opslag op disk): een code die een HA-herstart
niet overleeft is precies wat je wil voor iets kortdurends.
"""
from __future__ import annotations

import logging
import secrets
import time
from dataclasses import dataclass

from homeassistant.auth.models import TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN
from homeassistant.core import HomeAssistant
from datetime import timedelta

_LOGGER = logging.getLogger(__name__)

PAIRING_CODE_TTL_SECONDS = 5 * 60
MAX_PAIRING_ATTEMPTS = 8
# Zichtbaar in HA's Profiel > Long-Lived Access Tokens. Was ooit Nederlands - wijzigen
# betekent dat een volgende her-koppeling (na een eerdere Nederlandse pairing) geen
# bestaand token meer herkent/hergebruikt (de reuse-check hieronder matcht op exacte
# client_name) en een nieuw token aanmaakt naast het oude, ongebruikte. Onschadelijk
# (koppelen blijft werken), maar laat 1x een verweesd extra token in het profiel achter
# voor bestaande installaties.
CLIENT_NAME = "DayKit (QR/code pairing)"
# Zelfde ordegrootte als een handmatig aangemaakt long-lived token in het HA-profiel.
TOKEN_LIFESPAN_DAYS = 3650


@dataclass
class _PendingCode:
    created_at: float
    attempts: int = 0


class PairingStore:
    """Eén instance gedeeld over alle config entries (hass.data[DOMAIN]["_pairing_store"])."""

    def __init__(self) -> None:
        self._codes: dict[str, _PendingCode] = {}

    def generate_code(self) -> str:
        self._cleanup_expired()
        code = f"{secrets.randbelow(1_000_000):06d}"
        self._codes[code] = _PendingCode(created_at=time.monotonic())
        _LOGGER.info("Nieuwe koppelcode aangemaakt (verloopt over %ss)", PAIRING_CODE_TTL_SECONDS)
        return code

    def seconds_remaining(self, code: str) -> int:
        pending = self._codes.get(code)
        if pending is None:
            return 0
        remaining = PAIRING_CODE_TTL_SECONDS - (time.monotonic() - pending.created_at)
        return max(0, int(remaining))

    def _cleanup_expired(self) -> None:
        now = time.monotonic()
        expired = [
            c for c, pending in self._codes.items()
            if now - pending.created_at > PAIRING_CODE_TTL_SECONDS
        ]
        for c in expired:
            self._codes.pop(c, None)

    def consume(self, code: str) -> bool:
        """Valideert en verbruikt een code in 1 stap (eenmalig gebruik). True = geldig."""
        self._cleanup_expired()
        pending = self._codes.get(code)
        if pending is None:
            return False

        pending.attempts += 1
        if pending.attempts > MAX_PAIRING_ATTEMPTS:
            _LOGGER.warning("Koppelcode %s te vaak fout geprobeerd, ongeldig gemaakt", code)
            self._codes.pop(code, None)
            return False

        if time.monotonic() - pending.created_at > PAIRING_CODE_TTL_SECONDS:
            self._codes.pop(code, None)
            return False

        # Geldig - eenmalig gebruik, dus meteen verwijderen zodat replay niet kan.
        self._codes.pop(code, None)
        return True


def get_pairing_store(hass: HomeAssistant) -> PairingStore:
    """Geeft de gedeelde PairingStore terug (1x aangemaakt, gedeeld over alle entries)."""
    from .const import DOMAIN

    hass.data.setdefault(DOMAIN, {})
    store = hass.data[DOMAIN].get("_pairing_store")
    if store is None:
        store = PairingStore()
        hass.data[DOMAIN]["_pairing_store"] = store
    return store


async def async_create_long_lived_token(hass: HomeAssistant) -> str | None:
    """Maakt een long-lived access token aan onder het HA owner-account - functioneel
    identiek aan wat een gebruiker handmatig aanmaakt via Profiel -> Long-Lived Access
    Tokens, maar dan zonder dat de gebruiker dat handmatig hoeft te kopieren/plakken.

    Let op: `hass.auth.async_get_owner()` bestaat niet als publieke API - de betrouwbare
    manier is alle users ophalen en die met `is_owner` eruit filteren. Ook
    `async_create_access_token` is een @callback (synchrone functie) ondanks de
    "async_"-naam, dus GEEN `await` ervoor - dat gaf eerder een onafgevangen
    TypeError ("object str can't be used in 'await' expression"), die HA's HTTP-laag
    vertaalde naar een kale 500 zonder duidelijke foutmelding.
    """
    users = await hass.auth.async_get_users()
    owner = next((u for u in users if u.is_owner), None)
    if owner is None:
        _LOGGER.error("Geen HA owner-account gevonden, kan geen long-lived token aanmaken")
        return None

    # HA staat maar 1 long-lived token per client_name toe per user - een 2e
    # async_create_refresh_token-aanroep met dezelfde client_name gooit
    # ValueError("<naam> already exists"). Dat gebeurt hier bij elke herkoppeling
    # (nieuwe telefoon, opnieuw geïnstalleerd, etc.), dus: bestaand token hergebruiken
    # i.p.v. een nieuwe aanmaken. Er wordt alleen een nieuwe JWT (access token) van
    # gemint - het onderliggende refresh_token-record blijft hetzelfde.
    existing = next(
        (
            token for token in owner.refresh_tokens.values()
            if token.client_name == CLIENT_NAME
            and token.token_type == TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN
        ),
        None,
    )
    if existing is not None:
        refresh_token = existing
    else:
        refresh_token = await hass.auth.async_create_refresh_token(
            owner,
            client_name=CLIENT_NAME,
            token_type=TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN,
            access_token_expiration=timedelta(days=TOKEN_LIFESPAN_DAYS),
        )
    return hass.auth.async_create_access_token(refresh_token)


async def async_revoke_long_lived_token(hass: HomeAssistant) -> bool:
    """Trekt het long-lived token in dat async_create_long_lived_token hierboven aanmaakte
    (zelfde CLIENT_NAME-matching), voor de "Ontkoppelen"-knop (button.py). Verwijdert het
    onderliggende refresh_token-record via hass.auth zelf, wat elke access token die de app
    ermee heeft gemint ook direct ongeldig maakt. True = een token gevonden en ingetrokken,
    False = stille no-op (geen owner of geen matchend token, bv. al eerder ontkoppeld)."""
    users = await hass.auth.async_get_users()
    owner = next((u for u in users if u.is_owner), None)
    if owner is None:
        return False

    existing = next(
        (
            token for token in owner.refresh_tokens.values()
            if token.client_name == CLIENT_NAME
            and token.token_type == TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN
        ),
        None,
    )
    if existing is None:
        return False

    hass.auth.async_remove_refresh_token(existing)
    _LOGGER.info("Long-lived token ingetrokken (ontkoppeld)")
    return True
