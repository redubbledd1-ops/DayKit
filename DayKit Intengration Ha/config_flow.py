"""Config flow (setup-wizard) voor AgendaAlarm Backup."""
from __future__ import annotations

import base64
import json
from typing import Any

import voluptuous as vol

from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.helpers import selector
from homeassistant.helpers.network import NoURLAvailableError, get_url

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
    DEFAULT_SPEAKER_MODE,
    DEFAULT_VOLUME,
    DOMAIN,
)
from .pairing import get_pairing_store
from .qr import render_qr_png

# Zelfde opties als Android's ExternalSpeakerMode enum - bewust letterlijk gelijk gehouden
# (zie config_sync.py / HomeAssistantRepository.kt) zodat er geen naam-mapping nodig is.
SPEAKER_MODE_OPTIONS = ["DISABLED", "DEFAULT", "BACKUP_ONLY", "BOTH"]

# Labels voor het keuzemenu van AgendaAlarmBackupOptionsFlow.async_step_init - er is geen
# strings.json/translations-laag in deze integratie, dus dit is de simpelste manier om
# leesbare menu-namen te tonen (async_show_menu ondersteunt een {step_id: label} dict
# rechtstreeks, zie homeassistant/data_entry_flow.py). Bewust Engels, net als de rest van
# de integratie se UI-teksten - de Android-app heeft een eigen, aan de gebruikerstaal
# gekoppeld 18-talen-systeem (LanguageManager.kt) dat hier los van staat.
SECTION_MENU_OPTIONS = {
    "entities": "Entities",
    "speaker": "Speaker",
    "presence": "Presence",
    "out_of_bed": "Out of bed",
    "scripts": "Scripts",
    "defaults": "Defaults",
}


def _clean(user_input: dict[str, Any]) -> dict[str, Any]:
    """Lege optionele velden niet als lege string opslaan (zelfde gedrag als voorheen)."""
    return {k: (v if v not in ("", None) else None) for k, v in user_input.items()}


def _entity_field(key: str, current_value: Any) -> vol.Optional:
    """vol.Optional-marker voor een los entity-selector-veld, zonder default als er nog
    geen waarde is.

    HA's entity-selector accepteert None/"" niet als waarde - een `default=None` of
    `default=""` laat voluptuous dat direct als validatiefout tonen zodra het formulier
    opent, nog vóórdat de gebruiker iets heeft aangeraakt (bug uit vorige versie: elk leeg
    entity-veld toonde meteen "Entity None is neither a valid entity ID nor a valid
    UUID"). Zonder default blijft het veld leeg totdat de gebruiker zelf iets kiest, geen
    foutmelding.
    """
    if current_value:
        return vol.Optional(key, default=current_value)
    return vol.Optional(key)


def _merge_entities(current_options: dict[str, Any], *entity_ids: str | None) -> list[str]:
    """Union van de bestaande CONF_ENTITIES-lijst met de gegeven entity id's.

    Wordt na elke sectie met een entity-referentieveld (Luidspreker/Aanwezigheid/Uit bed/
    Scripts) aangeroepen, zodat een nieuw gekozen entiteit automatisch in de gedeelde lijst
    terechtkomt. Nooit iets weglaten - een lege/None entity_id wordt genegeerd, en niets
    wordt hier ooit verwijderd (bewuste verwijdering gebeurt alleen via de Entiteiten-sectie
    zelf of vanuit de app, zie HaSettingsViewModel.kt's lastConfirmedEntities-reconciliatie).
    """
    merged = list(current_options.get(CONF_ENTITIES) or [])
    for entity_id in entity_ids:
        if entity_id and entity_id not in merged:
            merged.append(entity_id)
    return merged


class AgendaAlarmBackupConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Handelt de eerste setup af (via HA UI: Instellingen > Apparaten & diensten > Integratie toevoegen)."""

    VERSION = 1

    async def async_step_user(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        # Toont meteen een koppelcode in dit eerste scherm, i.p.v. de gebruiker na het
        # toevoegen apart naar het apparaat te laten gaan om op "Generate pairing code" te
        # drukken - de code komt uit dezelfde gedeelde PairingStore (pairing.py) als die
        # knop, en bestaat onafhankelijk van een config entry, dus prima bruikbaar
        # vóórdat de entry er is. De entry zelf wordt pas aangemaakt zodra de gebruiker dit
        # scherm bevestigt; speaker/aanwezigheid/scripts/entiteiten blijven achteraf via
        # "Configureren" instelbaar (zie AgendaAlarmBackupOptionsFlow hieronder). Zonder
        # koppeling nu al klaar te zetten via de app kan dat trouwens ook later alsnog
        # vanaf de apparaatpagina ("Generate pairing code"-knop, zie button.py).
        if user_input is not None:
            return self.async_create_entry(title="AgendaAlarm Backup", data={})

        code = get_pairing_store(self.hass).generate_code()

        # QR-afbeelding is puur cosmetisch bovenop de tekstcode hierboven (zelfde
        # payload/beveiligingsmodel als sensor.agendaalarm_koppelcode's qr_payload en
        # camera.py's PairingQrCamera, zie qr.py) - geen bereikbare base_url betekent
        # gewoon een lege qr_data_uri, de tekstcode in strings.json blijft dan de
        # werkende fallback i.p.v. het scherm te laten crashen.
        qr_data_uri = ""
        try:
            base_url = get_url(self.hass, prefer_external=False, allow_internal=True)
        except NoURLAvailableError:
            base_url = None
        if base_url:
            payload = json.dumps({"base_url": base_url, "code": code})
            png_bytes = await self.hass.async_add_executor_job(render_qr_png, payload)
            b64 = base64.b64encode(png_bytes).decode("ascii")
            qr_data_uri = f"data:image/png;base64,{b64}"

        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema({}),
            description_placeholders={"code": code, "qr_data_uri": qr_data_uri},
        )

    @staticmethod
    @callback
    def async_get_options_flow(
        config_entry: config_entries.ConfigEntry,
    ) -> AgendaAlarmBackupOptionsFlow:
        return AgendaAlarmBackupOptionsFlow()


class AgendaAlarmBackupOptionsFlow(config_entries.OptionsFlow):
    """Laat instellingen achteraf aanpassen (Instellingen > Integraties > Configureren),
    opgesplitst per onderdeel achter een keuzemenu i.p.v. één reuzenformulier.

    Dit is ook precies de data die via POST /api/agendaalarm_backup/config door de app
    overschreven wordt (zie config_sync.py) - de app is bewust leidend, dus een wijziging
    hier kan door de eerstvolgende app-sync weer overschreven worden. Bedoeld voor
    diagnose/handmatig ingrijpen wanneer de app zelf niet bereikbaar is, niet als
    dagelijkse manier om instellingen te beheren.

    Let op: sinds HA 2024.12 wordt `self.config_entry` automatisch door de basisklasse
    gezet (en de oude `def __init__(self, config_entry): self.config_entry = config_entry`
    is in HA 2025.12 zelfs verwijderd/breekt) - vandaar geen eigen __init__ hier, en
    `async_get_options_flow` hierboven roept deze klasse zonder argumenten aan.

    Elke sectie persisteert zichzelf direct via `hass.config_entries.async_update_entry`
    en gaat daarna terug naar het keuzemenu (`async_step_init`) i.p.v. de flow af te sluiten
    via `async_create_entry` - zo ga je nooit een net opgeslagen sectie kwijt als je het
    dialoog meteen daarna sluit, en kan je in één "Configureren"-sessie meerdere secties na
    elkaar aanpassen zonder dat eerdere secties overschreven worden.
    """

    def _current_options(self) -> dict[str, Any]:
        return {**self.config_entry.data, **self.config_entry.options}

    def _persist(self, patch: dict[str, Any]) -> None:
        """Merge patch in de volledige bestaande config en persisteer meteen naar options.

        Baseert op `_current_options()` (entry.data + entry.options), niet alleen
        entry.options: entries die zijn aangemaakt vóór het opsplitsen van deze flow
        hebben hun hele config nog in entry.data staan (de oude async_step_user schreef
        alles naar `data=`) - zonder deze merge zou de eerste sectie die je opslaat al
        die nog-niet-gemigreerde velden stilletjes laten vallen.
        """
        new_options = {**self._current_options(), **patch}
        self.hass.config_entries.async_update_entry(self.config_entry, options=new_options)

    async def async_step_init(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        return self.async_show_menu(
            step_id="init",
            menu_options=SECTION_MENU_OPTIONS,
        )

    async def async_step_entities(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        if user_input is not None:
            self._persist({CONF_ENTITIES: user_input.get(CONF_ENTITIES) or []})
            return await self.async_step_init()

        current = self._current_options()
        schema = vol.Schema(
            {
                vol.Optional(
                    CONF_ENTITIES, default=current.get(CONF_ENTITIES, [])
                ): selector.selector({"entity": {"multiple": True}}),
            }
        )
        return self.async_show_form(step_id="entities", data_schema=schema)

    async def async_step_speaker(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        if user_input is not None:
            cleaned = _clean(user_input)
            cleaned[CONF_ENTITIES] = _merge_entities(
                self._current_options(), cleaned.get(CONF_SPEAKER_ENTITY)
            )
            self._persist(cleaned)
            return await self.async_step_init()

        current = self._current_options()
        schema = vol.Schema(
            {
                _entity_field(
                    CONF_SPEAKER_ENTITY, current.get(CONF_SPEAKER_ENTITY)
                ): selector.selector({"entity": {"domain": "media_player"}}),
                vol.Optional(
                    CONF_SPEAKER_MODE,
                    default=current.get(CONF_SPEAKER_MODE, DEFAULT_SPEAKER_MODE),
                ): selector.selector(
                    {"select": {"options": SPEAKER_MODE_OPTIONS, "mode": "dropdown"}}
                ),
            }
        )
        return self.async_show_form(step_id="speaker", data_schema=schema)

    async def async_step_presence(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        if user_input is not None:
            cleaned = _clean(user_input)
            cleaned[CONF_ENTITIES] = _merge_entities(
                self._current_options(), cleaned.get(CONF_PRESENCE_ENTITY)
            )
            self._persist(cleaned)
            return await self.async_step_init()

        current = self._current_options()
        schema = vol.Schema(
            {
                _entity_field(
                    CONF_PRESENCE_ENTITY, current.get(CONF_PRESENCE_ENTITY)
                ): selector.selector(
                    # person./device_tracker. = state "home"; binary_sensor. (bv. een
                    # ping- of netwerk-aanwezigheidssensor) = state "on". Beide worden
                    # ondersteund door AlarmBackupHub.is_user_home().
                    {"entity": {"domain": ["person", "device_tracker", "binary_sensor"]}}
                ),
                vol.Optional(
                    CONF_PRESENCE_EXPECTED_STATE,
                    default=current.get(
                        CONF_PRESENCE_EXPECTED_STATE, DEFAULT_PRESENCE_EXPECTED_STATE
                    ),
                ): selector.selector(
                    # Preset dekt person./device_tracker. ("home"/"not_home") en
                    # binary_sensor. ("on"/"off") - custom_value: True omdat
                    # presence_entity_id geen strikt 2-optie-boolean is (afhankelijk van
                    # het gekozen entiteit-domein), dus een afwijkende waarde moet vrij
                    # te typen blijven i.p.v. te crashen/resetten.
                    {
                        "select": {
                            "options": ["home", "not_home", "on", "off"],
                            "mode": "dropdown",
                            "custom_value": True,
                        }
                    }
                ),
            }
        )
        return self.async_show_form(step_id="presence", data_schema=schema)

    async def async_step_out_of_bed(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        if user_input is not None:
            cleaned = _clean(user_input)
            cleaned[CONF_ENTITIES] = _merge_entities(
                self._current_options(), cleaned.get(CONF_OUT_OF_BED_ENTITY)
            )
            self._persist(cleaned)
            return await self.async_step_init()

        current = self._current_options()
        schema = vol.Schema(
            {
                vol.Optional(
                    CONF_OUT_OF_BED_ENABLED,
                    default=current.get(CONF_OUT_OF_BED_ENABLED, DEFAULT_OUT_OF_BED_ENABLED),
                ): bool,
                _entity_field(
                    CONF_OUT_OF_BED_ENTITY, current.get(CONF_OUT_OF_BED_ENTITY)
                ): selector.selector({"entity": {}}),
                vol.Optional(
                    CONF_OUT_OF_BED_EXPECTED_VALUE,
                    default=current.get(
                        CONF_OUT_OF_BED_EXPECTED_VALUE, DEFAULT_OUT_OF_BED_EXPECTED_VALUE
                    ),
                ): selector.selector(
                    # custom_value: True omdat out_of_bed_entity_id geen domeinrestrictie
                    # heeft (selector.selector({"entity": {}}) hierboven) - in theorie dus
                    # ook een sensor met een andere tekst-state dan on/off, die moet vrij
                    # te typen blijven i.p.v. te crashen/resetten.
                    {
                        "select": {
                            "options": ["on", "off"],
                            "mode": "dropdown",
                            "custom_value": True,
                        }
                    }
                ),
            }
        )
        return self.async_show_form(step_id="out_of_bed", data_schema=schema)

    async def async_step_scripts(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        if user_input is not None:
            cleaned = _clean(user_input)
            cleaned[CONF_ENTITIES] = _merge_entities(
                self._current_options(),
                cleaned.get(CONF_ALARM_SCRIPT_ENTITY),
                cleaned.get(CONF_TIMER_SCRIPT_ENTITY),
            )
            self._persist(cleaned)
            return await self.async_step_init()

        current = self._current_options()
        schema = vol.Schema(
            {
                _entity_field(
                    CONF_ALARM_SCRIPT_ENTITY, current.get(CONF_ALARM_SCRIPT_ENTITY)
                ): selector.selector({"entity": {"domain": "script"}}),
                vol.Optional(
                    CONF_ALARM_SCRIPT_ENABLED,
                    default=current.get(CONF_ALARM_SCRIPT_ENABLED, False),
                ): bool,
                vol.Optional(
                    CONF_ALARM_SCRIPT_IGNORE_PRESENCE,
                    default=current.get(CONF_ALARM_SCRIPT_IGNORE_PRESENCE, True),
                ): bool,
                _entity_field(
                    CONF_TIMER_SCRIPT_ENTITY, current.get(CONF_TIMER_SCRIPT_ENTITY)
                ): selector.selector({"entity": {"domain": "script"}}),
                vol.Optional(
                    CONF_TIMER_SCRIPT_ENABLED,
                    default=current.get(CONF_TIMER_SCRIPT_ENABLED, False),
                ): bool,
                vol.Optional(
                    CONF_TIMER_SCRIPT_IGNORE_PRESENCE,
                    default=current.get(CONF_TIMER_SCRIPT_IGNORE_PRESENCE, True),
                ): bool,
            }
        )
        return self.async_show_form(step_id="scripts", data_schema=schema)

    async def async_step_defaults(
        self, user_input: dict[str, Any] | None = None
    ) -> config_entries.FlowResult:
        if user_input is not None:
            self._persist(_clean(user_input))
            return await self.async_step_init()

        current = self._current_options()
        schema = vol.Schema(
            {
                vol.Optional(
                    CONF_NOTIFY_SERVICE, default=current.get(CONF_NOTIFY_SERVICE, "")
                ): str,
                vol.Optional(
                    CONF_DEFAULT_VOLUME, default=current.get(CONF_DEFAULT_VOLUME, DEFAULT_VOLUME)
                ): selector.selector({"number": {"min": 0, "max": 100, "mode": "slider"}}),
                vol.Optional(
                    CONF_DEFAULT_INTERVAL,
                    default=current.get(CONF_DEFAULT_INTERVAL, DEFAULT_INTERVAL),
                ): selector.selector({"number": {"min": 1, "max": 600, "mode": "box"}}),
                vol.Optional(
                    CONF_DEFAULT_SOUND_URL,
                    # Bewust leeg als er nog niks opgeslagen is - anders staat de hardcoded
                    # DEFAULT_SOUND_URL vooringevuld en wordt 'm bij Verzenden zonder het veld
                    # aan te raken alsnog opgeslagen, alsof de gebruiker 'm bewust koos. Het
                    # runtime-gedrag (AlarmBackupHub.__init__ in __init__.py) valt zelf al
                    # terug op "" als er niks is opgeslagen, dus dit forced-default in het
                    # formulier was overbodig én misleidend.
                    default=current.get(CONF_DEFAULT_SOUND_URL, ""),
                ): str,
                vol.Optional(
                    CONF_SAFETY_TIMEOUT,
                    default=current.get(CONF_SAFETY_TIMEOUT, DEFAULT_SAFETY_TIMEOUT),
                ): selector.selector({"number": {"min": 10, "max": 3600, "mode": "box"}}),
            }
        )
        return self.async_show_form(step_id="defaults", data_schema=schema)
