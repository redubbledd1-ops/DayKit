# Live "Paired"-check terugdraaien (veroorzaakt valse "niet gekoppeld"-status)

## Context

De laatst toegevoegde `async_sync_paired_from_token()`-check (bedoeld om
`is_paired` live te laten kloppen met het daadwerkelijke token-bezit) blijkt
`is_paired` onterecht naar `False` te zetten terwijl de app zelf prima werkt en
gewoon met een geldig token met HA praat - een valse-negatief. Dat cascadeert
vervolgens door: zodra `is_paired` ten onrechte op `False` staat, worden de
koppel-knop/QR weer getoond en de "Unpair"-knop verdwijnt, wat verwarrend is
zolang de daadwerkelijke koppeling gewoon werkt. Niet verder debuggen - gewoon
terugdraaien naar de eenvoudigere, betrouwbaar gebleken opzet: `is_paired`
verandert alleen nog bij de twee expliciete triggers (geslaagde `/pair`, druk op
"Unpair").

Relevante bestanden: `__init__.py` (`AlarmBackupHub`), `pairing.py`
(`async_has_valid_pairing_token`), `http_views.py`
(`AgendaAlarmEventView`/`AgendaAlarmConfigView`).

## Wat te doen

**In `__init__.py`:**
- Verwijder `async_sync_paired_from_token()` en `_periodic_paired_check()`
  volledig.
- Verwijder de periodieke timer die `_periodic_paired_check` opzette
  (`hub._paired_check_unsub = async_track_time_interval(...)`, zowel de
  aanmaak als de bijbehorende opruiming in `async_unload_entry`).
- `_apply_paired_state()` mag blijven bestaan (nuttige gedeelde helper), maar
  wordt dan alleen nog aangeroepen vanuit `async_set_paired()` - dus feitelijk
  terug naar hoe het was vóór de live-check-toevoeging. Als je 'm liever
  helemaal terugvouwt in `async_set_paired()` zelf (geen aparte
  helper-functie meer nodig zonder de tweede aanroeper) is dat ook prima -
  maakt functioneel niks uit.

**In `pairing.py`:**
- Verwijder `async_has_valid_pairing_token()` (niet meer gebruikt na
  bovenstaande).

**In `http_views.py`:**
- Verwijder de `await hub.async_sync_paired_from_token()`-aanroepen aan het
  begin van `AgendaAlarmEventView.post` en `AgendaAlarmConfigView.get`/`.post`.

## Belangrijk voor na het deployen

`is_paired` staat momenteel (door de bug) ten onrechte op `False` terwijl de
koppeling feitelijk werkt. Deze fix corrigeert dat niet met terugwerkende
kracht - na het deployen moet er dus 1x opnieuw gekoppeld worden (via de nu
zichtbare Pairing code/QR) om `is_paired` weer op `True` te krijgen. Dat is
verwacht, geen teken dat de fix niet werkt.

## Oplevercriteria

- `is_paired`/de "Paired"-sensor verandert alleen nog bij een geslaagde
  `/pair`-uitwisseling of een druk op "Unpair" - niet meer vanzelf op de
  achtergrond.
- Geen periodieke timer meer die de koppel-status controleert.
- Ná opnieuw koppelen: "Paired" blijft betrouwbaar op `True` staan, ook na
  gewoon app-gebruik (geen automatische terugval meer naar `False`).
