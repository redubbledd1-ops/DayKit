# Opdracht voor Claude Code: discoAlarmBackupAlarm.mp3 altijd als fallback-geluid, app + HA

## Context

Terugkerend probleem (eerder al geprobeerd, toen niet gelukt): na het terugzetten van een backup staat het alarmgeluid in de app op "null" en blijft het alarm stil. Doel: zorg dat er **ALTIJD** een alarmgeluid afspeelt — zowel op de telefoon zelf als via de Home Assistant backup-speaker — met `C:\Users\redub\Desktop\Projects\CalenderAlarm\discoAlarmBackupAlarm.mp3` als de fallback/standaard, in beide systemen.

Ik heb de codebase al onderzocht en de twee onderliggende oorzaken gevonden (dit is dus geen open zoekopdracht, gebruik onderstaande bevindingen als startpunt):

### Bevinding 1 — App-kant: stil alarm na restore door "verweesde" custom-sound URI

- `discoAlarmBackupAlarm.mp3` (root van het project) is byte-voor-byte identiek (md5 `0a584b626eff8e0eb4ec01c17afb5bf8`) aan `app/src/main/res/raw/disco_ring_end.mp3`, dat al gebundeld is als de standaard "Disco"-ringtone via `SettingsManager.DEFAULT_ALARM_SOUND_URI = "android.resource://com.dd.daykit/raw/disco_ring_end"` (`SettingsManager.kt` regel 39). Dit bestand hoeft dus niet gekopieerd te worden, het zit al in de APK — het gaat om de logica eromheen.
- Het geluid dat een gebruiker kiest wordt opgeslagen als string-URI in `SharedPreferences("alarm_settings")` key `alarm_sound_uri` (`SettingsManager.getAlarmSoundUri`/`saveAlarmSoundUri`, regel 183-188). Bij een **custom** (zelf toegevoegd) geluid is dit een pad naar een bestand onder `context.filesDir/alarm_sounds/custom/...`, geregistreerd in de Room-database via `customAlarmSoundDao` (zie `SoundRepository.kt`).
- `BackupManager.kt` back-upt en herstelt deze `alarm_settings` SharedPreferences dus **wel** de opgeslagen `alarm_sound_uri`-string (zie `appSettingsPrefs` in `AppBackupData` en `restoreBackupData`, regel 275-335) — maar back-upt **niet** de Room-database (`customAlarmSoundDao`) en **niet** de daadwerkelijke geluidsbestanden onder `alarm_sounds/custom/`.
- Gevolg: na een restore verwijst `alarm_sound_uri` nog naar een custom geluid dat lokaal niet meer bestaat. Dat is **geen `null`/lege string**, dus de bestaande null-check-fallback in `AlarmService.kt` (`resolvePlaybackSoundUriString`, regel 601-619, en `playRingtoneAndVibrate`, regel 633-689) wordt **niet** geraakt: `RingtoneManager.getRingtone(...)` krijgt een niet-lege maar kapotte URI, faalt stil bij het afspelen (of geeft geen fout maar ook geen geluid), en er is geen enkele controle die dan alsnog terugvalt op `DEFAULT_ALARM_SOUND_URI`.
- Dezelfde soort niet-gevalideerde sound-URI resolutie zit ook in: `AlarmScheduler.kt` regel 118-123 (`defaultSoundUri`/`soundUri` wordt zonder validatie in het `AlarmItem` gebakken bij het plannen), en `rules/TriggerBehaviorViewModel.kt` regel 68 (`config.alarmSoundUri ?: SettingsManager.getAlarmSoundUri(context)`). Check ook `SnoozePopupForegroundService.kt`, `TimerPopupForegroundService.kt`, `StopwatchPopupForegroundService.kt`, `AgendaAlarmPopupForegroundService.kt`, `TestCountdownForegroundService.kt` en `TimerFinishedAlarmUi.kt` (allemaal eerder gevonden met `grep -rl "fallback"` op sound-gerelateerde code) op hetzelfde patroon.

### Bevinding 2 — Home Assistant-kant: twee losstaande bugs in de fallback-speaker

Integratie zit op **twee plekken** die momenteel identiek zijn (zelfde inhoud, zelfde mtime) en dus **allebei** aangepast moeten worden, of je moet er één een symlink/kopie van de ander van maken zodat ze niet uit sync raken:
- `C:\Users\redub\Desktop\Projects\CalenderAlarm\agendaalarm_backup\`
- `C:\Users\redub\Desktop\Projects\CalenderAlarm\ha-integration\custom_components\agendaalarm_backup\`

Bugs:
1. **`const.py` regel 35**: `DEFAULT_SOUND_URL = "https://raw.githubusercontent.com/redubbledD/AlarmSounds/main/Boogie.mp3"` — de standaard hangt af van een extern GitHub-bestand ("Boogie"), niet van `discoAlarmBackupAlarm.mp3`, en is een single point of failure (geen internet/GitHub down = geen fallback-geluid).
2. **`__init__.py` regel 104**: `self.sound_url: str = opts.get(CONF_DEFAULT_SOUND_URL, "")` — dit valt terug op een **lege string**, niet op de `DEFAULT_SOUND_URL`-constante. Vergelijk met `config_sync.py` regel 74, waar `CONFIG_FIELDS` voor hetzelfde veld wél correct `DEFAULT_SOUND_URL` als default gebruikt — dit is dus een inconsistentie/bug tussen de twee plekken waar de default wordt toegepast. Gevolg: zodra er geen expliciete `default_sound_url` in de config-entry options staat (bv. verse install, of een pad waarbij die key nooit geschreven is), is `hub.sound_url` leeg. In `_play_sound()` (regel 336-338) staat dan: `if not self.speaker_entity_id or not self.sound_url: return` — de fallback-speaker doet dan **helemaal niets**, stil, zonder foutmelding. Dit is exact het "geluid staat op null, alarm blijft stil"-symptoom maar dan aan de HA-kant.
3. App-kant heeft dezelfde GitHub-URL nogmaals hardcoded als standaardwaarde in `viewmodel/HaSettingsViewModel.kt` regel 48: `val selectedGithubSoundUrl: String = "https://raw.githubusercontent.com/redubbledD/AlarmSounds/main/Boogie.mp3"`. Deze waarde wordt naar HA gepushed als `default_sound_url` (zie regel 383-384 en `HomeAssistantRepository.kt` regel 793-947, `buildFallbackJson`/`sound_url` in de JSON payload).
4. Bestaand, al gebruikt patroon voor absolute HA-URL's naar lokaal gehoste bestanden: `get_url(hass, prefer_external=False, allow_internal=True)` — al in gebruik in `camera.py` regel 59, `config_flow.py` regel 131, `http_views.py` regel 263 en `sensor.py` regel 146. Custom geluiden die de app naar HA upload komen terecht in `<config>/www/agendaalarm_sounds/<bestand>`, publiek bereikbaar via `/local/agendaalarm_sounds/<bestand>` (zie `const.py` regel 51-52, `http_views.py`'s `AgendaAlarmSoundUploadView`, en `homeassistant/SoundHaSync.kt`'s `uploadSoundToHa`).

## Wat te doen

### 1. App: centrale, valideren-vóór-afspelen sound-resolutie (dit is de kern van de fix)

Maak in `SettingsManager.kt` (of een nieuw klein bestand, jouw keuze) één centrale functie, bv. `fun resolvePlayableAlarmSoundUri(context: Context, candidateUriString: String?): Uri`, die:
- `null`/lege string meteen naar `DEFAULT_ALARM_SOUND_URI` doorstuurt (bestaand gedrag behouden);
- bij een niet-lege string **daadwerkelijk controleert of het bestand/de URI nog bestaat en leesbaar is** vóórdat 'm gebruikt wordt: voor `file://`-paden en kale bestandspaden via `File(...).exists()`, voor `content://` via `context.contentResolver.openInputStream(uri)?.use { }` in een try/catch, voor `android.resource://` (systeem/bundled) altijd als geldig beschouwen;
- bij elke fout/ontbrekend bestand terugvalt op `Uri.parse(DEFAULT_ALARM_SOUND_URI)`, met een duidelijke `Log.w`-regel die zegt dat er is teruggevallen op het standaardgeluid en waarom (zodat dit later in Logcat te zien is als het weer misgaat).

Vervang vervolgens **alle** plekken die nu los `SettingsManager.getAlarmSoundUri(...)` of `config.alarmSoundUri` combineren met een `?:`/null-check door een aanroep van deze nieuwe functie, met name:
- `AlarmService.kt`: `resolvePlaybackSoundUriString` (regel 601-619) en de URI-parsing in `playRingtoneAndVibrate` (regel 665-674).
- `AlarmScheduler.kt` regel 118-123 (zodat een alarm nooit met een kapotte/verweesde `soundUri` gepland wordt).
- `rules/TriggerBehaviorViewModel.kt` regel 68.
- `SnoozePopupForegroundService.kt`, `TimerPopupForegroundService.kt`, `StopwatchPopupForegroundService.kt`, `AgendaAlarmPopupForegroundService.kt`, `TestCountdownForegroundService.kt`, `TimerFinishedAlarmUi.kt` — zoek zelf in deze bestanden naar waar een geluids-URI wordt opgehaald/geparsed en gebruik daar dezelfde centrale functie, zodat dit niet telkens los geïmplementeerd wordt.

Let op: de functie moet **synchroon en snel** zijn (geen netwerk, alleen lokale bestandschecks) — dit draait op het kritieke pad vlak vóórdat een alarm moet afgaan, dus geen vertraging of timeouts hier.

### 2. App: BackupManager laat geen verweesde referentie meer achter (extra vangnet, naast punt 1)

In `BackupManager.kt`, `restoreBackupData` (regel 275 e.v.): voeg na het herstellen van `appSettingsPrefs` een check toe die, als de herstelde `alarm_sound_uri` verwijst naar een custom geluid dat niet (meer) bestaat op dit toestel, deze pref actief terugzet naar `null`/leeg (via `SettingsManager.saveAlarmSoundUri(context, null)`), zodat de UI ("welk geluid is geselecteerd") ook een correcte, niet-misleidende staat toont in plaats van een geluid te "kiezen" dat toch niet meer bestaat. Dit is een aanvulling op punt 1, geen vervanging — punt 1 is de garantie dat er sowieso geluid is, dit hier is nette UI-hygiëne.

### 3. App: standaardgeluid consistent naar discoAlarmBackupAlarm/Disco noemen

In `viewmodel/HaSettingsViewModel.kt` regel 48: vervang de hardcoded GitHub-URL default (`selectedGithubSoundUrl`) door de nieuwe lokale HA-URL uit stap 4 hieronder (of laat 'm leeg en laat de HA-kant zelf de default toepassen, net zoals `config_sync.py` dat al doet — kies wat het minst dubbele bronnen van waarheid oplevert). Zorg dat de naam die in de UI getoond wordt voor dit standaardgeluid duidelijk "Disco"/"discoAlarmBackupAlarm" is, niet "Boogie" (zoek naar waar "Boogie" nog als label in de UI staat, bv. rond regel 1000-1010 van hetzelfde bestand, en pas de weergavenaam aan).

### 4. Home Assistant: discoAlarmBackupAlarm.mp3 lokaal bundelen en als default gebruiken (geen GitHub-afhankelijkheid meer)

Doel: de HA-fallback-speaker moet altijd een geluid kunnen afspelen, ook zonder internet/GitHub — dus bundel het bestand bij de integratie zelf.

- Kopieer `discoAlarmBackupAlarm.mp3` naar een nieuwe map in de integratie zelf, bv. `agendaalarm_backup/assets/discoAlarmBackupAlarm.mp3` (en identiek in `ha-integration/custom_components/agendaalarm_backup/assets/`).
- In `__init__.py`'s `async_setup_entry` (regel 432 e.v.): zorg dat dit bestand bij setup (idempotent, dus alleen kopiëren als het nog niet bestaat of als de bestandsgrootte/hash afwijkt) naar `<config>/www/agendaalarm_sounds/discoAlarmBackupAlarm.mp3` gekopieerd wordt — dezelfde map die `http_views.py`'s `AgendaAlarmSoundUploadView` al gebruikt (`SOUNDS_SUBDIR` in `const.py`). Gebruik `hass.async_add_executor_job` voor de bestands-I/O (niet blokkerend in de event loop, zoals de rest van deze integratie ook al async is).
- Bouw de absolute URL met hetzelfde bestaande patroon als elders in deze integratie: `get_url(hass, prefer_external=False, allow_internal=True)` + `/local/agendaalarm_sounds/discoAlarmBackupAlarm.mp3`.
- Verander `const.py` regel 35 zodat `DEFAULT_SOUND_URL` niet langer een hardcoded string is maar wordt opgebouwd/opgehaald op het moment dat 'm nodig is (met de lokale `/local/...`-URL als resultaat), OF houd 'm als relatief pad-constante (`"/local/agendaalarm_sounds/discoAlarmBackupAlarm.mp3"`) en laat de plek(ken) waar 'm gebruikt wordt 'm combineren met `get_url(...)` tot een absolute URL — kies de aanpak die het beste past bij hoe `sound_url` verder door de hub gebruikt wordt (zie `_play_sound`, regel 336-358, die 'm rechtstreeks als `media_content_id` doorgeeft aan `media_player.play_media`, dus daar moet 'm hoe dan ook al absoluut zijn tegen die tijd).

### 5. Home Assistant: fix de "" (empty-string) fallback-bug

In `__init__.py` regel 104: verander `opts.get(CONF_DEFAULT_SOUND_URL, "")` zodat dit **dezelfde** default gebruikt als `config_sync.py`'s `CONFIG_FIELDS` (regel 74), dus de (nu lokale, zie stap 4) `DEFAULT_SOUND_URL`-constante in plaats van `""`. Doe dezelfde controle/fix voor elke andere plek in `__init__.py` waar `self.sound_url` gezet wordt vanuit een `fallback`-dict (regel 207-208, 247-248, 549-550): als `fallback.get("sound_url")` leeg/afwezig is, moet het nooit resulteren in een lege `self.sound_url` — val in dat geval terug op de bestaande waarde (zoals nu al met de `if fallback.get(...)`-guard) of anders op de default, maar nooit stilzwijgend op `""`.

Doe dit in **beide** integratie-mappen (zie Context hierboven) — of maak van één een symlink naar de ander zodat dit soort dubbele-plekken-bugs niet meer kan terugkomen, en zeg erbij welke van de twee je als canoniek beschouwt.

### 6. Home Assistant: `_play_sound` moet nooit stil falen

In `__init__.py` regel 336-338: als `self.sound_url` na de fixes hierboven nog steeds leeg zou kunnen zijn (bv. door een edge case), log dan een duidelijke `_LOGGER.warning` vóór de `return`, zodat dit voortaan zichtbaar is in de HA-logs in plaats van volledig stil te falen zoals nu.

## Oplevercriteria

- Project bouwt succesvol (Android + de Python-integratie importeert zonder syntax-/importfouten).
- Op de telefoon: als het opgeslagen `alarm_sound_uri` verwijst naar een niet-bestaand custom geluid (simuleer dit door een custom geluid te kiezen, het onderliggende bestand handmatig te verwijderen uit `alarm_sounds/custom/`, en een alarm te laten afgaan), speelt het alarm alsnog `discoAlarmBackupAlarm`/`disco_ring_end` af — nooit stilte.
- Na een backup-restore-cyclus (backup maken, custom geluid daarna verwijderen/wijzigen, backup terugzetten) speelt het eerstvolgende alarm gegarandeerd geluid af.
- In Home Assistant: een verse/lege config-entry (geen `default_sound_url` ooit ingesteld) speelt bij een geforceerde fallback-trigger toch `discoAlarmBackupAlarm.mp3` af via de gekoppelde speaker, zonder internetverbinding nodig te hebben (GitHub-URL niet meer de bron).
- `agendaalarm_backup/` en `ha-integration/custom_components/agendaalarm_backup/` blijven identiek aan elkaar (of de één is een symlink van de ander).
- Kort verslag aan het eind van wat getest is, inclusief hoe de "verweesd custom geluid"-situatie en de "lege HA default_sound_url"-situatie concreet zijn nagebootst en gecontroleerd.
