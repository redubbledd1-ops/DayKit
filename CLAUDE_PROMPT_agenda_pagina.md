# Opdracht voor Claude Code: nieuwe "Agenda"-pagina + betrouwbare achtergrond-sync

## Context (al onderzocht, gebruik dit — hoef je niet opnieuw uit te zoeken)

Dit is een Android/Kotlin (Jetpack Compose) project, package `com.dd.daykit`.

**Belangrijke ontdekking:** er bestaan al TWEE bijna identieke, ongebruikte schermen voor agenda-sync die niemand ooit te zien krijgt, omdat ze nergens vanuit de navigatie worden geopend:

- `app/src/main/java/com/dd/daykit/AgendaSettingsActivity.kt` — enige plek waar hij geopend wordt: een knop in `GlobalSettingsActivity.kt` (regel ~243), en `GlobalSettingsActivity` is zelf alleen bereikbaar via `ShortcutsActivity.kt` (een verborgen/interne pagina, niet de normale Instellingen-flow).
- `app/src/main/java/com/dd/daykit/AgendaSyncSettingsActivity.kt` — staat wel in `AndroidManifest.xml` maar wordt door geen enkele knop ooit gestart. Volledig dode code.

Beide schermen bevatten al: een "Nu synchroniseren"-knop, een aan/uit-toggle voor automatische sync, en knoppen voor interval (15/30/60 min), en praten met `SettingsManager.getAutoSyncEnabled/saveAutoSyncEnabled` en `SettingsManager.getPeriodicSyncIntervalMinutes/savePeriodicSyncIntervalMinutes`.

De achtergrond-sync zelf werkt via WorkManager en is functioneel al grotendeels correct opgezet:

- `CalendarSyncWorker.kt` — een `CoroutineWorker`, gepland met `PeriodicWorkRequestBuilder`, `NetworkType.NOT_REQUIRED` (werkt dus ook offline), `WORK_NAME = "calendar_sync_periodic"`, `MIN_INTERVAL_MINUTES = 15` (dit is ook de harde ondergrens van Android's WorkManager zelf — lager dan 15 min kan niet op OS-niveau).
- `AgendaWekkerApplication.kt` (regel 28-30) plant de periodieke worker opnieuw elke keer dat het app-process start, als `getAutoSyncEnabled` true is.
- `BootCompletedReceiver.kt` (regel 22-24) plant de worker ook opnieuw na een device-reboot (`ACTION_BOOT_COMPLETED`), want WorkManager's jobs overleven een reboot niet automatisch.
- `AndroidManifest.xml` heeft al `RECEIVE_BOOT_COMPLETED` permissie en de receiver geregistreerd. `build.gradle.kts` heeft `androidx.work:work-runtime-ktx:2.9.1`.

**Conclusie: de vorige poging is waarschijnlijk mislukt omdat de UI om deze functie AAN te zetten nooit bereikbaar was voor de gebruiker — niet omdat het achtergrond-mechanisme kapot is.** Het echte werk is dus: (1) de twee losse/dode schermen opruimen tot één schone "Agenda"-pagina, (2) die pagina correct ophangen in de Instellingen-navigatie, (3) de sync-knop verplaatsen, en (4) het WorkManager-mechanisme verifiëren/hardener maken zodat het ook werkt als de app volledig gesloten/geforceerd gestopt is.

Relevante bestanden om te lezen voor je begint:
- `app/src/main/java/com/dd/daykit/SettingsActivity.kt` — de hoofd-Instellingen-pagina (`SettingsScreen`, met `SettingsPortraitButtonList` en `SettingsLandscapeButtonGrid`, huidige knoppen: Onderdelen, Navigatie, Meldingen, Ontwerp, Taal, Snelkoppelingen, Back-up & Herstel).
- `app/src/main/java/com/dd/daykit/KalenderAlarmInstellingenActivity.kt` — de "Kalender Alarm Instellingen"-pagina (vertaalkey `ka_title`). Bevat rond regel 271-321 de sync-knop die moet verhuizen (roept `KalenderAlarmManualSync.run(ctx)` aan, toont laadstatus via `SyncStatusManager`, en navigeert na succesvolle sync terug naar `MainActivity`).
- `app/src/main/java/com/dd/daykit/KalenderAlarmManualSync.kt` — de daadwerkelijke synclogica (alarm herplannen + Home Assistant sync + broadcast). Dit is de volledige/juiste sync-implementatie — niet de vereenvoudigde versie die nu in `AgendaSettingsActivity.kt` staat (die doet alleen `AlarmScheduler.scheduleNextAlarm` zonder de HA-sync en broadcast).
- `app/src/main/java/com/dd/daykit/SyncStatusManager.kt` — status/laatst-gesynchroniseerd state.
- `app/src/main/java/com/dd/daykit/CalendarSyncWorker.kt`, `AgendaWekkerApplication.kt`, `BootCompletedReceiver.kt` — achtergrondmechanisme, zie boven.
- `app/src/main/java/com/dd/daykit/SettingsManager.kt` (rond regel 700-712) — bestaande opslag voor `autoSyncEnabled` en `periodicSyncIntervalMinutes`, en ook `saveLastVisitedSubSettingsPage`/`getLastVisitedSubSettingsPage` (regel ~360-420 in SettingsActivity.kt) die per Instellingen-subpagina onthoudt waar de gebruiker laatst was.
- `app/src/main/java/com/dd/daykit/LanguageManager.kt` — alle UI-teksten/vertalingen zitten hier als `"key" to t("nl", "en", "es", "pt", "de", "fr", "it", "ko", "zh", "ja")`. Bekijk regels rond 624-660 (`ka_title`, `ka_alarm_options`, `ka_ha`) en 1540-1545 (`ka_sync`, `ka_syncing`) als voorbeeld van de bestaande structuur/toon.
- `app/src/main/AndroidManifest.xml` — activity-declaraties rond regel 49-69.

## Wat te bouwen

### 1. Eén schone "Agenda"-pagina, correct opgehangen onder Instellingen

- Kies **één** van de twee bestaande activities als basis (advies: `AgendaSettingsActivity.kt`, want die is al het minst rommelig) en verwijder de andere (`AgendaSyncSettingsActivity.kt`) inclusief de manifest-entry — geen dubbele dode code laten staan.
- Voeg een nieuwe knop **"Agenda"** toe onderaan de hoofd-Instellingen-pagina (`SettingsScreen` in `SettingsActivity.kt`), dus in `SettingsPortraitButtonList` ná "Back-up & Herstel", en in `SettingsLandscapeButtonGrid` ook als laatste item (herverdeel de landscape-kolommen zodat het er netjes uitziet, bv. 4+4 in plaats van 3+4). Deze knop opent de Agenda-pagina en volgt hetzelfde patroon als de andere knoppen: `SettingsManager.saveLastVisitedSubSettingsPage(ctx, "AGENDA")` bij het openen, en een `"AGENDA" -> ...` case toevoegen aan `navigateToLastSubSetting` in `SettingsScreen`.
- **Belangrijk:** deze knop hoort NIET thuis in `KalenderAlarmInstellingenActivity.kt` — dat blijft een apart scherm (Kalender Alarm Instellingen ≠ Agenda-instellingen). De Agenda-knop komt alleen op de hoofd-Instellingen-pagina.
- Voeg een nieuwe vertaalkey toe in `LanguageManager.kt` voor het knoplabel (bv. `"nav_agenda"`), consistent qua stijl met de bestaande `t(...)`-vertalingen voor alle 10 talen (nl: "Agenda", en: "Calendar", etc. — check hoe andere agenda-gerelateerde strings al vertaald zijn, bv. rond regel 219 `screen_agenda_alarm`, voor consistente woordkeuze per taal).
- Verwijder de nu overbodige knop naar `AgendaSettingsActivity` in `GlobalSettingsActivity.kt` als die naar hetzelfde scherm zou wijzen als de nieuwe Instellingen-knop (of laat 'm staan als dat scherm bewust ook via de shortcuts-route bereikbaar moet blijven — maak zelf een redelijke keuze en leg die kort uit in je samenvatting).

### 2. Verplaats de sync-knop van Kalender Alarm Instellingen naar de Agenda-pagina

- Haal de "Nu synchroniseren"-knop + foutmelding-weergave (regels ~271-321 in `KalenderAlarmInstellingenActivity.kt`, gebruikt `ka_sync`/`ka_syncing`, `KalenderAlarmManualSync.run(ctx)`, `SyncStatusManager`) volledig weg uit `KalenderAlarmInstellingenScreen`.
- Zet deze knop (met dezelfde volledige syncflow — dus `KalenderAlarmManualSync.run(ctx)`, niet de vereenvoudigde losse implementatie die nu in `AgendaSettingsActivity.kt` staat) op de nieuwe Agenda-pagina. Behoud het gedrag: laadstatus tonen tijdens sync, foutmelding tonen bij falen, en na succesvolle sync terugnavigeren zoals het origineel deed.
- Check of er nog andere losse verwijzingen naar deze knop/tekst zijn (bv. in `KalenderAlarmTriggerOnboardingCard.kt` of vergelijkbare onboarding-teksten die naar "de sync-knop op deze pagina" verwijzen) en werk die bij.

### 3. "Agenda update check" — aan/uit + configureerbare interval

Op de Agenda-pagina, gebruik de al bestaande (nu dode) UI-onderdelen uit `AgendaSettingsActivity.kt`/`AgendaSyncSettingsActivity.kt` als basis, en zorg dat:

- Er een duidelijke aan/uit-schakelaar is voor automatische achtergrond-check (gebruik `SettingsManager.getAutoSyncEnabled`/`saveAutoSyncEnabled`).
- **Alleen als de schakelaar AAN staat**, een interval-instelling zichtbaar is (gebruik `SettingsManager.getPeriodicSyncIntervalMinutes`/`savePeriodicSyncIntervalMinutes`) — de bestaande 15/30/60 min-presets zijn een prima basis, voeg gerust een paar praktische opties toe (bv. ook 2 uur, 4 uur, 6 uur) zolang de ondergrens 15 minuten blijft (`CalendarSyncWorker.MIN_INTERVAL_MINUTES`, harde WorkManager-limiet).
- Bij opslaan: `CalendarSyncWorker.schedule(context, ExistingPeriodicWorkPolicy.REPLACE)` als aan, `CalendarSyncWorker.cancel(context)` als uit — dit patroon bestaat al, hergebruik het.
- Toon de laatste sync-status (`SyncStatusManager.getStatusMessage`) op deze pagina, zoals de bestaande schermen al deden.

### 4. Zorg dat de achtergrond-check ECHT blijft draaien, ook als de app niet open/online is of geforceerd gestopt wordt

Dit is het onderdeel waar eerdere pogingen op vast liepen, dus extra aandacht hier:

- Verifieer dat `CalendarSyncWorker` + `AgendaWekkerApplication.onCreate` + `BootCompletedReceiver` correct samenwerken zoals hierboven beschreven (dit lijkt al te kloppen, maar test het: zet de sync aan, forceer de app te stoppen (niet alleen naar achtergrond, echt "force stop" of swipe uit recents), en verifieer via `adb shell dumpsys jobscheduler` of Logcat (`CalendarSyncWorker` tag) dat de worker na het ingestelde interval alsnog draait).
- Voeg — als hardening, niet als vervanging van WorkManager — een verzoek toe om vrijstelling van batterij-optimalisatie (`Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)`), met duidelijke uitleg aan de gebruiker waarom dat nodig is, zodat agressieve OEM battery-killers (Samsung/Xiaomi/Huawei-achtige "app sluiten na X minuten"-gedrag) de periodieke sync niet alsnog killen. Zet dit als losse, duidelijk zichtbare knop/uitleg op de Agenda-pagina, niet verplicht afgedwongen.
- Leg in de UI kort uit dat "helemaal uit" (toestel volledig uitgeschakeld) niet mogelijk is voor achtergrondwerk op geen enkel platform — de check hervat automatisch zodra het toestel weer aan staat, dankzij de boot-receiver die al aanwezig is.
- `NetworkType.NOT_REQUIRED` staat al goed ingesteld in `CalendarSyncWorker` — agenda's op het toestel (via `CalendarHelper.kt`/`ContentResolver`) zijn immers lokaal leesbaar zonder internet; behoud dit.

## Oplevercriteria (laat Claude Code dit zelf verifiëren voor die klaar is)

- Project bouwt succesvol (`./gradlew assembleDebug` of vergelijkbaar).
- Vanuit de hoofd-Instellingen-pagina is een nieuwe "Agenda"-knop zichtbaar en werkt in zowel portrait als landscape.
- Kalender Alarm Instellingen bevat geen sync-knop meer; die functionaliteit + status/foutweergave zit nu volledig op de Agenda-pagina.
- Er is precies één Agenda-activity meer (geen dubbele/dode `AgendaSyncSettingsActivity`/`AgendaSettingsActivity`-verwarring), manifest is opgeschoond.
- Aan/uit-schakelaar + interval-instelling werken en persisten via `SettingsManager`; `CalendarSyncWorker` wordt aantoonbaar (via logcat of dumpsys) elke ingestelde X minuten uitgevoerd, ook nadat de app volledig is afgesloten.
- Alle nieuwe UI-teksten zijn vertaald in alle bestaande talen in `LanguageManager.kt`, in dezelfde stijl/toon als bestaande strings.
- Kort verslag van wat er getest is (build, navigatie, background-run-verificatie) aan het eind.
