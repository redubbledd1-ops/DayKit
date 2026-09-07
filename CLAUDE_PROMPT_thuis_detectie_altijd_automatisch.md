# Opdracht voor Claude Code: thuis-detectie volledig automatisch, geen instelling/schakelaar meer

## Context

Vervolg op de net gebouwde ping-detectie (`binary_sensor.py`'s `PhoneReachableBinarySensor`, `last_known_phone_ip` op de hub, `AlarmOutputDecisionEngine.getPhoneIpAddress`). Die werkt technisch goed, maar zit nog op twee plekken onnodig vast aan andere instellingen — en dat moet er allemaal uit. Het doel: zodra de app met HA gekoppeld is, wordt het IP altijd uitgewisseld en de thuis-status altijd bijgehouden, zonder dat de gebruiker daar iets voor hoeft aan/uit te zetten of te configureren.

### 1. `armBackupWatchdog`/`reportAlarmAlive` (en dus `phone_ip`) zijn nog voorwaardelijk

Beide aanroepen zitten achter:
```kotlin
if (haSettings.externalSpeakerMode != ExternalSpeakerMode.DISABLED &&
    !haSettings.externalSpeakerEntityId.isNullOrBlank())
```
— `AlarmScheduler.kt` regel 216-217 (rond de `armBackupWatchdog`-call, regel 233-242) en `AlarmService.kt` regel 408-410 (rond de `reportAlarmAlive`-call, regel 424-432). Dat is een voorwaarde die met de externe-speaker-functie te maken heeft, niet met de ping/IP-uitwisseling — die twee horen niet aan elkaar vast te zitten. `HomeAssistantRepository.armBackupWatchdog`/`reportAlarmAlive` hebben zelf al een interne "geen HA-verbinding, overslaan"-guard (zie de bestaande `Log.w(...No HA connection, skipping...)`-regels) — dus de buitenste voorwaarde in `AlarmScheduler.kt`/`AlarmService.kt` is voor dit doel overbodig en kan gewoon weg. Roep deze twee functies dus onvoorwaardelijk aan (of hooguit een simpele check dat er een HA-koppeling actief is, bv. `activeBaseUrl`/`longLivedToken` niet leeg — géén check op speaker-instellingen).

Let op: de `fallback`-payload (speaker/volume/sound/interval) mag gewoon meegestuurd blijven zoals nu, ook als die leeg is — `AlarmBackupHub.async_arm`/`async_report_alive` aan de HA-kant behandelen een lege/`None`-speaker al veilig (`fallback.get("speaker") or self.speaker_entity_id`, laat bestaande waarde staan). Er hoeft dus niets aan de HA-kant te veranderen voor dit punt.

### 2. Geen "thuis detectie aan/uit" meer — altijd actief

`HaSettingsActivity.kt`: `isPresenceDetectionEnabled` (regel 89, afgeleid van `!selectedPresenceEntityId.isNullOrBlank()`), de schakelaar die dit aan/uit zet (rond regel 732-739, opent nu `PresenceModal` bij aanzetten / maakt `selectedPresenceEntityId` leeg bij uitzetten), en `PresenceModal` zelf (regel 644-660) — dit hele "aan/uit"-concept moet weg. Presence-detectie (`isUserAtHome()`) draait altijd, ongeacht of er al een specifieke entiteit gekozen is — bij niks gekozen valt het gewoon terug op de bestaande fail-safe (aannemen dat je thuis bent) of, na de vorige prompt, op de auto-adopt (ping-sensor / enkele `person.`-entiteit).

Vervang de aan/uit-schakelaar + verplichte-picker-flow door: een simpel, alleen-lezen statusregel "Thuis: Waar/Onwaar" die de actuele live status toont (gebaseerd op dezelfde check als `AlarmOutputDecisionEngine.isUserAtHome()`, of rechtstreeks de state van de huidige presence-entiteit). Laat een kleine "wijzig"-actie staan die naar de bestaande entiteit-picker (`PresenceModal` of vergelijkbaar) leidt, voor het randgeval dat iemand de automatisch gekozen entiteit toch wil overschrijven — maar zonder een "uit"-stand; hooguit "welke bron", nooit "geen bron actief zoals ingesteld door gebruiker".

### 3. `presenceExpectedState` ("waarde als je thuis bent") als los invulveld mag weg

Dit veld (`HomeAssistantSettings.presenceExpectedState`, default `"home"`) bestaat omdat de oude aanpak een willekeurige entiteit met een losse, door de gebruiker ingevulde tekst moest vergelijken. Nu de primaire/automatische bron een nette boolean `binary_sensor` is (de ping-sensor, `is_on` true/false, HA toont 'm als "Verbonden"/"Niet verbonden"), is dat handmatige tekstveld niet meer nodig voor het automatische pad.

Vervang de gebruikersinvoer door een automatisch afgeleide verwachte waarde, gebaseerd op het domein van de (automatisch of eventueel nog handmatig gekozen) entiteit — bv.:
- `binary_sensor.*` → verwacht `"on"`
- `person.*` / `device_tracker.*` → verwacht `"home"`
- overige domeinen (het randgeval van een handmatig gekozen, ongebruikelijke entiteit) → een redelijke default (bv. `"on"`), dit hoeft niet perfect te zijn, het gaat om het wegnemen van een instelveld voor het normale geval.

Doe dit zowel in `AlarmOutputDecisionEngine.isUserAtHome()` (app-kant, regel 134-163, i.p.v. `settings.presenceExpectedState` te gebruiken) als in de waarde die naar HA gepusht wordt voor `presence_expected_state` (`buildConfigPatchJson` in `HaSettingsViewModel.kt`) — HA's eigen `AlarmBackupHub.is_user_home()` (`__init__.py`) gebruikt dit veld namelijk ook zelf, onafhankelijk van de telefoon, voor de fail-safe watchdog-tak. Blijf dus wél een waarde synchroniseren naar HA (automatisch afgeleid, niet meer door de gebruiker getypt) zodat die kant blijft werken — alleen het handmatige invulveld in de app-UI verdwijnt.

Verwijder het bijbehorende tekstinvoerveld uit de instellingen-UI (zoek in `HaSettingsActivity.kt` naar waar `presenceExpectedState` als tekstveld getoond wordt).

## Wat te doen

1. Verwijder de `externalSpeakerMode`-voorwaarde rond de `armBackupWatchdog`-call in `AlarmScheduler.kt` en de `reportAlarmAlive`-call in `AlarmService.kt` — laat ze onvoorwaardelijk vuren (op z'n hoogst een "is er een HA-koppeling"-check, geen speaker-gerelateerde voorwaarde).
2. Verwijder `isPresenceDetectionEnabled` als aan/uit-concept in `HaSettingsActivity.kt`: geen schakelaar meer, presence-detectie staat conceptueel altijd aan. Bouw een alleen-lezen "Thuis: Waar/Onwaar"-statusregel die de live status toont. Houd een simpele "wijzig entiteit"-actie over voor het handmatige-override-randgeval, gekoppeld aan de bestaande picker.
3. Verwijder het handmatige `presenceExpectedState`-invulveld uit de UI; vervang door automatische, domein-gebaseerde afleiding op zowel de app-kant (`AlarmOutputDecisionEngine.isUserAtHome()`) als in wat er naar HA gepusht wordt (`buildConfigPatchJson`), zodat HA's eigen fail-safe watchdog-logica (`AlarmBackupHub.is_user_home()`) een correcte waarde blijft ontvangen.
4. Zorg dat dit samenspeelt met de auto-adopt-logica uit de vorige prompt (`tryAutoAdoptPresenceEntity`) — die bepaalt nog steeds *welke* entiteit gebruikt wordt (ping-sensor of enkele `person.`-entiteit), dit werk gaat over het feit dát presence-detectie altijd draait en geen handmatige aan/uit/waarde-instelling meer nodig heeft.
5. Controleer of er nog ergens anders in de app (bv. losse per-trigger "Smart Alarm"-instellingen, `TriggerBehaviorModels.kt`'s `checkUserAtHome`/`userPresenceEntityId`) een vergelijkbare aan/uit-schakelaar voor thuis-detectie heeft die dezelfde behandeling verdient — dat is een ánder, per-trigger mechanisme (niet de globale HA-instelling die deze prompt aanpakt), dus verander dat niet zonder het even te noemen in je verslag, maar meld het wel als je het tegenkomt.

## Oplevercriteria

- Project bouwt succesvol.
- Test: met `externalSpeakerMode = DISABLED` (of BOTH, maakt niet meer uit) en zonder dat er ooit een presence-entiteit gekozen is, wordt bij het plannen van een alarm alsnog `phone_ip` naar HA gestuurd en blijft de ping-sensor bijwerken — geen enkele instelling kan dit meer blokkeren.
- Test: de instellingen-UI toont geen aan/uit-schakelaar en geen "waarde als je thuis bent"-tekstveld meer voor de normale flow; wél een duidelijke, actuele "Thuis: Waar/Onwaar"-statusregel.
- Test: HA's eigen `binary_sensor`/`person.`-gebaseerde fail-safe watchdog (het pad dat draait als de telefoon niet reageert) werkt nog steeds correct met de automatisch afgeleide `presence_expected_state`-waarde — niet kapot door het weghalen van het handmatige veld.
- Kort verslag aan het eind, inclusief of er nog een vergelijkbare aan/uit-schakelaar bij de per-trigger Smart Alarm-instellingen is aangetroffen (zie punt 5) zodat daar in een vervolgstap naar gekeken kan worden.
