# DayKit (Home Assistant custom integration)

Native vervanging voor de handmatig opgezette combinatie van input_helpers +
YAML-automations + losse AppDaemon-app. Eén integratie die zichzelf installeert
via de HA UI.

## Wat dit oplost t.o.v. de handmatige opzet

- Geen handmatige helpers (`input_boolean.*`, `input_datetime.*`, `input_number.*`,
  `input_text.*`) meer nodig — de integratie maakt zelf `switch`/`number`/`sensor`
  entities aan.
- De watchdog-timing gebruikt `async_track_point_in_time` in plaats van een
  `input_datetime` + tijd-trigger + losse "laatste minuut"-automation. Dat laatste
  bleek in de praktijk gevoelig voor ontbrekende helpers en timing-precisie.
- Aanwezigheid ("is de gebruiker thuis?") wordt, als je in de setup een
  `person.*`/`device_tracker.*` kiest, **live** uitgelezen in plaats van via een
  los `input_boolean` dat de telefoon moet bijhouden. Dat voorkomt de
  "stale gebruiker_thuis"-bug die in de handmatige opzet zat (de waarde bleef
  hangen op de laatste keer dat een alarm daadwerkelijk afging, wat precies
  fout gaat in het scenario dat de telefoon volledig wegvalt).
- Eén HTTP-endpoint (`/api/daykit/event`) vervangt de losse
  `alarm_app_v1`-webhook, en vereist hetzelfde long-lived token dat de app al
  gebruikt — geen apart "local_only"-webhook-gat.
- Een tweede endpoint (`/api/daykit/sound_upload`) vervangt de losse
  AppDaemon-geluid-app. **AppDaemon is dus geen vereiste meer** — belangrijk,
  want de meeste HA-gebruikers hebben dat niet geïnstalleerd.

## Installatie (huidige fase — handmatig)

1. Kopieer de map `custom_components/daykit/` naar de `custom_components/`
   map van je Home Assistant config.
2. Herstart Home Assistant.
3. Instellingen → Apparaten & diensten → Integratie toevoegen → "DayKit".
4. Kies je fallback-speaker (`media_player.*`), optioneel een aanwezigheids-entity
   (`person.*`/`device_tracker.*`), optioneel een notify-service voor de
   telefoonmelding, en standaardwaarden voor volume/interval/geluid.

Later (volgende fase): dit wordt een HACS custom repository (1-click installeren/
updaten) met een QR-code-koppelflow, zodat dit hele stappenplan wegvalt.

## HTTP API (voor de Android-app)

```
POST /api/daykit/event
Authorization: Bearer <long-lived token>

{"action": "arm", "fire_at": "2026-07-09T06:31:30+02:00",
 "fallback": {"speaker": "media_player.woonkamer", "volume": 70,
              "sound_url": "...", "interval": 10}}

{"action": "start", "mobiel_betrouwbaar": true, "fallback": {...}}

{"action": "stop"}
```

```
POST /api/daykit/sound_upload
Authorization: Bearer <long-lived token>

{"filename": "mijn_geluid.mp3", "data_base64": "<base64 bytes>"}
```
Ondersteunde extensies: mp3/wav/ogg/m4a/aac (de extensie uit `filename` blijft behouden;
alleen bij een andere/ontbrekende extensie valt dit terug op ".mp3").

```
POST /api/daykit/weather_tts_upload
Authorization: Bearer <long-lived token>

{"data_base64": "<base64 WAV bytes>"}
-> {"success": true, "url": "/local/daykit_tts/weather_tts.wav"}
```
Vluchtige audio voor het weeralarm-uitspreken: de telefoon genereert de spraak zelf
(Android TextToSpeech, geen HA tts-platform meer nodig) en upload 'm hierheen; dit
endpoint overschrijft steeds hetzelfde vaste bestand (geen manifest, geen permanente
opslag in de custom-sound-bibliotheek). De app plakt zelf een cache-busting query-param
achter de URL voordat 'ie aan `media_player.play_media` wordt meegegeven, anders kan een
smart speaker een eerder afgespeelde versie uit zijn eigen cache blijven afspelen.

```
POST /api/daykit/pair          <- BEWUST GEEN Authorization-header
{"code": "123456"}
-> {"success": true, "token": "..."} of {"error": "..."}

GET /api/daykit/qr_code.png    <- vereist WEL Authorization (dashboard-kant)
-> QR-afbeelding (PNG) van de huidige actieve koppelcode

GET  /api/daykit/config
Authorization: Bearer <long-lived token>
-> {"speaker_entity_id": "...", "speaker_mode": "BOTH", "entities": [...], ...}

POST /api/daykit/config
Authorization: Bearer <long-lived token>
{"entities": ["media_player.woonkamer", "light.hoofdlamp_hue_ambiance"]}   <- partiele patch
-> volledige, bijgewerkte config (zelfde vorm als GET)
```

## Bidirectionele config-sync (klaar)

De entiteiten-lijst en alle speaker(-per-onderdeel)/presence/uit-bed/script-instellingen zijn
vanuit **beide kanten** te bewerken en blijven met elkaar in sync via
`/api/daykit/config` (zie `config_sync.py`):

- **Vanuit Home Assistant**: Instellingen → Apparaten & diensten → DayKit →
  Configureren. Naast de Agenda-alarm-speaker (die ook de watchdog/fallback-speaker is) staan
  hier ook **losse Timer-speaker- en Weer-speaker-secties** (entiteit, modus, volume,
  volume-ongemoeid-laten) — deze voeden geen watchdog, maar spiegelen de per-onderdeel
  speakerinstellingen die de app zelf gebruikt (`SpeakerContext.TIMER`/`WEATHER`). De
  entiteiten-lijst is hier een multi-entity selector — je kan native in HA zoeken/filteren op
  naam of domein i.p.v. entity_id's over te typen. Dit scherm bewerkt exact dezelfde
  config-entry `options` die de app via `/config` leest/schrijft.
- **Vanuit de app**: elke keer dat je in de HA-instellingen van de app op "Opslaan" drukt
  (`HaSettingsViewModel.saveSettings()`), wordt de volledige huidige stand (incl. alle 3
  speaker-contexten) teruggestuurd naar HA.
- **Wijzigingen die je in HA zelf maakt** (bv. via Configureren) worden **niet** automatisch
  overgenomen — de app blijft leidend. Ze worden pas zichtbaar via een expliciete,
  door de gebruiker gestarte controle (`HaSettingsViewModel.performHaEntityUpdateCheck`), die
  per gewijzigd veld een aparte checkbox toont ("HA-wijzigingen overnemen?") en niets toepast
  totdat je op bevestigen drukt. Zo kan een wijziging in HA nooit stilletjes een net op de
  telefoon ingestelde waarde overschrijven.
- **Conflictafhandeling**: bewust simpel gehouden — wie het laatst schrijft/bevestigt wint,
  geen merge/CRDT-logica; voor dit gebruik (1 persoon, af en toe wijzigen) is dat niet nodig.
- **Nog niet meegenomen** (bewuste scope-keuze, zie `config_sync.py`): `battery_usage_per_hour`
  loopt nog via de losse, langer bestaande `input_number.mobiel_batterij_per_uur`-helper
  (eenrichtings, app → HA) en `default_sound_url` synct de kale URL, niet een losse
  naam-mapping (bv. "Boogie" → GitHub-URL) — beide zijn kandidaten voor een latere iteratie.

## Setup-code / QR-koppeling (klaar)

Vervangt het handmatig kopieren/plakken van het long-lived token. Gekozen model:
een kortdurende, eenmalige code i.p.v. het token zelf in de QR te zetten (zie
`pairing.py` voor de volledige uitleg).

1. In Home Assistant: druk op de knop-entity **"Genereer koppelcode"**
   (`button.daykit_generate_pairing_code`). Dit geeft een 6-cijferige
   code, 5 minuten geldig, en toont 'm ook via een persistent notification.
2. De sensor **`sensor.daykit_pairing_code`** toont dezelfde code als state,
   en heeft attributen `qr_payload` (JSON: `{"base_url": ..., "code": ...}`) en
   `qr_image_url` (kant-en-klare QR-afbeelding). Zet een Picture-kaart op je
   dashboard die naar `qr_image_url` wijst als je 'm wil scannen, bijvoorbeeld:
   ```yaml
   type: picture-entity
   entity: sensor.daykit_pairing_code
   image: "{{ state_attr('sensor.daykit_pairing_code', 'qr_image_url') }}"
   ```
3. In de app: Instellingen → Home Assistant → **"Koppelen (QR/code)"** → Scan QR
   (camera-toestemming wordt gevraagd), of typ het HA-adres + de 6-cijferige code
   handmatig over als scannen niet lukt/kan.
4. De app wisselt de code meteen in bij `/pair` voor een echt long-lived token
   (aangemaakt onder het HA owner-account) en slaat adres + token automatisch op
   - geen handmatige token-stap meer nodig.

De code is maar 1x te gebruiken, verloopt na 5 minuten, en wordt na 8 foute
pogingen ook ongeldig — een nieuwe code aanvragen kost 1 knopdruk.

## Android-kant (klaar)

De app roept nu deze twee endpoints aan i.p.v. de losse webhook/AppDaemon-calls:
- `HomeAssistantRepository.armBackupWatchdog(...)` — `AlarmScheduler.kt`, op het moment
  dat een SMART_ALARM met externe-speaker-backup wordt ingepland (één "arm"-call i.p.v.
  de losse reset/mobiel_betrouwbaar/backup_alarm_time/gebruiker_thuis-calls van de
  handmatige opzet).
- `HomeAssistantRepository.reportAlarmAlive(...)` — `AlarmService.kt`, zodra het alarm
  daadwerkelijk afspeelt (vervangt `sendAlarmStartToHA`).
- `HomeAssistantRepository.reportAlarmStop()` — `AlarmService.kt` `onDestroy()` (vervangt
  `sendAlarmStopToHA`).
- `HomeAssistantRepository.uploadSoundToHomeAssistant(...)` — via `SoundHaSync.kt`
  (vervangt de losse AppDaemon-upload-URL-instelling; gebruikt nu dezelfde HA-verbinding
  als de rest van de app, dus AppDaemon is niet meer nodig aan de telefoon-kant).
- `HomeAssistantRepository.uploadWeatherTtsToHomeAssistant(...)` — `WeatherAlertWorker.kt`,
  bij elk uit te spreken weeralarm: Android's eigen TextToSpeech rendert de tekst naar een
  WAV-bestand, dat geupload wordt naar `weather_tts_upload`, waarna `media_player.play_media`
  op de weer-speaker wordt aangeroepen. Vervangt de eerdere `tts.speak`-aanroep op een in HA
  geconfigureerd tts-platform — die afhankelijkheid (en de bijbehorende platform-keuze in de
  app) is hiermee vervallen.
- `PairingClient.exchangeCode(...)` (nieuw, `homeassistant/PairingClient.kt`) — QR/code
  koppelen, zie hierboven. Gebruikt bewust een eigen ongeauthenticeerde OkHttp-call
  i.p.v. de normale `HomeAssistantClient` (die altijd een token toevoegt).

Let op: dit praat alleen met een HA-instantie waar deze integratie ook echt
geïnstalleerd is (zie Installatie hierboven). Zolang dat niet gebeurd is, falen deze
calls stil (best-effort, alleen in de Android-logcat te zien) en blijft de oude
handmatige opzet (indien nog actief) het enige dat werkt.

## Nog niet gebouwd (volgende fase, zoals besproken)

- HACS-verpakking (`hacs.json` + repository-structuur) voor 1-click install/update.
- `battery_usage_per_hour` en de naam-mapping voor `default_sound_url` meenemen in de nieuwe
  `/config`-sync (zie hierboven, "Nog niet meegenomen").

## Overstappen van AgendaAlarm Backup naar DayKit (eenmalig, v0.6.0)

Het domein is gewijzigd van `agendaalarm_backup` naar `daykit`. Home Assistant kan een
integratie niet van domein laten wisselen, dus dit is bewust een schone breuk:

1. Instellingen → Apparaten & diensten → **AgendaAlarm Backup → Verwijderen**.
2. Verwijder de map `custom_components/agendaalarm_backup/` en zet
   `custom_components/daykit/` ervoor in de plaats.
3. Herstart Home Assistant.
4. Integratie toevoegen → **DayKit** → koppel de app opnieuw (QR of code).
5. Installeer de bijbehorende app-versie. Oudere app-versies praten nog tegen
   `/api/agendaalarm_backup/...` en werken dus niet meer.
6. Druk in de app één keer op **alle geluiden uploaden** (Instellingen → geluiden). De
   geluidsmap heet nu `www/daykit_sounds/`; het meegeleverde fallback-geluid komt daar
   automatisch bij het opzetten in te staan, maar je eigen geluiden moeten er opnieuw heen.
7. Loop je automatiseringen na: entity-id's beginnen nu met `daykit_` in plaats van
   `agendaalarm_backup_`. De oude map `www/agendaalarm_sounds/` mag weg.

## Migreren vanaf de handmatige opzet (als je die al had)

Zodra deze integratie draait en getest is, kun je in je bestaande config
uitschakelen/verwijderen om dubbel afgaan te voorkomen:
- De automations met alias beginnend met "Alarm |" in `automations.yaml`
  (Supervisor, Fallback audio starten/stoppen/loop, App Webhook, Start Fallback,
  Laatste minuut realtime check, notificatie-automations, veiligheidsklep).
- De helpers `input_boolean.alarm_actief`, `mobiel_betrouwbaar_voor_alarm`,
  `gebruiker_thuis` (als je nu een presence-entity kiest in de nieuwe setup),
  `alarm_fallback_actief`, `input_datetime.backup_alarm_time`, `alarmtijd`,
  `alarm_last_played`, `input_number.alarm_fallback_volume`,
  `alarm_fallback_interval`, `input_text.alarm_speaker_entity`, `alarm_sound_url`.
- De AppDaemon-app `apps/alarm_sound_sync.py` + registratie in `apps/apps.yaml`.

Bewaar een backup van je config voor je dit verwijdert.
