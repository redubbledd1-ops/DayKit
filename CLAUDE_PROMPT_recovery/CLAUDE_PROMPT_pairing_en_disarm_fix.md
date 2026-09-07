# Koppel-UI + batterij-veldnaam + disarm (app-kant, Kotlin)

## Context

De HA-integratie (`ha-integration/custom_components/agendaalarm_backup/`) is inmiddels
teruggezet naar de juiste, laatste versie (uit een HA-backup) — die hoeft dus NIET
opnieuw gebouwd te worden. Deze prompt gaat alleen over de Android-app
(`app/src/main/java/com/dd/daykit/`), waar een paar dingen achterblijven op
wat de HA-kant inmiddels kan.

Belangrijk: veel van de koppel-logica bestaat al en werkt (zie
`homeassistant/PairingClient.kt`'s `exchangeCode()` en
`viewmodel/HaSettingsViewModel.kt`'s `pairWithSetupCode()`) — dit gaat puur om de
resterende UI-afwerking plus twee concrete bugs hieronder.

## 1. Koppel-kaart moet "Verbonden"-status + ontkoppel-knop tonen

**Huidige staat** (`HaSettingsActivity.kt`, rond regel 232-256): er staan twee losse,
altijd-identieke kaarten naast elkaar: "Koppelen (QR/code)" en "Token", allebei
altijd met dezelfde tekst, ongeacht of er al gekoppeld is.

**Gewenst:**
- Als er al een geldig token + actieve HA-verbinding is (`uiState.longLivedToken`
  niet leeg, `uiState.activeBaseUrl` niet leeg): de "Koppelen (QR/code)"-kaart moet
  tonen dat je verbonden bent (bv. titel "Verbonden" / beschrijving met het
  actieve adres), met een knop om te **ontkoppelen** in plaats van opnieuw te
  koppelen.
- Als er nog niet gekoppeld is: kaart toont zoals nu "Koppelen (QR/code)" met de
  scan/code-invoer-flow.
- Nieuwe functie `clearPairing()` in `HaSettingsViewModel.kt`: wist
  `longLivedToken` en `activeBaseUrl` uit de UI-state en persisteert dat via
  `settingsStorage.saveSettings(...)` (zelfde patroon als `saveSettings()`, maar
  dan leeg makend i.p.v. vullend). Roep `repository.clearClientCache()` aan zodat
  een volgende call niet per ongeluk het oude token nog gebruikt.
  Let op: dit is puur lokaal "vergeet deze sessie" — het long-lived token blijft
  in HA's Profiel > Long-Lived Access Tokens bestaan (dat hoeft niet serverside
  ingetrokken te worden om te ontkoppelen).

## 2. Token-kaart alleen nog als fallback tonen

**Huidige staat:** de losse "Token"-kaart (handmatig token plakken via
`TokenModal.kt`) staat altijd zichtbaar, ook als er al via QR/code gekoppeld is —
overbodig zodra er al een werkende koppeling is.

**Gewenst:** verberg de losse Token-kaart (of verplaats 'm onder een
"geavanceerd"/"handmatig"-sectie) zodra `uiState.longLivedToken` niet leeg is.
Nog niet gekoppeld → gewoon zichtbaar als alternatief voor wie liever handmatig
een token plakt dan QR/code gebruikt.

## 3. Bug: batterijpercentage komt niet aan bij HA (veldnaam-mismatch)

**Root cause, geverifieerd:** `HomeAssistantRepository.kt`'s `armBackupWatchdog()`
(regel ~904-909) en `reportAlarmAlive()` (regel ~941-946) sturen het
batterijpercentage als JSON-key `"battery"`. De HA-kant
(`http_views.py`'s `AgendaAlarmEventView`) leest echter `"battery_percent"` (en
apart `"battery_usage_per_hour"`, dat helemaal niet verstuurd wordt). Resultaat:
HA ontvangt nooit een bruikbaar batterijpercentage, wat de nieuwe
batterij-first beslislogica (`is_battery_likely_dead()`) altijd met lege data
laat werken — waarschijnlijk (mede)oorzaak van de vage/onverwachte
watchdog-status die je nu ziet.

**Fix:**
- In `buildFallbackJson`-aanroepen / de payload-opbouw van zowel
  `armBackupWatchdog()` als `reportAlarmAlive()`: hernoem de key `"battery"` naar
  `"battery_percent"`.
- Voeg `"battery_usage_per_hour"` toe aan diezelfde payload. De waarde staat al
  ergens in de app beschikbaar (zie `generateExportText()` in
  `HaSettingsViewModel.kt`, die `SettingsManager.getBatteryUsagePerHour(context)`
  gebruikt) — geef 'm door als extra parameter aan beide functies, net zoals nu al
  met `batteryPercent` gebeurt, en stuur 'm mee als `battery_usage_per_hour` in de
  JSON.
- Check de aanroeppunten van `armBackupWatchdog()`/`reportAlarmAlive()` (in
  `AlarmScheduler.kt`) en geef daar ook de actuele batterij-per-uur-waarde mee.

## 4. Ontbrekende disarm-aanroep

**Geverifieerd:** HA-kant ondersteunt al `action: "disarm"` op
`/api/agendaalarm_backup/event` (zie `AgendaAlarmEventView` in `http_views.py`),
maar er is geen `disarmBackupWatchdog()` in `HomeAssistantRepository.kt` — dus de
app roept dit endpoint nooit aan.

**Fix:**
- Voeg `disarmBackupWatchdog(): HaUpdateResult` toe aan `HomeAssistantRepository.kt`,
  zelfde stijl als `reportAlarmStop()` (regel ~963), maar met
  `put("action", "disarm")`.
- Roep deze aan vanuit `AlarmScheduler.kt` op elk punt waar een eerder gewapend
  alarm nu geannuleerd wordt zonder dat er een nieuw volgend alarm gepland wordt
  (dus: bij het annuleren van het hoofdalarm, en bij de tak waar er geen
  eerstvolgend plan­baar alarm meer overblijft). Best-effort/non-blocking, net als
  de andere HA-aanroepen hier.

## Oplevercriteria

- Gekoppeld: koppel-kaart toont een verbonden-status + ontkoppel-knop; ontkoppelen
  wist het lokale token/adres en de kaart valt terug naar de niet-gekoppeld-staat.
- Niet gekoppeld: Token-kaart blijft zichtbaar als alternatief; zodra gekoppeld
  (via QR/code of handmatig token) verdwijnt/verplaatst 'ie.
- `armBackupWatchdog`/`reportAlarmAlive` versturen `battery_percent` en
  `battery_usage_per_hour` met de exacte key-namen die `http_views.py` verwacht.
- Een geannuleerd alarm resulteert in een disarm-aanroep naar HA.
- Test na deze fix: arm een Slim-alarm met speaker geconfigureerd, controleer in
  HA of `Volgend alarm` een tijdstip toont (niet Onbekend) en de
  `WatchdogStatusSensor`/`Watchdog status`-attributen een batterijpercentage
  tonen in plaats van leeg.
