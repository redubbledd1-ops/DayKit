# Koppelen/ontkoppelen zichtbaar maken in HA zelf (symmetrisch met de app)

## Context

De Android-app toont inmiddels een "Verbonden"-status met ontkoppel-knop zodra
er al gekoppeld is, en anders de koppel-flow (QR/code). Dit prompt maakt
hetzelfde onderscheid zichtbaar aan de HA-kant: nu toont HA altijd gewoon de
"Generate pairing code"-knop, ongeacht of er al een geldige koppeling actief is.

Relevante bestaande bestanden in deze map (`custom_components/agendaalarm_backup/`):
`__init__.py` (de hub), `button.py` (koppelcode-knop), `sensor.py`,
`pairing.py` (koppelcode-uitgifte + long-lived-token-aanmaak,
zie `async_create_long_lived_token`/`CLIENT_NAME`), `http_views.py`
(`AgendaAlarmPairView` = het `/pair`-endpoint dat een code inwisselt voor een
token), `const.py`.

**Let op, bekende valkuil uit eerdere iteraties:** entiteiten die conditioneel
aangemaakt/weggelaten worden in `async_setup_entry` op basis van state (i.p.v.
altijd geregistreerd te blijven) leiden tot wees-entiteiten in de HA
entity-registry die niet vanzelf verdwijnen als de conditie omslaat (zie de
eerdere `GebruikerThuisSwitch`-bug). Gebruik dus GEEN conditionele
entity-registratie voor dit onderscheid. Gebruik in plaats daarvan de
entity-registry se eigen verberg-mechanisme (`hidden_by` op de
`RegistryEntry`, aan te passen via `entity_registry.async_update_entity(...,
hidden_by=...)`) om entiteiten op basis van koppel-status te tonen/verbergen
zonder ze te (her)registreren.

## Wat te doen

**1. Koppel-status bijhouden (persistent, overleeft een reload):**
- Voeg een `is_paired: bool`-veld toe, opgeslagen in `entry.data` (niet
  `entry.options` - dat wordt te vaak/bij ongerelateerde wijzigingen
  bijgewerkt). Lees dit bij hub-init; default `False` voor bestaande installaties
  zonder dit veld.
- Zet dit op `True` in `AgendaAlarmPairView.post` (`http_views.py`) direct nadat
  `async_create_long_lived_token` succesvol een token teruggeeft - dus bij een
  geslaagde koppeling, ongeacht via QR of handmatige code.
- Persisteer via `hass.config_entries.async_update_entry(entry, data={**entry.data,
  "is_paired": True})`.

**2. Nieuwe "Ontkoppelen"-knop (button.py):**
- Nieuwe `ButtonEntity` (zelfde stijl als `GeneratePairingCodeButton`), bv.
  `UnpairButton`, entity-id-achtig `unpair`/`disconnect`.
- Bij indrukken:
  - Zoek het long-lived refresh-token dat bij het koppelen is aangemaakt (zelfde
    `CLIENT_NAME`-matching als in `pairing.py`'s `async_create_long_lived_token`)
    onder de HA owner-user, en trek het in (verwijder het refresh-token record via
    de daarvoor bedoelde `hass.auth`-functie - dit maakt het token dat de app
    gebruikt voor alle API-calls ongeldig, dus na deze actie moet de app
    opnieuw koppelen om weer te kunnen praten met HA).
  - Zet `is_paired = False`, persisteer zoals hierboven.
  - Trigger een entity-update (`hub._notify_update()`) zodat de zichtbaarheid
    (zie punt 3) direct ververst.
- Icoon/label die duidelijk maakt dat dit de koppeling met de telefoon-app
  verbreekt (niet zomaar een reset van iets anders).

**3. Voorwaardelijke zichtbaarheid op basis van `is_paired`:**
- Niet gekoppeld (`is_paired == False`): "Generate pairing code"-knop +
  koppelcode-sensor/QR-camera-entiteit zichtbaar; "Ontkoppelen"-knop verborgen
  (`hidden_by` gezet).
- Wel gekoppeld (`is_paired == True`): omgekeerd - "Ontkoppelen"-knop zichtbaar,
  koppel-gerelateerde entiteiten (knop, QR-camera, koppelcode-sensor) verborgen.
- Implementeer dit door bij elke wijziging van `is_paired` (na stap 1 en 2) de
  entity-registry `hidden_by` van de betreffende entiteiten bij te werken -
  NIET door ze conditioneel wel/niet te registreren in `async_setup_entry`.

**4. Statussensor (optioneel maar gewenst, zelfde stijl als bestaande
True/False-sensoren zoals de uit-bed/thuis-sensoren in `sensor.py`):**
- Nieuwe sensor "Gekoppeld" die `"True"`/`"False"` teruggeeft op basis van
  `hub.is_paired` - handig als diagnostisch signaal, ook zichtbaar te maken in de
  bestaande watchdog-status-attributen als je die makkelijk kan hergebruiken.

## Oplevercriteria

- Vers geïnstalleerde/nog niet gekoppelde integratie: alleen de koppel-knop +
  QR/koppelcode zichtbaar, geen ontkoppel-knop.
- Na een geslaagde koppeling (via `/pair`): koppel-gerelateerde entiteiten
  verdwijnen (verborgen, niet verwijderd uit de registry), ontkoppel-knop
  verschijnt.
- Ontkoppelen-knop indrukken: maakt het bestaande long-lived token ongeldig (de
  app kan er niet meer mee authenticeren), koppel-status valt terug naar
  "niet gekoppeld", koppel-knop/QR verschijnen weer.
- `is_paired` overleeft een config-reload/HA-herstart (staat in `entry.data`, niet
  alleen in hub-geheugen).
- Geen wees-entiteiten in de entity-registry als gevolg van deze wijziging -
  gebruik `hidden_by`, niet conditionele registratie.
