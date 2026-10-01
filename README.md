# QCC

QCC ist eine Android-App für Qualitätskontrolle mit RFID und 2D-Codes im Handel. Sie läuft auf dem **Honeywell CT37** und nutzt den Bluetooth-RFID-Reader **Zebra RFD8500**.

- **QCC-Vergleich:** Per Pistolen-Trigger wird geprüft, ob der RFID-Tag (SGTIN-96) zum aufgedruckten GS1/QR-Code passt (Match/Mismatch), lokal protokolliert und als CSV exportierbar.
- **Duplikatsuche (Find duplicates):** Führt per Nähe-Anzeige, Ton und Vibration zu RFID-Tags mit identischem EPC und erfasst jeden Fund über die TID; CSV-Export beim Beenden.

Stack: Kotlin · Jetpack Compose (Material 3) · MVVM · Hilt · Room · Min SDK 28 / Target SDK 34. Zielhardware erforderlich (kein Emulator-Ziel).

## Dokumentation

Vollständige Applikationsdokumentation (Hintergrund, Anforderungen, Architektur, Datenmodell, Betrieb, Sicherheit …): [`docs/confluence/00-index.md`](docs/confluence/00-index.md).
Einstieg für Entwickler: [`SETUP.md`](SETUP.md).

## Schnellstart

```bash
./gradlew assembleDebug        # Debug-APK
./gradlew installDebug         # auf verbundenes Gerät installieren
./gradlew test                 # Unit-Tests (98)
```
