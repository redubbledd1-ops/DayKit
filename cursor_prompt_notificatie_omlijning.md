# Cursor-onderzoeksprompt: bron van de omlijningskleur bij timer 2/3-notificaties

Plak onderstaande opdracht in Cursor (Agent/Composer-modus) met de root van dit project (CalenderAlarm) open.

---

## Opdracht

NIET fixen. Alleen onderzoeken en rapporteren waar de gekleurde/grijze rand (omlijning) vandaan komt rond de notificatiekaarten van de 2e en 3e timer in het Android-meldingenscherm / media-centrum. De achtergrondkleur zélf wordt al correct gezet door de app — het gaat specifiek om de rand eromheen die niet overschreven kan worden.

## Al bevestigde context (niet opnieuw checken)

- Relevante bestanden:
  - `app/src/main/java/com/dd/daykit/TimerPopupForegroundService.kt` — `buildTimerNotification()`, `buildExtraTimerNotification()`, `syncExtraTimerNotifications()`
  - `app/src/main/java/com/dd/daykit/PopupNotificationFoundation.kt` — `alarmTrayCompactBuilder()`, `pocCompactStyleBuilder()`, `buildPocCompactRemoteViews()`, `resolveThemeColors()`, `ensureWakeCapableChannel()`, `ensureExtraTimerChannel()` / `extraTimerChannelId()`
  - `app/src/main/res/layout/notification_poc_compact.xml`
- De root-view (`poc_root`) van de custom notificatie-layout heeft **geen** eigen `android:background` of `<stroke>` in XML. De achtergrondkleur wordt runtime gezet via `RemoteViews.setInt(poc_root, "setBackgroundColor", colors.backgroundColor)`. Er zit dus geen rand in onze eigen layout/drawables (al gecheckt, incl. `poc_circle_button_bg.xml`).
- Elke extra timer (2e/3e) heeft al een eigen `NotificationChannel` (`timer_extra_0`, `timer_extra_1`, ...) om te voorkomen dat Android/de fabrikantschil ze als "dezelfde stroom" bundelt — dat loste eerder een grijze header op bij bundeling, maar de rand bij losse timer 2/3-kaarten blijft bestaan.
- `.setStyle(NotificationCompat.DecoratedCustomViewStyle())` wordt **nergens** in de codebase aangeroepen, terwijl `.setCustomContentView()` overal wordt gebruikt in combinatie met `.setColorized(true)`. Zonder decorated style kan het systeem alsnog eigen sjabloon-chrome (incl. rand/header) om de custom RemoteViews heen tekenen.
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` (zie `app/build.gradle.kts`).
- Screenshot van het probleem toont een niet-stock Android meldingenscherm (Nederlandse datumnotatie, media-centrum-achtige stijl) — vermoedelijk een fabrikantschil (bv. MIUI/HyperOS-achtig), wat relevant is omdat dat soort schillen bekend staat om het forceren van een eigen systeem-omlijningskleur rond notificaties, los van wat de app instelt.

## Te onderzoeken hypotheses (rangschik van waarschijnlijk naar onwaarschijnlijk, met onderbouwing)

1. Ontbreken van `setStyle(DecoratedCustomViewStyle())` op de `Notification.Builder` in `buildExtraTimerNotification()` / `buildTimerNotification()` — zorgt dit ervoor dat het systeem op targetSdk 35 alsnog een eigen template-rand tekent rond de custom RemoteViews, vooral in combinatie met `setColorized(true)`?
2. `setGroup("timer_notif_extra_$index")` zonder een group-summary-notificatie — genereert het systeem (stock of fabrikantschil) hierdoor automatisch een group-header/rand, ook al heeft elke timer een uniek kanaal?
3. De `NotificationChannel`-configuratie in `ensureWakeCapableChannel()` (importance via `WakeMobilePolicy.channelImportance()`, `setBypassDnd(true)`, `lockscreenVisibility = VISIBILITY_PUBLIC`) — forceert een hoge importance een systeem-accentkleur/rand op dit type meldingenscherm?
4. targetSdk 35 (Android 15) — zijn er nieuwe regels voor custom notification layouts (verplichte systeem-actierij, Material You-tint op "cards") die de app niet kan overschrijven?
5. Fabrikantschil-gedrag: is dit een bekend patroon (zoek naar publieke bug reports / Stack Overflow / XDA-threads over bv. "MIUI notification border color", "HyperOS notification outline", "notification card border color Android") waarbij de rand wordt afgeleid van wallpaper-accentkleur of systeemthema en niet overschreven kan worden door de app?

## Gewenste output

Een kort rapport (GEEN codewijzigingen):

- Meest waarschijnlijke oorzaak, met onderbouwing.
- Of dit binnen de app oplosbaar is (en zo ja: welke API/aanpak), of dat het een systeem-/fabrikantsbeperking is die de app niet kan overschrijven.
- Concrete vervolgstappen om de hypothese te bevestigen (bv. testen op een stock-Android emulator zonder fabrikantschil om te zien of de rand daar ook verschijnt, of tijdelijk `DecoratedCustomViewStyle` toevoegen op een test-build om het effect te observeren zonder het meteen als definitieve fix te committen).

Voer geen wijzigingen door — alleen onderzoek en rapport.
