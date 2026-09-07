# Koppelcode niet blijven tonen/hergenereren na koppelen

## Context

De `hidden_by`-aanpak uit de vorige prompt verbergt de koppel-entiteiten alleen
van het auto-gegenereerde Overview-dashboard, niet van de apparaat-instellingen-
pagina (dat is standaard HA-gedrag, geen bug) - daar blijft de laatst gebruikte
koppelcode dus gewoon zichtbaar staan. Simpeler en robuuster: zorg dat de code
zelf verdwijnt/niet meer opnieuw gegenereerd wordt zodra er gekoppeld is, in
plaats van te vertrouwen op waar de entiteit wel/niet getoond wordt.

Bijkomend echt probleem, geverifieerd in de code: `camera.py`'s
`async_camera_image()` genereert automatisch een NIEUWE geldige koppelcode
zodra de huidige verlopen/afwezig is - dit gebeurt ook gewoon ná het koppelen,
elke keer dat HA de dashboard-thumbnail ververst. Dat betekent dat er, ook lang
na een geslaagde koppeling, gewoon steeds een vers geldige koppelcode
rondslingert die iemand met zicht op dat dashboard zou kunnen scannen/intypen om
zelf ook een token te bemachtigen (`/pair` checkt niet of er al gekoppeld is).
Dat moet dicht.

Relevante bestanden: `__init__.py` (`AlarmBackupHub.pairing_code`,
`generate_pairing_code()`, `async_set_paired()`), `sensor.py`
(`PairingCodeSensor.native_value`), `camera.py`
(`PairingQrCamera.async_camera_image`).

## Wat te doen

**1. Code wissen zodra het koppelen slaagt.**
In `AlarmBackupHub.async_set_paired()` (`__init__.py`): als `paired` `True` is,
zet `self.pairing_code = None` vóór/naast de rest van de bestaande logica
(persisteren + `async_sync_pairing_visibility` + `_notify_update`). Dit is de
kern van de fix: de net-gebruikte code (die toch al eenmalig verbruikt is door
`PairingStore.consume()`) verdwijnt meteen uit de hub-state zodra het koppelen
lukt.

**2. Sensor: nette tekst i.p.v. de rauwe `pairing_code`-waarde als die leeg is.**
In `sensor.py`'s `PairingCodeSensor.native_value` (regel ~111-113): dat is nu
`self.hub.pairing_code or "no active code"`. Wijzig de fallback zodat 'ie
onderscheid maakt: `"Paired"` (of vergelijkbaar) als `self.hub.is_paired` is,
anders `"No active code"`. Dus:
```python
@property
def native_value(self) -> str:
    if self.hub.pairing_code:
        return self.hub.pairing_code
    return "Paired" if self.hub.is_paired else "No active code"
```

**3. Camera: geen nieuwe code meer auto-genereren zodra er al gekoppeld is.**
In `camera.py`'s `async_camera_image()` (regel ~48-56): de huidige logica
genereert onvoorwaardelijk een nieuwe code zodra de huidige ontbreekt/verlopen
is. Wijzig dit zodat auto-genereren ALLEEN nog gebeurt als `self.hub.is_paired`
`False` is (dus tijdens de eerste, nog-niet-gekoppelde setup - waar dit gedrag
juist heel gewenst is, zie de docstring bovenin het bestand). Als er al gekoppeld
is én er geen actieve code is, geef dan gewoon `None` terug (geen afbeelding) in
plaats van een nieuwe code aan te maken:
```python
async def async_camera_image(
    self, width: int | None = None, height: int | None = None
) -> bytes | None:
    store = get_pairing_store(self.hass)
    has_valid_code = bool(
        self.hub.pairing_code and store.seconds_remaining(self.hub.pairing_code) > 0
    )
    if not has_valid_code:
        if self.hub.is_paired:
            # Al gekoppeld: geen nieuwe code aanmaken puur omdat het dashboard
            # een thumbnail wil - dat zou een altijd-geldige koppelcode laten
            # rondslingeren voor iedereen die het dashboard kan zien.
            return None
        self.hub.generate_pairing_code()

    try:
        base_url = get_url(self.hass, prefer_external=False, allow_internal=True)
    except NoURLAvailableError:
        return None

    payload = json.dumps({"base_url": base_url, "code": self.hub.pairing_code})
    return await self.hass.async_add_executor_job(render_qr_png, payload)
```
(Pas eventueel exact aan op de huidige codestijl, dit is de kernlogica.)

**4. Her-koppelen (2e telefoon) blijft mogelijk.**
De "Generate pairing code"-knop (`GeneratePairingCodeButton.async_press`, in
`button.py`) blijft ongewijzigd: die roept altijd `hub.generate_pairing_code()`
aan, ongeacht `is_paired`. Dus als je later een 2e telefoon wil koppelen, druk je
gewoon opnieuw op die knop - dan verschijnt er weer een geldige code/QR totdat
die gebruikt of verlopen is, ook al was je al gekoppeld. Dat is bewust zo gelaten
en hoeft niet aangepast.

## Oplevercriteria

- Direct na een geslaagde koppeling: `Pairing code`-sensor toont niet meer de
  zojuist gebruikte code, maar `"Paired"`.
- Zolang er geen nieuwe code handmatig aangevraagd is: de camera/QR-tegel
  genereert geen nieuwe geldige koppelcode meer zodra er al gekoppeld is
  (dashboard-thumbnail-ververs mag geen nieuwe code meer opleveren).
- Handmatig op "Generate pairing code" drukken werkt nog steeds, ook als er al
  gekoppeld is (voor het koppelen van een 2e toestel) - dat toont dan gewoon
  weer een geldige code/QR totdat die verbruikt/verlopen is.
- Na ontkoppelen (`is_paired` weer `False`): camera/QR-tegel genereert weer
  automatisch een verse code zodra nodig, zoals bij een verse installatie.
