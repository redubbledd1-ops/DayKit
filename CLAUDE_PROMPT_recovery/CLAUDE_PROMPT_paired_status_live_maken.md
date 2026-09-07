# "Paired"-status echt live maken (niet alleen een eenmalige vlag)

## Context

`hub.is_paired` wordt nu alleen gezet op de twee momenten dat we het zelf
expliciet aanroepen: `True` bij een geslaagde `/pair`-uitwisseling, `False` bij
een druk op de "Unpair"-knop. Dat is niet hetzelfde als "is de app nu écht
verbonden": als het token op een andere manier verdwijnt (bv. de gebruiker
trekt 'm handmatig in via HA's eigen Profiel > Long-Lived Access Tokens, in
plaats van via onze Unpair-knop) blijft `is_paired` voor altijd `True` staan,
ook al werkt de koppeling niet meer. Omgekeerd zegt een eenmalige `True` ook
niks over of de app recent nog echt iets heeft laten horen.

Doel: `is_paired` (en dus de "Paired"-sensor) moet de daadwerkelijke,
actuele geldigheid van het token weerspiegelen, en zichzelf corrigeren zodra
die geldigheid verandert - niet alleen reageren op onze eigen twee expliciete
triggers.

Relevante bestanden: `__init__.py` (`AlarmBackupHub`), `pairing.py`
(`CLIENT_NAME`, `TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN` - dezelfde matching als
`async_create_long_lived_token`/`async_revoke_long_lived_token` al gebruiken),
`http_views.py` (`AgendaAlarmEventView`, `AgendaAlarmConfigView` - de
endpoints die de app bij elke normale aanroep al raakt).

## Wat te doen

**1. Live check-functie op de hub.**
Nieuwe methode `async def async_sync_paired_from_token(self) -> None` op
`AlarmBackupHub`: controleert of er daadwerkelijk nog een geldig refresh-token
bestaat dat bij deze koppeling hoort (zelfde `CLIENT_NAME`/
`TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN`-matching als `pairing.py`'s
`async_create_long_lived_token`/`async_revoke_long_lived_token` al gebruiken -
haal de owner op via `hass.auth.async_get_users()` en zoek naar een
`refresh_tokens`-entry met die client_name). Als de uitkomst afwijkt van
`self.is_paired`: werk `self.is_paired` bij, persisteer in `entry.data` (zelfde
manier als in `async_set_paired`), pas de entiteiten-zichtbaarheid aan (dezelfde
toevoeg/verwijder-logica als in `async_set_paired`), en roep `_notify_update()`
aan. Overweeg deze gedeelde logica te hergebruiken tussen `async_set_paired` en
deze nieuwe methode i.p.v. te dupliceren (bv. door `async_set_paired` deze
nieuwe methode intern te laten aanroepen na het zetten van de gewenste waarde,
of een gedeelde private helper `_apply_paired_state(paired: bool)`).

**2. Aanroepen bij elke echte app-aanroep (opportunistisch, "er is nu echt
verbinding").**
In `http_views.py`: roep `await hub.async_sync_paired_from_token()` aan het
begin van `AgendaAlarmEventView.post` en `AgendaAlarmConfigView.get`/`.post`
(vóór de eigenlijke logica) voor elke hub uit `_hubs(hass)` - dit zijn de
endpoints die de app sowieso al bij elke normale actie raakt (alarm
wapenen/rapporteren, instellingen ophalen/opslaan), dus dit is de goedkoopste
plek om "er is nu een echt werkende, geauthenticeerde aanroep binnengekomen"
te detecteren zonder apart verkeer te hoeven opwekken.

**3. Periodieke controle (vangt "token elders ingetrokken, app belt toevallig
niet" op).**
In `AlarmBackupHub.__init__`/`async_setup_entry`: zet een periodieke check op
via `async_track_time_interval` (bv. elke 30 minuten, vergelijkbare frequentie
als de bestaande app-side periodieke sync) die `async_sync_paired_from_token()`
aanroept. Zorg dat deze timer netjes opgeruimd wordt in `async_unload_entry`
(zelfde patroon als de bestaande `_watchdog_unsub`/`_loop_unsub`/
`_timeout_unsub`-opruiming).

## Oplevercriteria

- Token intrekken via HA's eigen Profiel > Long-Lived Access Tokens (dus NIET
  via onze Unpair-knop): "Paired" valt vanzelf terug naar `False` zodra de app
  weer een normale aanroep doet, of uiterlijk na de periodieke check.
- Een geslaagde `/pair`-uitwisseling of druk op "Unpair" blijft meteen (niet
  pas bij de volgende app-aanroep/timer) het juiste resultaat tonen - dat
  gedrag van `async_set_paired` blijft ongewijzigd/behouden.
- Geen dubbele/conflicterende logica tussen `async_set_paired` en
  `async_sync_paired_from_token` - hergebruik dezelfde
  toevoeg/verwijder-entiteiten-stap.
- De periodieke timer wordt netjes gecanceld bij unload/reload (geen
  weggelekte timer die na een reload dubbel blijft lopen).
