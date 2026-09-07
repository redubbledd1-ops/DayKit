# Opdracht voor Claude Code: reload van de HA-integratie wist phone_ip en last_event diagnose

## Context

Sinds de sync-omdraaiing (app pusht instellingen naar HA bij elke app-start/instellingenpagina-opening/koppeling) triggert een config-patch in HA telkens een reload van de `agendaalarm_backup`-entry (`_async_options_updated` → `async_reload`). Dat gebeurt dus nu veel vaker dan vroeger.

`_save_pending_rearm`/`_restore_pending_rearm` (`agendaalarm_backup/__init__.py` regel 543-582) bewaart een actieve wapening/fallback over zo'n reload heen, maar het `pending`-dict (regel 549-574) bevat alleen: `fire_at`, `mobiel_betrouwbaar`, `fallback_actief`, de fallback-instellingen (`speaker`/`volume`/`sound_url`/`interval`), en batterij-telemetrie (`last_known_battery_percent`/`last_battery_report_at`/`battery_usage_per_hour`).

**`hub.last_known_phone_ip`, `hub.last_event_received_at` en `hub.last_event_payload` ontbreken.** Live geconstateerd: na een koppeling/reload toonde "Watchdog status" correct "Armed, waiting for alarm time" (want `fire_at` was bewaard van een eerdere test), maar "Last event received" stond op "Onbekend" en "Phone reachable (ping)" op "Niet beschikbaar" — puur omdat die twee velden bij elke reload teruggezet worden naar hun `__init__`-default (`None`) op de verse hub, ook al was er al lang eerder een event/IP bekend. Gezien reloads nu veel frequenter zijn, is dit direct zichtbaar/hinderlijk geworden.

## Wat te doen

In `agendaalarm_backup/__init__.py` (en de identieke kopie in `ha-integration/custom_components/agendaalarm_backup/__init__.py`):

1. In `_save_pending_rearm`: voeg, op dezelfde plek als de batterij-telemetrie (regel 567-573), ook toe:
   - `pending["last_known_phone_ip"] = hub.last_known_phone_ip`
   - `pending["last_event_received_at"] = hub.last_event_received_at`
   - `pending["last_event_payload"] = hub.last_event_payload`

   Doe dit **niet** afhankelijk maken van de bestaande `if not pending: return`-guard (regel 558-559) — die guard bepaalt nu of er *iets* te bewaren valt op basis van wapening/fallback-status; als er niks gewapend is, hoeft er ook geen phone_ip/last_event bewaard te worden (die zijn toch niet relevant zonder actieve wapening), dus gewoon binnen het bestaande blok na die guard toevoegen is prima.

2. In `_restore_pending_rearm` (regel 578 e.v.): zet deze drie velden terug op de verse hub, op dezelfde plek/manier als de batterij-telemetrie al hersteld wordt (`hub.last_known_battery_percent = pending["last_known_battery_percent"]` etc.) — gebruik `.get(...)` met een veilige default (huidige hub-waarde, meestal `None`) zodat dit ook robuust is tegen een oudere, nog-niet-bijgewerkte `pending`-payload (bv. als een reload halverwege een eerdere Claude Code-run heeft plaatsgevonden vóórdat deze velden bestonden).

3. Doe dit in **beide** integratie-mappen.

## Oplevercriteria

- Project bouwt succesvol (Python-syntax OK in beide mappen, beide mappen blijven identiek).
- Test: arm een alarm (zodat `phone_ip`/`last_event_received_at` gezet worden), forceer daarna een reload (bv. door in de app een instelling op te slaan zodat er een config-patch gepusht wordt), en controleer dat "Last event received" en "Phone reachable (ping)" hun waarde/status behouden na de reload in plaats van terug te vallen op "Onbekend"/"Niet beschikbaar".
- Kort verslag aan het eind van wat getest is.
