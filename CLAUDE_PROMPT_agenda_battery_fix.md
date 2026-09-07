# Opdracht voor Claude Code: batterij-knop laten werken op Oppo + UI opschonen

## Context

Bestand: `app/src/main/java/com/dd/daykit/AgendaSettingsActivity.kt` (de Agenda-pagina, bereikbaar via Instellingen).
Knop-logica zit in: `app/src/main/java/com/dd/daykit/PermissionSettingsNavigator.kt`, functie `openBatteryOptimizationSettings()` (regel ~107-131), die via `openFirstAvailable()` een lijst van settings-intents afgaat totdat er eentje resolvet.

Er was al een bug gefixt (missende `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />` in `AndroidManifest.xml`, inmiddels toegevoegd). Op een normaal/Pixel-achtig toestel werkt de knop nu. **Op een Oppo-toestel (ColorOS) doet de knop nog steeds niks.**

Reden: ColorOS (gebruikt door Oppo, Realme en OnePlus — gedeelde codebase) negeert vaak de standaard AOSP-intent `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` / `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, of laat 'm resolven naar een leeg/nutteloos scherm. ColorOS heeft zijn eigen aparte instellingenschermen voor achtergrondgedrag ("Startup Manager" / "App-batterijbeheer" / "Auto-launch toestaan") die niet via de standaard Android-API bereikbaar zijn — die moet je met expliciete package/component-namen benaderen.

## Wat te doen

### 1. Batterij-knop ook laten werken op Oppo/ColorOS

In `openBatteryOptimizationSettings()` in `PermissionSettingsNavigator.kt`: voeg vóór de bestaande generieke candidates een paar ColorOS-specifieke candidates toe, maar **alleen** geprobeerd als het toestel daadwerkelijk Oppo/Realme/OnePlus is (check `Build.MANUFACTURER`/`Build.BRAND`, case-insensitive, bevat "oppo", "realme" of "oneplus"). Gebruik hetzelfde veilige patroon dat er al is (`resolveActivity`-check + try/catch + doorvallen naar volgende candidate, zoals `openFirstAvailable` al doet) zodat een niet-bestaand scherm op een andere ColorOS-versie nooit een crash geeft.

Bekende ColorOS component-namen om als candidates te proberen (probeer ze in deze volgorde, ze verschillen per ColorOS-versie dus meerdere pogingen is nodig):
- `ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")`
- `ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")`
- `ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")`
- `ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")`

Bouw deze als losse `SettingsCandidate` entries (zelfde `data class SettingsCandidate(label, buildIntent)` die er al is) met `Intent().setComponent(...)`, zet ze vóór de bestaande `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_APP`-candidate in de lijst als het toestel Oppo/Realme/OnePlus is. Laat de bestaande generieke candidates + `appDetailsCandidate` als fallback staan zoals ze nu zijn — die blijven de vangnet voor alle andere merken.

Test/verifieer via Logcat (tag `PermissionSettings`, die logt nu al welke candidate geopend is) dat er op een Oppo-toestel daadwerkelijk een van deze schermen opent. Zoek zelf ook even op of de exacte component-namen recent nog kloppen voor de ColorOS-versie die beschikbaar is om te testen (dit soort namen verandert soms tussen ColorOS-versies) en pas aan waar nodig.

### 2. Uitleg-tekst boven de batterij-knop veel korter maken

Vertaalkey `agenda_battery_desc` in `LanguageManager.kt` (regel ~670-673) is nu een lange alinea over Samsung/Xiaomi/Huawei die agressief apps sluiten. Maak dit één korte zin, bv. NL: "Voorkomt dat het toestel de achtergrond-check uitschakelt." (of vergelijkbaar, kort en to-the-point — geen opsomming van merken meer nodig). Vertaal deze kortere versie in alle bestaande talen in dezelfde `t(...)`-structuur (nl, en, es, pt, de, fr, it, ko, zh, ja, ru, ar, hi, tr, pl, id, uk, vi — zelfde volgorde/talen als de rest van het bestand).

### 3. Scheidingslijntjes weghalen

In `AgendaSettingsActivity.kt` staan twee `HorizontalDivider(...)` calls die weg moeten, zonder dat er een lelijk groot leeg gat overblijft:

- Regel ~179-181: `Spacer(24dp)` + `HorizontalDivider` + `Spacer(24dp)` tussen de sync-knop/status en "Automatisch synchroniseren". Vervang dit blok door één enkele `Spacer(Modifier.height(32.dp))`.
- Regel ~267-269: zelfde patroon tussen de interval-opties (15/30/60/2u/4u/6u) en het batterijoptimalisatie-blok. Vervang ook door één `Spacer(Modifier.height(32.dp))`.

Resultaat: geen enkele `HorizontalDivider` meer op deze pagina, alleen nette witruimte tussen de secties (sync, auto-sync+interval, batterij-hardening, annuleren-knop).

## Oplevercriteria

- Project bouwt succesvol.
- Op een Oppo/ColorOS-toestel opent de batterij-knop daadwerkelijk een bruikbaar instellingenscherm (startup manager / batterijbeheer / ignore-optimizations — wat er ook beschikbaar is op dat toestel), geverifieerd via Logcat tag `PermissionSettings`.
- Op niet-Oppo-toestellen verandert er niets aan het bestaande (werkende) gedrag.
- `agenda_battery_desc` is overal een korte zin, geen lange alinea meer, in alle bestaande talen.
- Geen `HorizontalDivider` meer in `AgendaSettingsScreen`; secties staan nog steeds visueel duidelijk gescheiden door witruimte.
- Kort verslag van wat getest is aan het eind.
