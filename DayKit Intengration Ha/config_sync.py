"""Config-sync tussen Home Assistant en de AgendaAlarm-app.

De APP is de bron van waarheid, niet HA: de app schrijft haar lokale instellingen naar
HA via POST /api/agendaalarm_backup/config bij elke lokale save (en bij elke (her)koppeling),
zie HaSettingsViewModel.kt's saveSettings()/applyHomeAssistantSettings(). HA's config-entry
`options` is hier het doelwit dat bijgewerkt wordt - de app leest deze bewust niet meer terug
om de lokale staat te overschrijven (dat overschreef anders bij elke app-start/koppeling de
instellingen van de gebruiker met HA's kale defaults zodra de config-entry nog leeg stond).
GET /api/agendaalarm_backup/config (serialize_config hieronder) blijft bestaan zodat de
huidige stand in HA's eigen "Configureren"-scherm getoond kan worden, maar wordt niet meer
gebruikt om de app te vullen.

Let op: dit dekt bewust NIET `battery_usage_per_hour` (dat loopt via een losse,
al langer bestaande `input_number.mobiel_batterij_per_uur`-helper aan de HA-kant
en `SettingsManager` aan de app-kant, zie HaSettingsViewModel.kt's
syncBatteryUsageToHomeAssistant) en ook nog niet een naam-uit-URL mapping
voor `default_sound_url` (bv. "Boogie" -> volledige GitHub-URL) - de app stuurt/
ontvangt gewoon de kale URL. Beide zijn losse vervolgstappen, zie README.
"""
from __future__ import annotations

import logging

from homeassistant.core import HomeAssistant

from .const import (
    CONF_ALARM_SCRIPT_ENABLED,
    CONF_ALARM_SCRIPT_ENTITY,
    CONF_ALARM_SCRIPT_IGNORE_PRESENCE,
    CONF_DEFAULT_INTERVAL,
    CONF_DEFAULT_SOUND_URL,
    CONF_DEFAULT_VOLUME,
    CONF_ENTITIES,
    CONF_NOTIFY_SERVICE,
    CONF_OUT_OF_BED_ENABLED,
    CONF_OUT_OF_BED_ENTITY,
    CONF_OUT_OF_BED_EXPECTED_VALUE,
    CONF_PRESENCE_ENTITY,
    CONF_PRESENCE_EXPECTED_STATE,
    CONF_SAFETY_TIMEOUT,
    CONF_SPEAKER_ENTITY,
    CONF_SPEAKER_MODE,
    CONF_TIMER_SCRIPT_ENABLED,
    CONF_TIMER_SCRIPT_ENTITY,
    CONF_TIMER_SCRIPT_IGNORE_PRESENCE,
    DEFAULT_INTERVAL,
    DEFAULT_OUT_OF_BED_ENABLED,
    DEFAULT_OUT_OF_BED_EXPECTED_VALUE,
    DEFAULT_PRESENCE_EXPECTED_STATE,
    DEFAULT_SAFETY_TIMEOUT,
    DEFAULT_SOUND_PATH,
    DEFAULT_SPEAKER_MODE,
    DEFAULT_VOLUME,
    DOMAIN,
)

_LOGGER = logging.getLogger(__name__)

# Alle velden die via /config gelezen/geschreven mogen worden, met hun default
# als er nog niets ingesteld is. Nieuwe syncbare velden hier + in const.py + in de
# juiste sectie-schema in config_flow.py's AgendaAlarmBackupOptionsFlow toevoegen
# houdt dit overzichtelijk op 1 plek.
CONFIG_FIELDS: dict[str, object] = {
    CONF_SPEAKER_ENTITY: None,
    CONF_SPEAKER_MODE: DEFAULT_SPEAKER_MODE,
    CONF_PRESENCE_ENTITY: None,
    CONF_PRESENCE_EXPECTED_STATE: DEFAULT_PRESENCE_EXPECTED_STATE,
    CONF_OUT_OF_BED_ENTITY: None,
    CONF_OUT_OF_BED_EXPECTED_VALUE: DEFAULT_OUT_OF_BED_EXPECTED_VALUE,
    CONF_OUT_OF_BED_ENABLED: DEFAULT_OUT_OF_BED_ENABLED,
    CONF_ALARM_SCRIPT_ENTITY: None,
    CONF_ALARM_SCRIPT_ENABLED: False,
    CONF_ALARM_SCRIPT_IGNORE_PRESENCE: True,
    CONF_TIMER_SCRIPT_ENTITY: None,
    CONF_TIMER_SCRIPT_ENABLED: False,
    CONF_TIMER_SCRIPT_IGNORE_PRESENCE: True,
    CONF_NOTIFY_SERVICE: None,
    CONF_DEFAULT_VOLUME: DEFAULT_VOLUME,
    CONF_DEFAULT_INTERVAL: DEFAULT_INTERVAL,
    # None hier (i.p.v. een vaste string): de echte default is de lokaal-gehoste
    # discoAlarmBackupAlarm.mp3, waarvan de absolute URL van de HA-instance afhangt
    # (get_url) - zie serialize_config()'s override hieronder.
    CONF_DEFAULT_SOUND_URL: None,
    CONF_SAFETY_TIMEOUT: DEFAULT_SAFETY_TIMEOUT,
    CONF_ENTITIES: [],
}


def _first_entry(hass: HomeAssistant):
    """Deze integratie gaat er (net als http_views.py's _hubs()) van uit dat er
    normaliter 1 config entry actief is (1 HA-instantie <-> 1 AgendaAlarm-app-koppeling)."""
    entries = hass.config_entries.async_entries(DOMAIN)
    return entries[0] if entries else None


def _default_local_sound_url(hass: HomeAssistant) -> str:
    """Absolute URL naar het bij de integratie gebundelde fallback-geluid (zie __init__.py's
    async_setup_entry, dat het bestand naar <config>/www/agendaalarm_sounds/ kopieert).
    Lokaal gehost, dus werkt zonder internetverbinding - in tegenstelling tot de oude
    hardcoded GitHub-URL default."""
    from homeassistant.helpers.network import get_url

    try:
        return f"{get_url(hass, prefer_external=False, allow_internal=True)}{DEFAULT_SOUND_PATH}"
    except Exception:  # noqa: BLE001 - geen URL beschikbaar (zeer ongebruikelijke HA-config)
        _LOGGER.warning("Kon geen lokale HA-URL bepalen voor het standaard fallback-geluid")
        return ""


def serialize_config(hass: HomeAssistant) -> dict:
    """Geeft de huidige, volledige config terug (met defaults voor ontbrekende velden).

    `default_sound_url` is nooit leeg: een missende/lege waarde in de config-entry wordt hier
    altijd vervangen door de lokaal-gehoste standaard (zie _default_local_sound_url), zodat de
    fallback-speaker nooit stil blijft door een lege/ontbrekende instelling.
    """
    entry = _first_entry(hass)
    merged = {**entry.data, **entry.options} if entry is not None else {}
    result = {key: merged.get(key, default) for key, default in CONFIG_FIELDS.items()}
    if not result.get(CONF_DEFAULT_SOUND_URL):
        result[CONF_DEFAULT_SOUND_URL] = _default_local_sound_url(hass)
    return result


async def async_apply_config_patch(hass: HomeAssistant, patch: dict) -> dict:
    """Past een (partiele) patch toe op de config-entry options van alle entries.

    Onbekende sleutels worden genegeerd (met een warning) i.p.v. de hele patch af
    te wijzen - zo blijft een oudere app-versie met een paar extra/oude velden
    werken. Geeft de nieuwe, volledige config terug.

    Belangrijk: de app stuurt bij elke lokale save haar VOLLEDIGE huidige
    instellingen mee (geen echte diff), inclusief `null` voor velden die de app
    zelf nog niet kent/heeft opgehaald. Als je zo'n veld net via HA's eigen
    Configureren-scherm hebt ingesteld, zou een `null` uit de app dat anders
    stilletjes overschrijven - `null`-waarden worden daarom hier genegeerd
    (behandeld als "dit veld niet aanraken").
    """
    unknown = [k for k in patch if k not in CONFIG_FIELDS]
    if unknown:
        _LOGGER.warning("Onbekende config-velden genegeerd in /config patch: %s", unknown)

    known_patch = {k: v for k, v in patch.items() if k in CONFIG_FIELDS and v is not None}

    entries = hass.config_entries.async_entries(DOMAIN)
    for entry in entries:
        new_options = {**entry.options, **known_patch}
        hass.config_entries.async_update_entry(entry, options=new_options)

    return serialize_config(hass)
