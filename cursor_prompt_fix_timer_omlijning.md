# Cursor-implementatieprompt: fix omlijning timer 2/3-notificaties

Plak dit in Cursor (Agent/Composer-modus, met schrijfrechten) in de root van dit project (CalenderAlarm). Dit keer WEL implementeren — het onderzoek is al gedaan (zie `cursor_prompt_notificatie_omlijning.md` en het rapport dat daaruit kwam).

---

## Bevestigde diagnose (niet opnieuw onderzoeken)

De hoofdoorzaak: `setColorized(true)` wordt door Android alleen toegepast op een notificatie die **de** notificatie van een actieve foreground service is. De primaire timer gebruikt `PopupNotificationFoundation.startForeground(this, NOTIFICATION_ID, initial)` (`TimerPopupForegroundService.kt`, rond regel 208-209) en wordt daardoor wél volledig gekleurd. Timer 2/3 worden gepost via een gewone `nm.notify(id, notification)` (`syncExtraTimerNotifications()`, regel 241-267 in `TimerPopupForegroundService.kt`) — géén FGS-koppeling, dus `setColorized` wordt genegeerd door het systeem. Alleen `poc_root` (via `RemoteViews.setInt(..., "setBackgroundColor", ...)`) krijgt de juiste kleur; de rest van de systeem-template rond de custom view blijft ongekleurd, wat zichtbaar is als rand — versterkt op HyperOS/MIUI door hun "Notification spotlight"-kaartstijl.

**Belangrijke beperking om te respecteren:** dit is bewust losgelaten in het verleden. Zie het commentaar in de code:

```kotlin
// Gewoon notify() — startForeground() met wisselende ids bleek onbetrouwbaar (de
// meldingen verdwenen soms helemaal). Elke extra timer heeft nu wel zijn EIGEN
// notificatiekanaal (zie buildExtraTimerNotification), dat is de aanpak tegen de
// grijze systeemrand die niet ten koste gaat van de zichtbaarheid.
```

Het probleem zat in **hetzelfde service-object herhaaldelijk `startForeground()` laten aanroepen met wisselende notificatie-ids** — Android ondersteunt maar één "de" FGS-notificatie per service-instantie, en switchen breekt dat. De fix hieronder omzeilt dat door **elke extra timer-slot zijn eigen, vaste Service-klasse en vast notificatie-id** te geven (er zijn er maximaal 2, zie `ExtraTimerManager.MAX_EXTRA_TIMERS`), zodat er nooit binnen één service-instantie van id gewisseld wordt.

## Opdracht

Implementeer onderstaande architectuurwijziging, met vangnet, en verifieer dat het stabiel is (geen verdwijnende notificaties, geen crashes, geen ANRs).

### 1. Twee nieuwe, dunne foreground-service-klassen (één per extra slot)

Maak `ExtraTimerForegroundService0.kt` en `ExtraTimerForegroundService1.kt` (of één abstracte basisklasse `ExtraTimerForegroundServiceBase(slotIndex: Int)` met twee minimale subklassen — vermijd codeduplicatie waar mogelijk, maar Android vereist aparte concrete Service-klassen om aparte proces-/lifecycle-identiteit per slot te garanderen).

Elke service:
- Doet **niets** aan timer-logica — die blijft volledig in `TimerPopupForegroundService` / `GlobalTimerManager` / `ExtraTimerManager`. Deze services zijn puur **notificatie-hosts** zodat `setColorized(true)` effect heeft.
- Bij start: bouwt de bestaande notificatie via `TimerPopupForegroundService`'s bestaande `buildExtraTimerNotification(slot, index)`-logica (maak deze functie `internal`/toegankelijk, of verplaats de build-logica naar een gedeelde plek zoals `PopupNotificationFoundation` zodat beide services 'm kunnen aanroepen zonder duplicatie) en roept `startForeground(fixedId, notification)` precies één keer aan met het bestaande vaste id (`EXTRA_NOTIFICATION_ID_BASE + index`, dus 9020/9021 — hergebruik de bestaande constante, verzin geen nieuwe ids).
- Bij updates (elke tick / state change): gebruik `NotificationManager.notify(fixedId, updatedNotification)` met **hetzelfde** id als bij `startForeground` — dat is toegestaan en breekt de FGS-koppeling niet, zolang het id niet wijzigt.
- Bij stoppen (timer klaar/geannuleerd/geswiped): `stopForeground(STOP_FOREGROUND_REMOVE)` gevolgd door `stopSelf()`.
- Reuse het bestaande kanaal per slot: `PopupNotificationFoundation.extraTimerChannelId(index)` / `ensureExtraTimerChannel(context, index)` — niet wijzigen.

### 2. AndroidManifest.xml

Registreer beide nieuwe services naast de bestaande `TimerPopupForegroundService`-entry (regel ~198-205), met hetzelfde patroon:

```xml
<service
    android:name=".ExtraTimerForegroundService0"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Persistent extra timer countdown popup notification" />
</service>
<service
    android:name=".ExtraTimerForegroundService1"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Persistent extra timer countdown popup notification" />
</service>
```

### 3. `syncExtraTimerNotifications()` aanpassen

In plaats van rechtstreeks `nm.notify(id, notification)` aan te roepen (regel 250-256), moet deze functie:
- Bij het **eerste** keer zichtbaar worden van een slot (index niet in `shownExtraNotificationIndices`): de bijbehorende `ExtraTimerForegroundServiceN` starten via `ContextCompat.startForegroundService(context, intent)`, met de notificatie-data als extras (of laat de service zelf de state uit `ExtraTimerManager.timers[index]` lezen — bepaal zelf wat robuuster is, maar zorg dat de service bij elke herstart een consistente state kan opbouwen, ook na process death).
- Bij **volgende** syncs voor een al-lopend slot: **niet opnieuw starten**, maar de bijgewerkte notificatie posten met `NotificationManagerCompat.from(context).notify(fixedId, updatedNotification)` — dit mag van buiten de service-klasse zelf, zolang het id gelijk blijft aan wat die service als FGS-id gebruikt.
- Bij het **verdwijnen** van een slot (niet meer in `liveIndices`): de bijbehorende service expliciet stoppen (`context.stopService(Intent(context, ExtraTimerForegroundServiceN::class.java))` of een `ACTION_STOP`-intent sturen die intern `stopForeground` + `stopSelf` aanroept) — niet alleen `nm.cancel()`, anders blijft de service als "foreground zonder zichtbare notificatie" hangen.

### 4. Vangnet — verplicht, niet optioneel

Omdat het startpad hierboven al eerder problemen gaf (verdwijnende meldingen), moet elke `startForegroundService()`-aanroep in een `try/catch` staan. Bij falen (bv. `ForegroundServiceStartNotAllowedException` op Android 12+ als de app niet in een uitzonderingscategorie valt, of een andere `RemoteException`/`IllegalStateException`):
- Log de fout duidelijk (zelfde `Log.w`/`Log.e`-stijl als de rest van het bestand).
- Val terug op de **huidige werkende aanpak**: gewone `nm.notify(id, notification)` zonder FGS-koppeling (dus zonder colorized-effect, maar wél zichtbaar). De gebruiker moet nooit een timer kwijtraken, ook niet als de kleur dan niet klopt.

### 5. Wat dit WEL en NIET oplost

- Lost op: timer 2/3 krijgen dezelfde systeem-colorized-behandeling als timer 1, dus de kaart wordt net zo consistent gevuld/gekleurd als de primaire timer.
- Lost NIET op: de HyperOS/MIUI "Notification spotlight" kaartrand rond álle notificaties (inclusief timer 1, die deze ook al heeft) — dat is OEM-chrome zonder publieke API om te overschrijven. Na deze fix moeten timer 1, 2 en 3 er wel **consistent** hetzelfde uitzien (allemaal met of allemaal zonder die subtiele OEM-rand), in plaats van dat 2/3 er duidelijk anders/lelijker uitzien dan 1.

### 6. Verificatie (verplicht voor je dit als klaar beschouwt)

- Build slaagt zonder warnings over de nieuwe services.
- Start 3 timers tegelijk (primair + 2 extra), laat ze allemaal lopen, en vergelijk visueel of timer 2/3 nu net zo gekleurd zijn als timer 1.
- Stress-test exact het oude faalscenario: timers snel achter elkaar starten/pauzeren/hervatten/stoppen (ook door elkaar heen, niet alleen na elkaar) en controleren dat er geen meldingen spontaan verdwijnen. Dit is de belangrijkste regressietest.
- Force-stop de app en start 'm koud opnieuw terwijl er (volgens `ExtraTimerManager`-state) nog timers actief hadden moeten zijn — controleer dat de sync-logica de extra services correct herstart of netjes leeg laat, zonder crash.
- Scherm vergrendelen/ontgrendelen en app naar achtergrond sturen tijdens actieve extra timers — controleer dat de FGS'en niet door het systeem gekilld worden.
- `adb shell dumpsys notification --noredact | grep -A 30 "timer_extra"` — controleer dat de extra-timer-notificaties nu als foreground-service-notificatie geregistreerd staan.
- Rapporteer aan het eind kort: wat je hebt aangepast, of de stress-test stabiel was, en of het vangnet ooit is getriggerd tijdens testen.

Voer de wijzigingen door, test ze zoals hierboven beschreven, en geef een kort verslag — geen aparte losse investigate-only stap meer nodig.
