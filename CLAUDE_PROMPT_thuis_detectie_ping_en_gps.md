# Opdracht voor Claude Code: thuis-detectie makkelijker en betrouwbaarder maken (ping + GPS-suggestie)

## Context

Vervolg op het net afgeronde werk (sync-richting omgedraaid, geluid-fallback, custom-sound-backup). Nu gaat het om de thuis-detectie zelf (`presenceEntityId`/`presenceExpectedState` in `HomeAssistantSettings`, gebruikt door `AlarmOutputDecisionEngine.isUserAtHome()` en HA's `AlarmBackupHub.is_user_home()`). Op dit moment moet de gebruiker zelf een `person.`/`device_tracker.`/`binary_sensor.`-entiteit uitzoeken en kiezen — dat is voor veel gebruikers (incl. de projecteigenaar) te veel gedoe of niet toepasbaar (GPS-tracking staat vaak bewust uit i.v.m. batterijverbruik).

Twee verbeteringen, allebei met als doel: zo min mogelijk handmatige HA-configuratie, zo licht mogelijk qua permissies/batterij.

### 1. Ingebouwde ping-based thuis-detectie in `agendaalarm_backup` zelf

De gebruiker heeft dit soort detectie al eerder gebruikt via een losse HA "Ping (ICMP)"-integratie + handmatig IP-adres + een aparte `binary_sensor.` die vervolgens in de bestaande presence-picker gekozen moest worden. Dat werkt (vereist geen Android-locatiepermissie, want het is een netwerk-laag-ping van HA náár de telefoon, niet andersom), maar het los moeten opzetten in HA + het handmatig moeten weten/instellen van het IP-adres is precies het "instel-gedoe" dat weg moet.

Voorstel, in twee delen:

**A. Telefoon rapporteert automatisch haar eigen IP, geen handmatige invoer nodig.**
De app doet al regelmatig POST-aanroepen naar `/api/agendaalarm_backup/event` (zie `http_views.py`'s `AgendaAlarmEventView`, acties `"arm"` en `"start"` — respectievelijk aangeroepen vanuit `HomeAssistantRepository.armBackupWatchdog` in `AlarmScheduler.kt` regel 232, en `HomeAssistantRepository.reportAlarmAlive` in `AlarmService.kt` regel 424). Voeg aan de payload van beide een optioneel veld `phone_ip` toe: het huidige lokale IP-adres van de telefoon op het verbonden netwerk. Dit is **niet** hetzelfde als de WiFi-SSID uitlezen — het eigen toegewezen IP-adres opvragen (via `ConnectivityManager.getLinkProperties(activeNetwork)?.linkAddresses`, niet via `WifiManager.connectionInfo`) vereist geen `ACCESS_FINE_LOCATION`-permissie op Android, dus geen nieuwe permissie-aanvraag nodig. Val terug op "geen IP meesturen" (bestaand gedrag) als er geen actief netwerk/adres te vinden is — best-effort, mag arm/report-alive nooit laten falen.

Aan de HA-kant: `AlarmBackupHub.async_arm`/`async_report_alive` (`__init__.py`) onthouden dit als `self.last_known_phone_ip` (nieuw veld, `str | None`, default `None`) telkens als het meegestuurd wordt — zelfde patroon als hoe `battery_percent` nu al opgeslagen wordt.

**B. Native ping-binary_sensor, gebaseerd op dat laatst gerapporteerde IP.**
Voeg een nieuwe entiteit toe aan de integratie (nieuw bestand, bv. `binary_sensor.py` uitbreiden of een aparte ping-module) die periodiek pingt naar `hub.last_known_phone_ip` (als die gezet is) en een `binary_sensor.<naam>_telefoon_bereikbaar`-achtige entiteit bijhoudt. Kijk naar hoe HA's **eigen, ingebouwde** "Ping (ICMP)"-integratie dit doet (`homeassistant/components/ping` in HA core, gebruikt de `icmplib`-library, async, geen root nodig) en hergebruik diezelfde aanpak/dependency i.p.v. zelf een ping-implementatie te verzinnen — voeg `icmplib` toe aan `requirements` in `manifest.json` (check de exacte versie die HA core's eigen ping-component momenteel gebruikt/pinned, en gebruik dezelfde, in plaats van een aanname te doen). Doe dit in **beide** integratie-mappen (`agendaalarm_backup/` en `ha-integration/custom_components/agendaalarm_backup/` — zie eerdere afspraak dat deze twee identiek moeten blijven).

Deze nieuwe sensor moet, als er nog geen `presenceEntityId` gekozen is (noch lokaal in de app, noch al gesynct naar HA), automatisch gebruikt worden als presence-signaal — zonder dat de gebruiker 'm apart hoeft op te zoeken en te kiezen in een entity-picker. Bedenk zelf de netste manier om dit te bewerkstelligen gegeven de net doorgevoerde "app is leidend"-syncrichting (bv.: als er lokaal geen presence-entiteit gekozen is zodra deze sensor voor het eerst een geldig IP + resultaat heeft, stelt de app 'm automatisch in als `presenceEntityId` en pusht dat mee in de eerstvolgende config-push) — respecteer daarbij dat de gebruiker een expliciet gekozen andere entiteit nooit stilzwijgend mag laten overschrijven.

Belangrijke robuustheid: als `last_known_phone_ip` nog niet bekend is (bv. vlak na installatie, vóór de eerste arm/report-alive-call), moet de sensor een neutrale/unavailable staat tonen, geen valse "niet thuis". En zoals besproken: een gemiste ping (telefoon in WiFi-spaarstand met scherm uit) kan af en toe een vals-negatief geven — dat is een bekende beperking van ping-based presence, geen bug om hier op te lossen, alleen niet verergeren (bv. niet na één gemiste ping meteen "weg" concluderen; overweeg 2-3 gemiste pings op rij voordat de staat omslaat, vergelijkbaar met hoe HA's eigen Ping-integratie dit met een consecutive-failure-teller doet).

### 2. Bestaande presence-picker: automatisch `person.<gebruiker>` voorstellen

Los van de ping-optie hierboven: voor gebruikers die wél GPS/device-tracking via de officiële HA-companion-app gebruiken, bestaat de infrastructuur al (`person.`-entiteiten, die HA zelf al aggregeert uit GPS/router/Bluetooth-bronnen) — alleen moet de gebruiker 'm nu zelf opzoeken in de picker.

Let op een bestaande architectuurkeuze die je hierbij moet respecteren: `loadPresenceEntities()` in `HaSettingsViewModel.kt` (regel 643-671) filtert **alleen** uit `currentState.entities` (de handmatig gekoppelde entiteiten-lijst), met een expliciet commentaar dat er bewust **geen** live "alles uit HA"-fallback meer gebruikt wordt. Dat betekent: `person.`-entiteiten die de gebruiker nog niet aan de gekoppelde lijst heeft toegevoegd, verschijnen nu sowieso niet in deze picker — dat moet dus eerst opgelost worden voor de auto-suggestie kan werken.

Voorstel: gebruik `HomeAssistantRepository.fetchAllEntities()` (regel 296, bestaat al, wordt elders al gebruikt voor de algemene entiteiten-picker) **uitsluitend** voor een eenmalige, gerichte zoekactie naar `person.*`-entiteiten — niet om de algemene "no live fallback"-beslissing terug te draaien. Als er precies één `person.`-entiteit bestaat, en er is nog geen `presenceEntityId` gekozen: voeg 'm automatisch toe aan de gekoppelde entiteiten-lijst (`entities`) én selecteer 'm als `presenceEntityId`. Zijn er meerdere `person.`-entiteiten (multi-gebruiker HA-instantie): niet gokken welke van toepassing is, gewoon niks automatisch doen en de gebruiker zelf laten kiezen zoals nu — een verkeerde gok is hier erger dan geen suggestie.

## Wat te doen

1. Implementeer 1A: `phone_ip` meesturen in de bestaande arm/report_alive-payloads (app-kant), en opslaan op de hub (HA-kant, beide integratie-mappen).
2. Implementeer 1B: native ping-binary_sensor gebaseerd op `last_known_phone_ip`, met een consecutive-failure-teller tegen valse negatieven, en de auto-adopt-als-presence-entiteit-logica zoals hierboven beschreven.
3. Implementeer 2: eenmalige gerichte `person.*`-zoekactie + auto-adopt bij precies één treffer, in `HaSettingsViewModel.kt`.
4. Zorg dat beide nieuwe presence-bronnen (ping-sensor, person-entiteit) gewoon via het bestaande `presenceEntityId`-mechanisme lopen — geen nieuwe, aparte "hoe bepaal ik thuis"-tak ernaast bouwen in `AlarmOutputDecisionEngine`/`AlarmBackupHub.is_user_home()`. Die blijven zoals ze zijn; alleen *welke entiteit* daar automatisch in terechtkomt verandert.
5. Werk `agendaalarm_backup/const.py`'s `CONFIG_FIELDS`/`config_flow.py` bij als er nieuwe config-velden nodig zijn (bv. een aan/uit-vlag voor de ingebouwde ping-detectie), in beide integratie-mappen.

## Oplevercriteria

- Project bouwt succesvol (Android + Python-integratie importeert zonder fouten in beide mappen).
- Test 1: koppel de app met HA, laat een alarm plannen (zodat `phone_ip` wordt meegestuurd), en controleer in HA dat er een nieuwe ping-based binary_sensor verschijnt die overeenkomt met de bereikbaarheid van de telefoon op het netwerk (aan als telefoon op WiFi zit en pingbaar is, uit/onbeschikbaar anders) — zonder dat er ergens handmatig een IP-adres is ingevuld.
- Test 2: op een verse koppeling zonder al gekozen presence-entiteit, verifieer dat óf de ping-sensor óf (als er precies één bestaat) een `person.`-entiteit automatisch als `presenceEntityId` wordt gekozen — en dat een al door de gebruiker expliciet gekozen presence-entiteit nooit stilzwijgend wordt overschreven door deze auto-logica.
- Test 3: met meerdere `person.`-entiteiten in dezelfde HA-instantie, verifieer dat er niks automatisch gekozen wordt (geen gok) en de gebruiker gewoon zelf moet kiezen zoals nu.
- Kort verslag aan het eind van wat getest is en met welk resultaat.
