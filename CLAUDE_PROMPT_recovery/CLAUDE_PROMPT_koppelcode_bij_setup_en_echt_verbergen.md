# Koppelcode direct bij "Integratie toevoegen" + koppel-entiteiten écht laten verdwijnen

## Context

Twee dingen die de gebruiker wil:

1. Bij "Instellingen > Apparaten & diensten > Integratie toevoegen" meteen een
   koppelcode laten zien, i.p.v. dat de gebruiker na het toevoegen apart naar
   het apparaat moet om op "Generate pairing code" te drukken.
2. De koppel-gerelateerde entiteiten (koppel-knop, koppelcode-sensor, QR-camera)
   moeten na een geslaagde koppeling ECHT verdwijnen - ook van de
   apparaat-instellingenpagina (Instellingen > Apparaten & diensten > dit
   apparaat), niet alleen van het auto-dashboard.

**Belangrijk, uitleg over punt 2:** de vorige aanpak gebruikte `hidden_by` via
`async_sync_pairing_visibility()` in `__init__.py`. Dat werkt correct voor het
auto-gegenereerde Overview-dashboard, maar de apparaat-instellingenpagina toont
altijd ALLE entiteiten van een apparaat, verborgen of niet (standaard HA-gedrag,
niet te beïnvloeden via `hidden_by`). Om ze daar ook echt weg te krijgen is
maar één manier: de entiteiten daadwerkelijk conditioneel aanmaken (niet
registreren als niet relevant) EN, bij het omslaan van de koppel-status, de nu
niet-relevante registry-records expliciet verwijderen. Vervang
`async_sync_pairing_visibility`/de `hidden_by`-aanpak dus door onderstaande
conditionele aanpak - dat is preciezer wat hier gevraagd wordt.

**Bekende valkuil (nogmaals, zie eerdere `GebruikerThuisSwitch`-bug):**
conditioneel entiteiten aanmaken zonder de niet-meer-relevante registry-records
expliciet op te ruimen laat wees-entiteiten achter. Dit prompt lost dat af
door bij elke omslag van `is_paired` de niet-relevante set expliciet te
verwijderen via `entity_registry.async_remove()`, gecombineerd met
platform-`async_setup_entry`'s die voortaan conditioneel beslissen wat ze
toevoegen.

## Deel 1: koppelcode tonen tijdens "Integratie toevoegen"

In `config_flow.py`'s `AgendaAlarmBackupConfigFlow.async_step_user`: toon, in
plaats van meteen `async_create_entry`, eerst een stap met de koppelcode erin,
en maak de entry pas aan zodra de gebruiker die stap bevestigt (bv. op
"Voltooien" drukt - geen velden nodig, alleen een submit-knop). De code komt uit
dezelfde gedeelde `PairingStore` als de "Generate pairing code"-knop
(`pairing.py`'s `get_pairing_store`) - die bestaat al onafhankelijk van een
config entry, dus prima bruikbaar vóórdat de entry er is.

```python
async def async_step_user(
    self, user_input: dict[str, Any] | None = None
) -> config_entries.FlowResult:
    if user_input is not None:
        return self.async_create_entry(title="AgendaAlarm Backup", data={})

    from .pairing import get_pairing_store
    code = get_pairing_store(self.hass).generate_code()
    return self.async_show_form(
        step_id="user",
        data_schema=vol.Schema({}),
        description_placeholders={"code": code},
    )
```

Voeg een `strings.json` (en evt. `translations/en.json`, kopieer dan hetzelfde)
toe in deze map met een beschrijving voor deze stap die `{code}` gebruikt, bv.:

```json
{
  "config": {
    "step": {
      "user": {
        "title": "Pair AgendaAlarm",
        "description": "Open the AgendaAlarm app, go to Settings > Pair, and enter this code: {code}\n\nValid for 5 minutes. You can also finish setup and pair later from the device page if you're not ready to open the app right now."
      }
    }
  }
}
```

Zonder `strings.json` toont HA geen tekst voor `description_placeholders` - dus
dit bestand is niet optioneel voor dit stuk.

## Deel 2: koppel-/ontkoppel-entiteiten écht conditioneel

**In `button.py`, `sensor.py`, `camera.py`'s `async_setup_entry`:** voeg
`GeneratePairingCodeButton`/`PairingCodeSensor`/`PairingQrCamera` alléén toe als
`not hub.is_paired`; voeg `UnpairButton` alléén toe als `hub.is_paired`. (De
altijd-zichtbare `PairedSensor`/"Paired"-sensor blijft ongewijzigd, die hoort
juist bij beide statussen zichtbaar te zijn.)

**In `__init__.py`:**
- Verwijder `async_sync_pairing_visibility()` en de `hidden_by`-aanroepen ervan
  (in `async_setup_entry` en in `async_set_paired`) - vervangen door
  onderstaande.
- Hergebruik de bestaande `_PAIRING_FLOW_ENTITIES`/`_UNPAIR_ENTITIES`-lijsten
  (domain, key)-paren voor de registry-opschoning.
- Pas `async_set_paired` aan:

```python
async def async_set_paired(self, paired: bool) -> None:
    """Zet de koppel-status, persisteert 'm in entry.data, en verwijdert de
    entiteiten die bij de NIEUWE status niet meer relevant zijn (echte
    registry-removal, niet hidden_by - dat laat namelijk de apparaat-
    instellingenpagina ongemoeid, zie de uitleg boven in dit bestand/prompt).
    De relevante set voor de nieuwe status wordt zo meteen weer aangemaakt door
    button.py/sensor.py/camera.py's async_setup_entry, die nu conditioneel op
    is_paired beslissen - getriggerd door de reload die de bestaande
    update-listener (_async_options_updated) toch al start zodra
    async_update_entry hieronder de entry bijwerkt. GEEN extra expliciete
    async_reload-aanroep hier toevoegen: dat zou een dubbele/racende reload
    geven bovenop die automatische."""
    self.is_paired = paired
    if paired:
        self.pairing_code = None

    registry = er.async_get(self.hass)
    now_irrelevant = _UNPAIR_ENTITIES if not paired else _PAIRING_FLOW_ENTITIES
    for domain, key in now_irrelevant:
        entity_id = registry.async_get_entity_id(domain, DOMAIN, f"{self.entry.entry_id}_{key}")
        if entity_id:
            registry.async_remove(entity_id)

    self.hass.config_entries.async_update_entry(
        self.entry, data={**self.entry.data, DATA_IS_PAIRED: paired}
    )
    self._notify_update()
```

- Verwijder ook de aanroep van `async_sync_pairing_visibility(hass, entry,
  hub.is_paired)` in `async_setup_entry` (na `async_forward_entry_setups`) - dat
  is niet meer nodig, want de platforms beslissen nu zelf via `hub.is_paired`
  wat ze toevoegen; er hoeft niks meer achteraf verborgen te worden.

## Oplevercriteria

- Bij "Integratie toevoegen": eerste scherm toont meteen een koppelcode +
  instructie, geen apart bezoek aan het apparaat nodig om te beginnen met
  koppelen.
- Vers/niet-gekoppeld: apparaat-instellingenpagina toont "Generate pairing
  code", "Pairing code"-sensor, "Pairing QR"-camera; GEEN "Unpair"-knop.
- Na een geslaagde koppeling: diezelfde pagina toont die drie NIET meer (echt
  verdwenen, niet alleen verborgen) en toont in plaats daarvan de "Unpair"-knop.
- Na ontkoppelen: precies omgekeerd, en een verse koppelcode is weer meteen
  beschikbaar (via de net weer verschenen "Generate pairing code"-knop, of door
  de integratie via "Herconfigureren" opnieuw te doorlopen).
- Geen wees-entiteiten: elke overgang (paired->unpaired en andersom) laat geen
  "entity not provided by integration"-records achter - test dit expliciet door
  een paar keer te koppelen/ontkoppelen en de entity-registry (Instellingen >
  Apparaten & diensten > Entiteiten, filter op deze integratie) te controleren.
- `Watchdog status`/`Next alarm`/andere niet-koppel-gerelateerde entiteiten
  blijven ongewijzigd zichtbaar en werkend gedurende deze wijziging.
