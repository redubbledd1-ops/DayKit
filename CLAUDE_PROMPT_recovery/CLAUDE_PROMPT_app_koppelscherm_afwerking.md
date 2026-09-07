# Koppelscherm afwerking (app-kant, Kotlin)

## Context

De koppel/ontkoppel-flow uit de vorige prompt werkt al (bevestigd: connectie werkt
goed). Dit zijn twee kleine, gerichte afwerk-fixes in
`app/src/main/java/com/dd/daykit/HaSettingsActivity.kt`.

## 1. Koppelscherm moet direct sluiten na een geslaagde koppeling

**Huidige staat:** `PairModal` (regel ~533-550) sluit alleen via de eigen
sluit-knop/`onDismiss`. Na een geslaagde `viewModel.pairWithSetupCode(url, code)`
blijft het scherm gewoon open met een succesbericht erin - de gebruiker moet het
zelf wegklikken.

**Gewenst:** zodra `qrPairingState` een geslaagde koppeling meldt
(`isPairing == false`, `statusIsError == false`, `lastMessage` niet leeg - zie
`HaSettingsViewModel.pairWithSetupCode()`'s success-tak), moet `showPairModal`
automatisch naar `false` gezet worden, zodat het scherm direct sluit. Gebruik een
`LaunchedEffect(qrPairingState)` (of vergelijkbaar) rond de `PairModal`-aanroep om
dit te detecteren en `onDismiss`'s logica (`showPairModal = false;
scannedPairingPayload = null`) te triggeren. Roep ook
`viewModel.clearQrPairingMessage()` aan zodat het succesbericht niet blijft
hangen voor de volgende keer dat het scherm opent.

Bij een MISLUKTE koppeling (`statusIsError == true`) moet het scherm gewoon open
blijven zodat de gebruiker de foutmelding ziet en het opnieuw kan proberen - alleen
bij succes automatisch sluiten.

## 2. Ontkoppel-knop smaller maken, wel gecentreerd

**Huidige staat:** in `PairedCard` (regel ~1287-1335) gebruikt de
"Ontkoppelen"-knop `Modifier.fillMaxWidth()` - loopt over de volle breedte van de
kaart.

**Gewenst:** knop smaller maken (bv. `Modifier.wrapContentWidth()` met wat
horizontale padding, of een vaste/relatieve breedte), maar wel horizontaal
gecentreerd binnen de kaart. Simpelste aanpak: zet de `Button` in een `Row` of
`Box` met `Modifier.fillMaxWidth()` en `horizontalArrangement =
Arrangement.Center` (Row) / `contentAlignment = Alignment.Center` (Box), en geef
de `Button` zelf geen `fillMaxWidth()` meer.

## Oplevercriteria

- Na een geslaagde koppeling verdwijnt het koppelscherm vanzelf, zonder dat de
  gebruiker iets hoeft weg te klikken.
- Na een mislukte koppeling blijft het scherm open met de foutmelding zichtbaar.
- De "Ontkoppelen"-knop is smaller dan de volle kaartbreedte, maar staat
  horizontaal gecentreerd.
