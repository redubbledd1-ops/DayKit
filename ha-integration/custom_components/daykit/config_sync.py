"""Config-sync tussen Home Assistant en de DayKit-app.

De APP blijft de bron van waarheid: zij schrijft haar lokale instellingen naar HA via
POST /api/daykit/config bij elke lokale save (en bij elke (her)koppeling), zie
HaSettingsViewModel.kt's saveSettings()/applyHomeAssistantSettings(). HA's config-entry
`options` is het doelwit dat daarbij bijgewerkt wordt.

HA kan wél degelijk wijzigingen aandragen, alleen nooit stilzwijgend. Vóór elke terug-push
haalt de app de volledige config hier op (GET, serialize_config hieronder), vergelijkt die met
haar eigen staat en legt elk verschil aan de gebruiker voor in een "HA-update gevonden"-melding
(zie HaSettingsViewModel.kt's performHaEntityUpdateCheck/buildConfigFieldDiffs). Pas na een
bevestiging wordt iets overgenomen. Dat is bewust géén automatische twee-richtings-sync: die
overschreef vroeger bij elke app-start de instellingen van de gebruiker met HA's kale defaults
zodra de config-entry nog leeg stond.

Elk veld in CONFIG_FIELDS hieronder is daarmee vanuit beide kanten in te stellen, en komt overeen
met precies één veld in HA's "Configureren"-scherm (config_flow.py, acht secties: Entities,
Alarm/Timer/Weer-speaker, Presence, Out of bed, Scripts, Defaults) én één veld aan de app-kant.
Een nieuw syncbaar veld toevoegen betekent dus: const.py + CONFIG_FIELDS hier + de juiste sectie
in config_flow.py + strings.json/translations + aan de app-kant HaDayKitConfig (parser),
buildConfigPatchJson (push) en buildConfigFieldDiffs (melding).

Let op: dit dekt bewust NIET `battery_usage_per_hour` - dat loopt via een losse, al langer
bestaande `input_number.mobiel_batterij_per_uur`-helper aan de HA-kant en `SettingsManager` aan
de app-kant, zie HaSettingsViewModel.kt's syncBatteryUsageToHomeAssistant.
"""
from __future__ import annotations

import logging
import time

from homeassistant.core import HomeAssistant

from .const import (
    CONF_CONFIG_LAST_MODIFIED,
    CONF_CONFIG_LAST_MODIFIED_BY,
    SOURCE_APP,
    CONF_ALARM_SCRIPT_ENABLED,
    CONF_ALARM_SCRIPT_ENTITY,
    CONF_ALARM_SCRIPT_IGNORE_PRESENCE,
    CONF_DEFAULT_INTERVAL,
    CONF_DEFAULT_SOUND_URL,
    CONF_DEFAULT_VOLUME,
    CONF_SKIP_BACKUP_VOLUME,
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
    CONF_TIMER_SPEAKER_ENTITY,
    CONF_TIMER_SPEAKER_MODE,
    CONF_TIMER_SPEAKER_VOLUME,
    CONF_TIMER_SPEAKER_SKIP_VOLUME,
    CONF_TIMER_SPEAKER_SOUND_URL,
    CONF_WEATHER_SPEAKER_ENTITY,
    CONF_WEATHER_SPEAKER_MODE,
    CONF_WEATHER_SPEAKER_VOLUME,
    CONF_WEATHER_SPEAKER_SKIP_VOLUME,
    CONF_WEATHER_SPEAKER_SOUND_URL,
    CONF_WEATHER_TTS_ENABLED,
    DEFAULT_INTERVAL,
    DEFAULT_OUT_OF_BED_ENABLED,
    DEFAULT_OUT_OF_BED_EXPECTED_VALUE,
    DEFAULT_PRESENCE_EXPECTED_STATE,
    DEFAULT_SAFETY_TIMEOUT,
    DEFAULT_SKIP_BACKUP_VOLUME,
    DEFAULT_SOUND_PATH,
    DEFAULT_SPEAKER_MODE,
    DEFAULT_VOLUME,
    DEFAULT_WEATHER_TTS_ENABLED,
    DOMAIN,
)

_LOGGER = logging.getLogger(__name__)

# Alle velden die via /config gelezen/geschreven mogen worden, met hun default
# als er nog niets ingesteld is. Nieuwe syncbare velden hier + in const.py + in de
# juiste sectie-schema in config_flow.py's DayKitOptionsFlow toevoegen
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
    CONF_SKIP_BACKUP_VOLUME: DEFAULT_SKIP_BACKUP_VOLUME,
    CONF_DEFAULT_INTERVAL: DEFAULT_INTERVAL,
    # None hier (i.p.v. een vaste string): de echte default is de lokaal-gehoste
    # discoAlarmBackupAlarm.mp3, waarvan de absolute URL van de HA-instance afhangt
    # (get_url) - zie serialize_config()'s override hieronder.
    CONF_DEFAULT_SOUND_URL: None,
    CONF_SAFETY_TIMEOUT: DEFAULT_SAFETY_TIMEOUT,
    CONF_ENTITIES: [],
    # Timer- en weer-speaker - eigen naamruimte, los van de Agenda-alarm-watchdogvelden
    # hierboven (CONF_SPEAKER_ENTITY e.a.), zie de toelichting bij deze constanten in const.py.
    CONF_TIMER_SPEAKER_ENTITY: None,
    CONF_TIMER_SPEAKER_MODE: DEFAULT_SPEAKER_MODE,
    CONF_TIMER_SPEAKER_VOLUME: DEFAULT_VOLUME,
    CONF_TIMER_SPEAKER_SKIP_VOLUME: DEFAULT_SKIP_BACKUP_VOLUME,
    # None, geen lokaal-gehoste default zoals CONF_DEFAULT_SOUND_URL: Timer en Weer hebben geen
    # HA-kant fallback die stil zou vallen zonder geluid - "niets ingesteld" is hier een geldige
    # staat (de app gebruikt dan gewoon zijn eigen lokale keuze).
    CONF_TIMER_SPEAKER_SOUND_URL: None,
    CONF_WEATHER_SPEAKER_ENTITY: None,
    CONF_WEATHER_SPEAKER_MODE: DEFAULT_SPEAKER_MODE,
    CONF_WEATHER_SPEAKER_VOLUME: DEFAULT_VOLUME,
    CONF_WEATHER_SPEAKER_SKIP_VOLUME: DEFAULT_SKIP_BACKUP_VOLUME,
    CONF_WEATHER_SPEAKER_SOUND_URL: None,
    CONF_WEATHER_TTS_ENABLED: DEFAULT_WEATHER_TTS_ENABLED,
    # Herkomst-stempel, geen instelling: staat niet in config_flow.py en hoort niet in het
    # Configureren-scherm. Wel in CONFIG_FIELDS, want anders zou serialize_config() het niet
    # teruggeven aan de app en async_apply_config_patch() het als onbekend veld weggooien.
    CONF_CONFIG_LAST_MODIFIED: 0,
    CONF_CONFIG_LAST_MODIFIED_BY: None,
}


def _first_entry(hass: HomeAssistant):
    """Deze integratie gaat er (net als http_views.py's _hubs()) van uit dat er
    normaliter 1 config entry actief is (1 HA-instantie <-> 1 DayKit-app-koppeling)."""
    entries = hass.config_entries.async_entries(DOMAIN)
    return entries[0] if entries else None


def _default_local_sound_url(hass: HomeAssistant) -> str:
    """Absolute URL naar het bij de integratie gebundelde fallback-geluid (zie __init__.py's
    async_setup_entry, dat het bestand naar <config>/www/daykit_sounds/ kopieert).
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

    # Herkomst vastleggen: deze waarden komen van de app. De app leest dit straks terug en weet
    # dan dat de config alleen maar haar eigen push weerspiegelt - geen reden om de gebruiker te
    # vragen of hij een "wijziging in HA" wil overnemen. De tijdstempel is die van de app zelf
    # (meegestuurd in de patch), zodat de app 'm herkent zonder klokken te hoeven vergelijken;
    # ontbreekt hij, dan valt het terug op de HA-klok.
    known_patch[CONF_CONFIG_LAST_MODIFIED_BY] = SOURCE_APP
    if CONF_CONFIG_LAST_MODIFIED not in known_patch:
        known_patch[CONF_CONFIG_LAST_MODIFIED] = int(time.time() * 1000)

    entries = hass.config_entries.async_entries(DOMAIN)
    for entry in entries:
        new_options = {**entry.options, **known_patch}
        hass.config_entries.async_update_entry(entry, options=new_options)

    return serialize_config(hass)
