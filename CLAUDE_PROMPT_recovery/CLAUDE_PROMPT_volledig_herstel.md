# Volledig herstel: AgendaAlarm Backup (HA-integratie + app-koppeling)

## Belangrijk vooraf

De map `ha-integration/custom_components/agendaalarm_backup/` en de bijbehorende
app-side Kotlin-bestanden zijn per ongeluk teruggezet naar een oude staat (8 juli).
Alles wat hieronder beschreven staat is er sindsdien bovenop gebouwd in een eerdere
sessie, maar die tussenstappen (en de losse prompt-bestanden die daarbij hoorden)
bestaan niet meer op schijf. Dit ene bestand is een samenvoeging van al die eerdere
stappen en bevat de VOLLEDIGE eindstaat die geïmplementeerd moet worden, in één keer,
bovenop de huidige (oude) code.

Ga stap voor stap door de secties hieronder. Elke sectie heeft eigen oplevercriteria.
Bouw en test tussentijds waar mogelijk. Als iets van de oude code al lijkt te matchen
met wat hieronder staat, hoeft dat uiteraard niet opnieuw — controleer gewoon of het
werkelijk aanwezig is voordat je het overslaat.

Relevante mappen:
- HA-integratie: `ha-integration/custom_components/agendaalarm_backup/`
- Android app: `app/src/main/java/com/dd/daykit/`

---

## 1. Minimale setup-flow + opties-menu opsplitsen

**Context:** Vroeger moest je bij "Integratie toevoegen" meteen alles invullen
(speaker, presence, scripts, etc.) via één groot formulier. Dat moet weg. Toevoegen
moet nul verplichte velden hebben; alle configuratie gebeurt daarna via het
Configureren-menu, opgesplitst in kleine per-onderwerp schermen.

**Wat te doen:**
- `config_flow.py`: `async_step_user` mag geen velden meer vragen — gewoon direct
  `return self.async_create_entry(title="AgendaAlarm Backup", data={})`.
- Voeg een `AgendaAlarmBackupOptionsFlow` toe met `async_step_init` die
  `self.async_show_menu(step_id="init", menu_options=SECTION_MENU_OPTIONS)` teruggeeft.
- Splits de config op in losse stappen, elk als eigen menu-item:
  - **Entiteiten** — de gedeelde entiteiten-whitelist (zie sectie 2)
  - **Speaker** — `speaker_entity_id`, `speaker_mode` (DISABLED/DEFAULT/BACKUP_ONLY/BOTH,
    zelfde enum-namen als Android's `ExternalSpeakerMode`)
  - **Aanwezigheid** — `presence_entity_id`, `presence_expected_state`
  - **Uit bed** — `out_of_bed_entity_id`, `out_of_bed_expected_value`
  - **Scripts** — `alarm_script_entity_id`/`alarm_script_enabled`/`alarm_script_ignore_presence`,
    `timer_script_entity_id`/`timer_script_enabled`/`timer_script_ignore_presence`
  - **Standaardwaarden** — `default_volume`, `default_interval`, `default_sound_url`,
    veiligheidsklep-timeout (zie sectie 9)
- Elke stap: bij opslaan de nieuwe waarden mergen in de volledige `entry.options`
  (niet overschrijven) via `hass.config_entries.async_update_entry`, en daarna
  terugkeren naar `await self.async_step_init()` zodat je weer in het keuzemenu komt
  (in plaats van dat de flow afsluit na elk klein onderdeel).
- Gebruik voor entity-selector-velden een helper die het `default=`-argument
  weglaat als er nog geen waarde is opgeslagen. **Let op:** `vol.Optional(key,
  default=None)` of `default=""` op een HA entity-selector veroorzaakt een directe
  valse validatiefout ("Entity None is neither a valid entity ID...") zodra het
  scherm opent, nog voordat de gebruiker iets aanraakt. Dus: alleen `default=`
  meegeven als er al een opgeslagen waarde is.

**Oplevercriteria:**
- Integratie toevoegen vraagt helemaal niets, opent meteen succesvol.
- Configureren opent een keuzemenu met de 6 subsecties hierboven.
- Elk subscherm opslaan brengt je terug naar het keuzemenu, niet naar "klaar".
- Een entity-selector-veld zonder opgeslagen waarde toont geen foutmelding bij het
  openen van het scherm.
- Instellingen die je in het ene scherm zet blijven behouden als je een ander
  scherm opslaat (geen overschrijven van niet-aangeraakte velden).

---

## 2. Entiteiten-whitelist, bidirectioneel gesynchroniseerd tussen app en HA

**Context:** Er moet één gedeelde lijst van entity-ID's zijn die zowel vanuit HA
(via Configureren > Entiteiten) als vanuit de app (Instellingen-scherm) beheerd kan
worden, en die aan beide kanten hetzelfde is. Toevoegen vanuit de ene kant moet aan
de andere kant verschijnen. Niks mag automatisch verdwijnen behalve als de
gebruiker het expliciet verwijdert.

**Wat te doen:**
- HA-kant: `CONF_ENTITIES` in `entry.options`, ingesteld via een multi-entity
  selector in het "Entiteiten"-scherm van de options flow (native HA
  zoeken/filteren, geen vrije tekst).
- Helper `_merge_entities(current_options, *entity_ids)` /
  `_merge_entities_from_fields`: als de gebruiker in andere secties (bv. Speaker,
  Aanwezigheid) een entity kiest, wordt die automatisch toegevoegd aan
  `CONF_ENTITIES` — nooit automatisch verwijderd.
- `config_sync.py`: `CONFIG_FIELDS` is de single source of truth voor de
  `/api/agendaalarm_backup/config` GET/POST-endpoint. `entities` moet hier onderdeel
  van zijn.
- App-kant (`HaSettingsViewModel.kt` / `HomeAssistantSettings.kt`):
  `applyRemoteConfigToUiState()` moet een **union met tombstone-tracking** doen:
  bewaar `lastConfirmedEntities` (de laatst van HA bevestigde set) zodat je kan
  onderscheiden tussen "HA heeft deze verwijderd" (zat in `lastConfirmedEntities`,
  zit niet meer in de nieuwe remote lijst → lokaal ook verwijderen) en "lokaal net
  toegevoegd, nog niet gesynced" (zit niet in `lastConfirmedEntities`, moet blijven
  staan ook al kent de remote lijst 'm nog niet).
- Zorg dat een save vanuit de app (POST naar `/config`) de volledige gemergde lijst
  meestuurt, niet alleen de lokale toevoegingen.

**Oplevercriteria:**
- Entity toevoegen in HA (Configureren > Entiteiten) verschijnt na een sync in de
  app-instellingen, en andersom.
- Een entity die je in de app toevoegt maar nog niet opgeslagen/gesynced hebt,
  overleeft een binnenkomende config-sync vanuit HA (wordt niet weggegooid).
- Een entity die je bewust verwijdert (aan welke kant dan ook) komt niet
  vanzelf terug na de volgende sync.

---

## 3. QR-koppelscherm zichtbaar op het dashboard + koppel/ontkoppel-UI + vertalingen

**Context:** De pairing-QR moet direct zichtbaar zijn op het HA-apparaat-dashboard
(niet alleen via een los endpoint dat je moet opzoeken), en de app moet een
koppel/ontkoppel-toggle hebben: nog niet gekoppeld → koppel-knop + QR + lang-leven
token; wel gekoppeld → ontkoppel-knop, geen QR/token meer nodig.

**Wat te doen:**
- Nieuw bestand `qr.py`: gedeelde helper `render_qr_png(payload)` die de QR-PNG
  rendert (gebruikt door zowel de camera-entiteit als het bestaande
  `AgendaAlarmQrCodeView`-endpoint).
- Nieuw bestand `camera.py`: `PairingQrCamera(AgendaAlarmBackupEntity, Camera)`.
  `async_camera_image()` genereert automatisch een nieuwe koppelcode als de huidige
  verlopen/afwezig is, en rendert 'm via `qr.py`. Camera-entiteiten worden door HA
  automatisch als afbeeldingstegel op het apparaat-dashboard getoond — geen losse
  Lovelace-kaart nodig.
- App-kant (`HaSettingsActivity.kt`, `HaSettingsViewModel.kt`):
  - Als er nog geen koppeling is: toon koppel-kaart met QR-instructie/setup-code-veld
    en het lang-leven token-veld.
  - Als er al gekoppeld is: verberg het lang-leven-token-veld (overbodig zodra
    gekoppeld) en toon in plaats daarvan een ontkoppel-knop.
  - Nieuwe `clearPairing()` in `HaSettingsViewModel.kt` voor de ontkoppel-actie.
  - Ontkoppel-knop UI: gecentreerd (was eerder scheef uitgelijnd).
- Vertaal het volledige koppelscherm (setup-instructies, knoppen, foutmeldingen)
  naar alle 18 talen die `LanguageManager.kt` al ondersteunt
  (nl,en,es,pt,de,fr,it,ko,zh,ja,ru,ar,hi,tr,pl,id,uk,vi) — gebruik het bestaande
  `t(...)`-patroon dat de rest van de app al gebruikt.

**Oplevercriteria:**
- Het HA-apparaat-dashboard toont de QR-code direct als afbeeldingstegel, zonder
  los endpoint te hoeven bezoeken.
- App toont koppel-UI als er nog geen koppeling is, en ontkoppel-UI (gecentreerd)
  als er wel een koppeling is.
- Lang-leven token is niet meer zichtbaar zodra er al gekoppeld is.
- Koppelscherm-teksten zijn voor alle 18 talen aanwezig (geen hardcoded NL/EN-only
  strings op dit scherm).

---

## 4. Entiteitenbeheer NIET via een to-do-lijst

**Context:** Er is eerder geprobeerd om de entiteitenlijst als HA `todo`-entiteit te
bouwen. Dat werkte totaal niet: geen zoek/autocomplete op entity-ID's, vereiste een
handmatig samengestelde dashboard-kaart om er ook maar bij te kunnen. Dit is
expliciet teruggedraaid na negatieve gebruikerservaring. **Bouw dit niet opnieuw.**
Entiteitenbeheer loopt via het normale Configureren-menu (zie sectie 1 en 2), met
een native HA multi-entity-selector. Als er nog restanten zijn van de
`todo`-aanpak (bv. `todo.py`, verwijzingen naar `todo.add_item`/`todo.remove_item`
voor entiteitenbeheer), verwijder die.

**Oplevercriteria:**
- Geen `todo`-platform meer aanwezig voor entiteitenbeheer.
- Entiteiten beheren kan volledig via Configureren > Entiteiten (native selector,
  met zoeken/filteren).

---

## 5. Kleine UX- en datafixes

**Context:** Een aantal kleinere bugs/verbeteringen die los van elkaar staan.

**Wat te doen:**
- **Lege default sound URL (2-laags bug):** `const.py`'s `DEFAULT_SOUND_URL` bevatte
  een echte GitHub-URL die als "standaard" werd voorgesteld terwijl 'ie niet
  functioneel bedoeld was als standaard-geluid. Vervang door een neutrale/lege
  placeholder. **Én** `config_sync.py`'s `CONFIG_FIELDS`-dict lekte deze URL nog
  steeds naar de app via `/config` ook als het formulier leeg was — zet de default
  daar op `""`. Voeg een eenmalige migratie toe die een reeds opgeslagen waarde die
  exact matcht met de oude hardcoded URL wist.
- **Orphaned `GebruikerThuisSwitch`:** deze entiteit werd alleen aangemaakt
  `if not hub.presence_entity_id`. HA verwijdert conditioneel aangemaakte
  entiteiten niet automatisch als de voorwaarde later verandert. Voeg expliciete
  entity-registry-opschoning toe: als `presence_entity_id` wél gezet is, verwijder
  de switch-entiteit uit de registry als die nog bestaat.
- **Extra diagnostic sensoren** in `sensor.py`:
  - `VolgendAlarmSensor` (`SensorDeviceClass.TIMESTAMP`) — leest
    `hub.next_alarm_fire_at`.
  - `WatchdogStatusSensor` (diagnostic) — `extra_state_attributes` met o.a.
    `mobiel_betrouwbaar`, `gebruiker_thuis`, `speaker_geconfigureerd`,
    `geluid_geconfigureerd`, `laatst_afgespeeld`, `batterij_percentage`,
    `batterij_laatst_gerapporteerd`, `batterij_waarschijnlijk_leeg`, `uit_bed`.

**Oplevercriteria:**
- Een leeg sound-URL-veld toont geen echte URL, ook niet via de `/config`-API.
- Geen wees-entiteit meer zichtbaar als presence eenmaal is ingesteld.
- Beide nieuwe sensoren zichtbaar en gevuld in HA.

---

## 6. Root cause-fixes voor "Volgend alarm blijft op Onbekend"

**Context:** Dit was een langlopend probleem met drie afzonderlijke oorzaken,
allemaal gevonden en opgelost tijdens de vorige sessie.

**Wat te doen:**

**6a. `speaker_mode`-gate (app-kant, GEEN bug, maar wél een makkelijk te missen eis):**
`AlarmScheduler.kt` (rond de `armBackupWatchdog()`-call) gate't de hele HA-sync op
`triggerConfig?.mode == TriggerBehaviorMode.SMART_ALARM` ÉN
`externalSpeakerMode != DISABLED && externalSpeakerEntityId niet leeg`. Dit is
bestaand, gewenst gedrag — zorg dat dit behouden blijft, niet per ongeluk
weggehaald wordt bij het herbouwen. **Let op:** "Normaal"-modus alarmen (NORMAL)
zijn bewust en blijvend volledig standalone en syncen nooit met HA — dat is geen
open vraag, niet opnieuw ter discussie stellen.

**6b. Dode `presence_expected_state`:** dit veld werd wel getoond in UI/config-sync
maar nooit echt gelezen — `is_user_home()` had `state.state in ("home", "on")`
hardcoded. Fix: `is_user_home()` moet `self.presence_expected_state` gebruiken.

**6c. Config-reload wist watchdog-state (het belangrijkste, meest sluipende
probleem):** elke config-entry-wijziging (`entry.add_update_listener`) triggert
een volledige `hass.config_entries.async_reload()`. Dit gebeurt zeer vaak — elke
keer dat de app instellingen opslaat (POST naar `/config`) én elke keer dat er in
HA's Configureren-menu iets wordt opgeslagen, ook iets onrelateerds. Een reload
= `async_unload_entry` (annuleert `hub._watchdog_unsub`/`_loop_unsub`/`_timeout_unsub`)
gevolgd door een gloednieuwe `AlarmBackupHub`-instantie. Alle runtime-state
(`next_alarm_fire_at`, `mobiel_betrouwbaar`, een actieve fallback, de gewapende
watchdog zelf) ging daarbij verloren.

Fix: bewaar kritieke live-state vóór unload in
`hass.data[DOMAIN]["_pending_rearm"]` (fire_at, of er een actieve fallback loopt,
mobiel_betrouwbaar, etc.) en herstel die na de nieuwe setup — zodat een
config-sync nooit meer een al gewapende watchdog stilletjes annuleert.

**Oplevercriteria:**
- `Volgend alarm`-sensor update betrouwbaar zodra de app een alarm arm't, en
  overleeft daaropvolgende config-syncs (test: arm een alarm, wijzig daarna iets
  in Configureren, controleer dat `Volgend alarm` niet terugvalt naar Onbekend).
- Aanwezigheid-check gebruikt daadwerkelijk de ingestelde
  `presence_expected_state`, niet een hardcoded waarde.

---

## 7. Disarm-mechanisme

**Context:** Een geannuleerd alarm vertelde HA nooit dat het niet meer bewaakt
hoefde te worden (alleen de oude, aparte webhook-integratie werd geleegd) — kan
een vals backup-alarm veroorzaken voor een alarm dat niet meer bestaat.

**Wat te doen:**
- HA-kant: nieuwe `async_disarm()` op `AlarmBackupHub`, en een nieuwe
  `"disarm"`-actie in `AgendaAlarmEventView` (naast de bestaande
  `arm`/`start`/`stop`).
- App-kant: nieuwe `disarmBackupWatchdog()` in `HomeAssistantRepository.kt`,
  aangeroepen vanuit zowel `cancelMainAlarm()` als de tak waar er geen
  planbaar volgend alarm meer over is.

**Oplevercriteria:**
- Een alarm annuleren in de app stuurt een disarm-signaal naar HA en de
  `Volgend alarm`-sensor/watchdog-status reflecteert dat er niets meer gewapend
  is.

---

## 8. Backup-alarm beslislogica: batterij-first, fail-safe

**Context:** Volledige herontwerp van de beslislogica die bepaalt of het
backup-alarm afgaat, met als kernprincipe: batterijstatus van de telefoon weegt
zwaarder dan aanwezigheid, en bij twijfel/onbekende status altijd wél alarmeren
(fail-safe), nooit stilzwijgend niks doen.

**Wat te doen — de volgorde in `_async_watchdog_check()`:**
1. **Betrouwbaar telefoon-report:** als de telefoon zelf recent en betrouwbaar
   heeft gemeld dat het alarm is afgegaan (`reportAlarmAlive(reliable=true)`),
   geen backup nodig — stop hier.
2. **Batterij waarschijnlijk leeg (hoogste prioriteit override):** als
   `is_battery_likely_dead()` waar is, **altijd** het backup-alarm starten,
   ongeacht aanwezigheid/uit-bed-status. Dit checkt continu (niet pas na het
   uitvallen): laatste bekende batterijpercentage +
   verbruik-per-uur-heuristiek om in te schatten of de telefoon nog leeft op het
   moment dat het hoofdalarm had moeten afgaan.
3. **Aanwezigheid bevestigd thuis (fail-safe):** als presence niet geconfigureerd
   is, of de status is "not home"/onbekend, **alarm afgaan**. Alleen als
   expliciet bevestigd thuis, ga door naar stap 4.
4. **Uit bed bevestigd (fail-safe):** alleen als zowel thuis ALS uit-bed
   bevestigd zijn, mag het backup-alarm onderdrukt worden. Is uit-bed niet
   bevestigd (of niet geconfigureerd terwijl presence wel geconfigureerd is)
   → alarm afgaan. **Uitzondering, expliciet zo gewenst:** als de gebruiker
   geen aanwezigheid-detectie heeft ingesteld, negeer de uit-bed-check dan
   volledig (aanwezigheid ontbreekt al → stap 3 heeft dan al besloten).

**Batterij-tracking (nodig voor stap 2):**
- Hub-attributen: `last_known_battery_percent`, `last_battery_report_at`,
  `battery_usage_per_hour`.
- `is_battery_likely_dead()`: schat op basis van laatst bekend percentage,
  tijdsverloop sinds dat report, en het verbruik-per-uur of de telefoon
  waarschijnlijk nog batterij over heeft op het beoogde alarm-tijdstip.
- App-kant: `AlarmOutputDecisionEngine.kt` — nieuwe `readCurrentBatteryPercent(context)`
  (echte `BatteryManager`/`ACTION_BATTERY_CHANGED`-read), meegestuurd bij zowel
  `armBackupWatchdog()` als `reportAlarmAlive()`-calls zodat HA een actueel
  beeld heeft.
- Nieuw: `is_out_of_bed()` op de hub — leest de geconfigureerde
  `out_of_bed_entity_id`/`out_of_bed_expected_value` als read-only statusspiegel
  (de daadwerkelijke uit-bed-beslissing voor het hoofdalarm blijft
  app-side in `RuleEngine.kt`; dit is puur voor HA's eigen watchdog-beslissing).

**Oplevercriteria:**
- Alle 4 stappen zijn terug te vinden in `_async_watchdog_check()` in exact deze
  volgorde/prioriteit.
- Onbekende/niet-geconfigureerde aanwezigheid of uit-bed-status resulteert altijd
  in een alarm (fail-safe), nooit in stilzwijgend niks doen.
- Batterij-waarschijnlijk-leeg overschrijft altijd aanwezigheid/uit-bed, ook als
  je "thuis" en "uit bed" bent.
- `WatchdogStatusSensor`-attributen (zie sectie 5) tonen de actuele
  batterij-/uit-bed-status zodat dit controleerbaar is vanuit HA.

---

## 9. Thread-safety crash in `async_write_ha_state`

**Context:** Herhaalde crashes in de HA-log (`RuntimeError` vanuit
`async_write_ha_state()`/`async_dispatcher_send()`), vermoedelijk veroorzaakt
doordat HA's periodieke camera-thumbnail-ververs-cyclus `async_camera_image()`
aanroept vanuit een executor-thread (niet de event loop), wat vervolgens
`generate_pairing_code()`/`_notify_update()` triggert die op hun beurt
`async_write_ha_state()` aanroepen vanuit de verkeerde thread. Dit verklaart
vermoedelijk een groot deel van de "sensoren updaten niet"-symptomen door de
hele sessie heen.

**Wat te doen:**
- `entity.py` (`AgendaAlarmBackupEntity._handle_update()`, rond de
  `self.async_write_ha_state()`-call) en overal elders waar
  `async_write_ha_state`/`async_dispatcher_send` mogelijk vanuit een
  non-event-loop-thread aangeroepen kan worden: zorg dat de aanroep terug naar
  de event loop wordt gehopt (bv. via `hass.loop.call_soon_threadsafe(...)` of
  door de state-update-logica zelf niet vanuit `async_camera_image()`'s
  thread-context te laten lopen, maar via een schedule-call terug op de loop).
- Controleer specifiek `camera.py`'s `async_camera_image()`-pad: als daar
  code staat die de hub-state update / de pairing-code genereert en dat
  vervolgens dispatched, moet dat thread-safe gebeuren.

**Oplevercriteria:**
- Geen `RuntimeError`/thread-safety-crashes meer in de HA-log na een periode van
  normaal gebruik (inclusief het open hebben van het apparaat-dashboard met de
  camera-tegel, wat de refresh-cyclus triggert).
- Bevestig expliciet (in de samenvatting die je teruggeeft) of de
  watchdog-arm-mechaniek zelf al dan niet beïnvloed werd door deze crash —
  dispatcher-exceptions zijn meestal per-subscriber geïsoleerd, maar dit moet
  geverifieerd worden, niet aangenomen.

---

## 10. Instelbare veiligheidsklep (safety timeout)

**Context:** De fallback stopt automatisch na een vaste timeout als niemand 'm
stopt. Dit stond hardcoded op 20 minuten (`SAFETY_TIMEOUT_SECONDS = 20 * 60`)
— moet instelbaar worden, met een veel kortere default.

**Wat te doen:**
- `const.py`: vervang de harde `SAFETY_TIMEOUT_SECONDS`-constante door
  `CONF_SAFETY_TIMEOUT` (config-key) + `DEFAULT_SAFETY_TIMEOUT = 60` (seconden).
- Voeg een `number`-entiteit toe (net als de bestaande
  `FallbackIntervalNumber`-stijl in `number.py`) waarmee de gebruiker dit kan
  instellen, en/of een veld in het "Standaardwaarden"-scherm van de options
  flow (sectie 1).
- Zorg dat de watchdog/fallback-timeout-logica deze geconfigureerde waarde
  gebruikt in plaats van de oude constante.

**Oplevercriteria:**
- Standaardwaarde is 60 seconden, niet 20 minuten.
- Waarde is aanpasbaar via HA (number-entiteit en/of Configureren-scherm) en
  de fallback stopt daadwerkelijk na de ingestelde tijd.

---

## 11. Engelse vertaling + True/False sensoren

**Context:** De hele HA-integratie stond in het Nederlands, ongeacht de
taalinstelling van de gebruiker (HA-integraties gebruiken normaal gesproken
`strings.json`/`translations/`, niet hardcoded NL-tekst in Python). Daarnaast
moeten boolean-achtige sensoren (bv. "Thuis", "Uit bed") letterlijk "True"/"False"
tonen in plaats van Nederlandse aan/uit-achtige teksten.

**Wat te doen:**
- Vertaal alle entity-namen, service-namen, config-flow-labels en
  foutmeldingen in de HA-integratie naar het Engels, via HA's standaard
  `translations/en.json` (en desgewenst `strings.json` als bronbestand) in
  plaats van hardcoded Nederlandse strings in de Python-code.
- Binary-sensor-achtige statusvelden (bv. "Thuis (live)", "Uit bed") tonen
  letterlijk `"True"`/`"False"` als state/tekstweergave.
- Check of dit specifiek voor deze integratie bedoeld is (los van HA's eigen
  systeemtaal-instelling) — de gebruiker gaf aan dat alles gewoon naar het
  Engels moet, niet per se dynamisch per HA-gebruikerstaal.

**Oplevercriteria:**
- Geen Nederlandse UI-tekst meer zichtbaar in de HA-integratie (entiteiten,
  configflow, sensoren).
- Boolean-sensoren tonen letterlijk True/False.

---

## Afsluitende check

Geef aan het einde een korte samenvatting terug van:
- Welke van de 11 secties hierboven daadwerkelijk iets moesten doen (vs. al
  aanwezig bleken).
- Of de thread-safety-fix (sectie 9) bevestigd is opgelost, inclusief of de
  watchdog-arm-mechaniek daar wel/niet door beïnvloed werd.
- Eventuele afwijkingen van bovenstaande spec die je bewust hebt gemaakt, en
  waarom.
