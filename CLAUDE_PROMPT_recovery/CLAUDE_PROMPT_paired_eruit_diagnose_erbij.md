# Paired-tracking volledig verwijderen + diagnose-sensor voor binnenkomende aanroepen

## Context

`is_paired` (`PairedSensor`, `UnpairButton`, conditionele entity-creatie/removal
in button.py/sensor.py/camera.py, `DATA_IS_PAIRED` in entry.data) is nooit
betrouwbaar geweest en - belangrijker - **gate't functioneel helemaal niks**:
`AgendaAlarmEventView`/`AgendaAlarmConfigView` (`http_views.py`) hebben
`requires_auth = True`, wat door HA's eigen ingebouwde tokencheck wordt
afgehandeld, volledig los van onze `is_paired`-vlag. Er is dus geen enkele
functionele reden om dit bij te houden - het was puur cosmetisch (welke knop je
ziet) en heeft alleen maar voor verwarring/valse status gezorgd. Volledig
verwijderen.

**Wat WEL blijft:** de koppelcode/QR-uitwisseling zelf (`pairing.py`'s
`PairingStore`/`async_create_long_lived_token`, `/pair`-endpoint in
`http_views.py`, `GeneratePairingCodeButton`, `PairingCodeSensor`,
`PairingQrCamera`) - dat werkt aantoonbaar (token wordt succesvol uitgewisseld)
en blijft de manier om de app aan een token te helpen. Alleen de
"is_paired True/False, welke entiteiten daarom wel/niet bestaan"-laag erbovenop
gaat weg.

Ook toevoegen: een diagnose-sensor die toont wat de LAATSTE binnenkomende,
geauthenticeerde aanroep op `/api/agendaalarm_backup/event` was (actie +
tijdstip + ruwe payload), ongeacht of 'm slaagde - zodat bij het volgende
testen meteen duidelijk is of de app HA daadwerkelijk bereikt, zonder verder te
hoeven gokken.

## Deel 1: is_paired volledig weg

**`__init__.py`:**
- Verwijder `is_paired`-attribuut, `_apply_paired_state`, `async_set_paired`,
  `DATA_IS_PAIRED`-gebruik, `_PAIRING_FLOW_ENTITIES`/`_UNPAIR_ENTITIES` en alle
  registry-removal-logica die daarop draait.
- `AgendaAlarmPairView.post` (`http_views.py`) hoeft na een geslaagde
  token-aanmaak niks meer bij te werken op de hub - gewoon het token
  teruggeven, klaar.

**`const.py`:** verwijder `DATA_IS_PAIRED`, `ENTITY_KEY_UNPAIR`,
`ENTITY_KEY_PAIRED_SENSOR` (niet meer gebruikt).

**`button.py`:** verwijder `UnpairButton` volledig. `GeneratePairingCodeButton`
blijft, onvoorwaardelijk (was al onvoorwaardelijk, dat hoeft niet te
veranderen).

**`sensor.py`:** verwijder `PairedSensor`. `PairingCodeSensor` en
`PairingQrCamera` (camera.py) blijven zoals ze zijn (onvoorwaardelijk aanwezig,
tonen "No active code"/geen afbeelding als er niks actiefs is - dat gedrag was
al goed en hoeft niet te wijzigen).

**`camera.py`:** de `if self.hub.is_paired: return None`-tak in
`async_camera_image` wordt: gewoon altijd een nieuwe code genereren zodra de
huidige verlopen/afwezig is (het oorspronkelijke, simpele gedrag vóór de hele
is_paired-toevoeging) - dus die is_paired-check er gewoon uit, terug naar
onvoorwaardelijk `self.hub.generate_pairing_code()` aanroepen wanneer er geen
geldige code is.

**`pairing.py`:** `async_revoke_long_lived_token` mag blijven bestaan (nuttige,
op zichzelf staande functie om een token in te trekken) maar wordt nergens meer
automatisch aangeroepen nu `UnpairButton` weg is - dat is prima, gewoon een
ongebruikte utility-functie laten staan, of verwijderen als je liever opschoont
(maakt functioneel niet uit).

## Deel 2: diagnose-sensor voor binnenkomende aanroepen

**`__init__.py`'s `AlarmBackupHub`:** nieuwe attributen
`self.last_event_received_at: datetime | None = None` en
`self.last_event_payload: dict | None = None`. Nieuwe methode
`def record_incoming_event(self, action: str, payload: dict) -> None` die deze
twee velden zet (`last_event_received_at = dt_util.utcnow()`,
`last_event_payload = {"action": action, **payload}` of vergelijkbaar) en
`self._notify_update()` aanroept.

**`http_views.py`'s `AgendaAlarmEventView.post`:** roep
`hub.record_incoming_event(action, data)` aan voor elke hub uit `_hubs(hass)`,
zodra de JSON succesvol geparsed is (dus vóór de `if action == "arm": ...`-tak),
ongeacht welke actie het is of of de rest van de verwerking slaagt - dit moet
dus ook geregistreerd worden als er later in de functie een 400/500 terugkomt.

**`sensor.py`:** nieuwe diagnostic sensor `LastEventSensor`
(`_attr_entity_category = EntityCategory.DIAGNOSTIC`, naam "Last event
received"): `native_value` = `hub.last_event_received_at` (device_class
TIMESTAMP, zelfde patroon als `VolgendAlarmSensor`), `extra_state_attributes`
= `{"payload": hub.last_event_payload}`. Toevoegen aan de
`async_add_entities([...])`-lijst.

## Oplevercriteria

- `is_paired`/"Paired"-sensor/"Unpair"-knop bestaan nergens meer in de
  integratie.
- Koppelen via QR/code werkt nog steeds (token-uitwisseling ongewijzigd).
- Nieuwe "Last event received"-sensor toont na een arm/start/stop/disarm-actie
  vanuit de app meteen een vers tijdstip + de ruwe payload, ook als die actie
  verder faalt - dit is het eerste waar je naar kijkt bij de volgende test om
  te zien of de app HA daadwerkelijk bereikt.
- Geen wees-entiteiten: `PairedSensor`/`UnpairButton` waren onvoorwaardelijk of
  voorwaardelijk geregistreerd geweest - check dat een eventueel bestaand
  registry-record voor deze unique_id's opgeruimd wordt (zelfde
  orphan-cleanup-patroon als `_async_remove_orphaned_gebruiker_thuis_switch` in
  switch.py), zodat er geen "unavailable, not provided by integration"-restjes
  achterblijven.
