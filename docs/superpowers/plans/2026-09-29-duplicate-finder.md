# Duplicate Finder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Neuer Use Case „Find duplicates“: Anwender gibt EPC/SGTIN vor (oder liest ihn mit der RFD8500 an), wird per Nähe-Tacho, Ton und Vibration zu jedem Duplikat geführt, bestätigt jeden Fund per TID und exportiert die Funde als CSV.

**Architecture:** Das Zebra-Gateway bekommt Trigger-Modi (`QCC`/`CAPTURE`/`LOCATE`), Ortung über `Actions.TagLocationing` (immer exakt, volle Maske) und kurze Nahbereichs-Lesevorgänge mit 5 % der Maximalleistung (EPC bzw. TID-Speicherbank, 96 Bit). Ein neues, pro Destination gescoptes `DuplicateSearchViewModel` orchestriert Eingabe, Ortung, TID-Prüfung und Export; Persistenz über zwei neue Room-Tabellen (Schema v7). Außerhalb von `QCC` leitet das Gateway Trigger-Events nicht an den QCC-Ablauf weiter.

**Tech Stack:** Kotlin, Jetpack Compose/Material3, Hilt, Room 2.6.1, Zebra RFID API3 2.0.5.238, JUnit4, Robolectric 4.14.1, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-29-duplicate-finder-design.md`

**Abweichungen von der ursprünglichen Spec (nach dem Hardware-Spike am 2026-09-30 bereits in die Spec übernommen):**
1. Trigger-Weiterleitung passiert im Gateway (nur im Modus `QCC` werden `observeTriggerEvents()` ausgelöst), `ScanViewModel` bleibt unverändert.
2. In Phase 1 ist der Trigger dauerhaft im Modus `CAPTURE`: Trigger-Druck **oder** Button „Read with reader“ liest sofort.
3. **SGTIN-Filterwert statt Filter-Maske:** Die RFD8500 akzeptiert nur Masken, die vom MSB an lückenlos 1 sind; eine Maske, die die Filter-Bits ausblendet, wird mit `Invalid EPC Mask` abgelehnt. Bei SGTIN-Eingabe erzeugt die App den EPC daher mit dem Filterwert **1** (Standard, `LocateTarget.DEFAULT_SGTIN_FILTER`), wählbar 0–7 in der UI. Gesucht wird immer exakt mit `LocateTarget.FULL_MASK`. `LocateTarget` hat nur noch `epc` und `sgtin`.
4. Nahbereichs-Lesen (Anlesen, TID-Prüfung) mit **5 % der Maximalleistung** statt Leistungsindex 0; TID wird mit genau 6 Wörtern (96 Bit) gelesen.

**Konventionen:**
- Alle Gradle-Befehle vom Repo-Root: `/Users/u092789/00_Development /Android Studio/CaRfidChecker`
- Nutzer sichtbare Texte nur über String-Ressourcen (EN `values/`, DE `values-de/`, PL `values-pl/`).
- Commits enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

## Dateistruktur

| Datei | Aktion | Verantwortung |
|---|---|---|
| `app/src/main/java/de/ca/qcc/domain/model/Models.kt` | Modify | + `LocateTarget`, `DuplicateSearch`, `RegisterFindResult` |
| `app/src/main/java/de/ca/qcc/core/gs1/Gs1ParserService.kt` | Modify | + `parseLocateTarget`, `encodeSgtin96` |
| `app/src/main/java/de/ca/qcc/device/zebra/RfidReaderGateway.kt` | Modify | + `TriggerMode`, Duplikat-Funktionen mit Defaults |
| `app/src/main/java/de/ca/qcc/device/zebra/ZebraRfd8500Gateway.kt` | Modify | Trigger-Modi, Ortung, Nahbereichs-Lesen |
| `app/src/main/java/de/ca/qcc/data/local/Entities.kt` | Modify | + `DuplicateSearchEntity`, `DuplicateFindEntity` |
| `app/src/main/java/de/ca/qcc/data/local/Daos.kt` | Modify | + `DuplicateDao` |
| `app/src/main/java/de/ca/qcc/data/local/AppDatabase.kt` | Modify | Version 7, neue Entities/DAO |
| `app/src/main/java/de/ca/qcc/data/local/Migrations.kt` | Modify | + `MIGRATION_6_7` |
| `app/src/main/java/de/ca/qcc/data/local/StorePreferences.kt` | Create | zuletzt verwendete Filiale |
| `app/src/main/java/de/ca/qcc/data/export/CsvDocumentWriter.kt` | Create | CSV ins Documents-Verzeichnis schreiben, `csvEscape` |
| `app/src/main/java/de/ca/qcc/data/repository/Repositories.kt` | Modify | `MismatchRepository` nutzt `CsvDocumentWriter` |
| `app/src/main/java/de/ca/qcc/data/repository/DuplicateSearchRepository.kt` | Create | Suchen/Funde speichern, offene Suche, CSV |
| `app/src/main/java/de/ca/qcc/di/AppModule.kt` | Modify | Migration, `CsvDocumentWriter`, `ProximityFeedback` |
| `app/src/main/java/de/ca/qcc/feature/duplicates/ProximityFeedback.kt` | Create | Ton + Vibration je Nähe |
| `app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModel.kt` | Create | Zustand und Ablauf |
| `app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchScreen.kt` | Create | UI beider Phasen |
| `app/src/main/java/de/ca/qcc/feature/duplicates/ProximityGauge.kt` | Create | Tacho-Composable |
| `app/src/main/java/de/ca/qcc/navigation/Nav.kt` | Modify | Route `duplicates` |
| `app/src/main/java/de/ca/qcc/app/MainActivity.kt` | Modify | Drawer-Eintrag + Destination |
| `app/src/main/res/values*/strings.xml` | Modify | neue Texte EN/DE/PL |
| `app/build.gradle.kts` | Modify | `room-testing`, Schemas als Test-Assets |
| `app/src/test/java/de/ca/qcc/core/gs1/Gs1LocateTargetTest.kt` | Create | Parser-Tests |
| `app/src/test/java/de/ca/qcc/data/local/MigrationTest.kt` | Create | Migration 6 → 7 |
| `app/src/test/java/de/ca/qcc/testutil/DuplicateFakes.kt` | Create | Fakes + In-Memory-DB |
| `app/src/test/java/de/ca/qcc/data/repository/DuplicateSearchRepositoryTest.kt` | Create | Repository-Tests |
| `app/src/test/java/de/ca/qcc/feature/duplicates/BeepIntervalTest.kt` | Create | Taktberechnung |
| `app/src/test/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModelTest.kt` | Create | ViewModel-Tests |

---

### Task 1: LocateTarget und SGTIN-96-Encoding im Parser (erledigt, siehe Commits bd0ea0d, 414f66d, 75be11a)

> **Historisch:** Der Code in diesem Task zeigt den ursprünglichen Stand mit `mask`/`filterMasked`/`FILTER_IGNORING_MASK`. Durch Commit 75be11a (nach dem Hardware-Spike) gilt stattdessen: `data class LocateTarget(val epc: String, val sgtin: String)` mit `FULL_MASK` und `DEFAULT_SGTIN_FILTER = 1`; `Gs1ParserService.parseLocateTarget(input: String, sgtinFilter: Int = LocateTarget.DEFAULT_SGTIN_FILTER)`. Maßgeblich ist der Code im Repository.

**Files:**
- Modify: `app/src/main/java/de/ca/qcc/domain/model/Models.kt`
- Modify: `app/src/main/java/de/ca/qcc/core/gs1/Gs1ParserService.kt`
- Test: `app/src/test/java/de/ca/qcc/core/gs1/Gs1LocateTargetTest.kt`

- [ ] **Step 1: Failing Test schreiben**

`app/src/test/java/de/ca/qcc/core/gs1/Gs1LocateTargetTest.kt`:

```kotlin
package de.ca.qcc.core.gs1

import de.ca.qcc.domain.model.LocateTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Gs1LocateTargetTest {

    private val parser = Gs1ParserService()

    @Test
    fun epcInput_isNormalized_andSearchedExactly() {
        val target = parser.parseLocateTarget(" 3014f824285a980300000006 ")

        assertEquals("3014F824285A980300000006", target.epc)
        assertEquals("4065546.092768.12884901894", target.sgtin)
        assertEquals(LocateTarget.FULL_MASK, target.mask)
        assertFalse(target.filterMasked)
    }

    @Test
    fun sgtinInput_encodesEpcWithFilterZero_andIgnoresFilterBits() {
        val target = parser.parseLocateTarget("4065546.092768.12884901894")

        assertEquals("3014F824285A980300000006", target.epc)
        assertEquals("4065546.092768.12884901894", target.sgtin)
        assertEquals(LocateTarget.FILTER_IGNORING_MASK, target.mask)
        assertTrue(target.filterMasked)
    }

    @Test
    fun masks_haveExpectedShape() {
        assertEquals("F".repeat(24), LocateTarget.FULL_MASK)
        assertEquals("FF1F" + "F".repeat(20), LocateTarget.FILTER_IGNORING_MASK)
    }

    @Test
    fun encodeSgtin96_roundTripsForAllPartitions() {
        val cases = listOf(
            "123456789012" to "0",
            "12345678901" to "12",
            "1234567890" to "123",
            "123456789" to "1234",
            "12345678" to "12345",
            "4065546" to "092768",
            "123456" to "1234567"
        )
        cases.forEach { (gcp, itemRef) ->
            val epc = parser.encodeSgtin96(gcp, itemRef, "987654", filter = 3)
            assertEquals("$gcp.$itemRef.987654", parser.parseEpcToSgtin(epc).sgtin)
            val filterBits = BigInteger(epc, 16).shiftRight(85).and(BigInteger.valueOf(7)).toInt()
            assertEquals(3, filterBits)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun urnNotation_isRejected() {
        parser.parseLocateTarget("urn:epc:id:sgtin:4065546.092768.12884901894")
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbage_isRejected() {
        parser.parseLocateTarget("ABC")
    }

    @Test(expected = IllegalArgumentException::class)
    fun companyPrefixTooShort_isRejected() {
        parser.parseLocateTarget("12345.1234567.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun itemReferenceWithWrongLength_isRejected() {
        parser.parseLocateTarget("4065546.92768.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun serialWithLeadingZero_isRejected() {
        parser.parseLocateTarget("4065546.092768.012")
    }

    @Test(expected = IllegalArgumentException::class)
    fun serialTooLarge_isRejected() {
        parser.parseLocateTarget("4065546.092768.274877906944")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonSgtinEpc_isRejected() {
        parser.parseLocateTarget("E28068940000500000000001")
    }
}
```

(`274877906944` = 2^38, also eins zu groß für die 38 Serial-Bits.)

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.core.gs1.Gs1LocateTargetTest"`
Expected: FAIL – Kompilierfehler `Unresolved reference: LocateTarget` / `parseLocateTarget`.

- [ ] **Step 3: `LocateTarget` anlegen**

Am Ende von `app/src/main/java/de/ca/qcc/domain/model/Models.kt` anfügen:

```kotlin
@Serializable
data class LocateTarget(
    val epc: String,
    val sgtin: String,
    val mask: String,
    val filterMasked: Boolean
) {
    companion object {
        val FULL_MASK: String = "F".repeat(24)

        // Ignores the 3 filter bits (bits 8-10) of an SGTIN-96 EPC
        val FILTER_IGNORING_MASK: String = "FF1F" + "F".repeat(20)
    }
}
```

- [ ] **Step 4: Parser erweitern**

In `app/src/main/java/de/ca/qcc/core/gs1/Gs1ParserService.kt`:

Import ergänzen:

```kotlin
import de.ca.qcc.domain.model.LocateTarget
```

Unter `private val qrCompanyPrefixDigits = 7` einfügen:

```kotlin
    private val sgtinSerialBits = 38
```

Vor `private fun hexToBinary(` einfügen:

```kotlin
    fun parseLocateTarget(input: String): LocateTarget {
        val normalized = input.trim()
        return when {
            normalized.matches(Regex("^[0-9A-Fa-f]{24}$")) -> {
                val parsed = parseEpcToSgtin(normalized)
                LocateTarget(
                    epc = parsed.epc,
                    sgtin = parsed.sgtin,
                    mask = LocateTarget.FULL_MASK,
                    filterMasked = false
                )
            }
            normalized.matches(Regex("""^\d+\.\d+\.\d+$""")) -> {
                val (gcp, itemRef, serial) = normalized.split('.')
                LocateTarget(
                    epc = encodeSgtin96(gcp, itemRef, serial, filter = 0),
                    sgtin = normalized,
                    mask = LocateTarget.FILTER_IGNORING_MASK,
                    filterMasked = true
                )
            }
            else -> throw IllegalArgumentException("Eingabe ist weder EPC (24 Hex) noch SGTIN")
        }
    }

    fun encodeSgtin96(gcp: String, itemRef: String, serial: String, filter: Int): String {
        val partition = gcpDigits.indexOf(gcp.length)
        require(partition >= 0) { "Company Prefix muss 6-12 Stellen haben" }
        require(itemRef.length == itemRefDigits[partition]) {
            "Item Reference muss ${itemRefDigits[partition]} Stellen haben"
        }
        require(filter in 0..7) { "Filterwert muss 0-7 sein" }
        require(serial == "0" || !serial.startsWith("0")) {
            "SGTIN-96 Seriennummer darf keine führende Null haben"
        }
        val serialValue = serial.toLong()
        require(serialValue < (1L shl sgtinSerialBits)) { "Seriennummer zu groß für SGTIN-96" }

        val bits = buildString {
            append(sgtin96Header)
            append(filter.toString(2).padStart(3, '0'))
            append(partition.toString(2).padStart(3, '0'))
            append(gcp.toLong().toString(2).padStart(gcpBits[partition], '0'))
            append(itemRef.toLong().toString(2).padStart(itemRefBits[partition], '0'))
            append(serialValue.toString(2).padStart(sgtinSerialBits, '0'))
        }
        return BigInteger(bits, 2).toString(16).padStart(24, '0').uppercase()
    }
```

- [ ] **Step 5: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.core.gs1.*"`
Expected: PASS (alle Parser-Tests inkl. der bestehenden).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/de/ca/qcc/domain/model/Models.kt app/src/main/java/de/ca/qcc/core/gs1/Gs1ParserService.kt app/src/test/java/de/ca/qcc/core/gs1/Gs1LocateTargetTest.kt
git commit -m "feat(gs1): parse locate targets and encode SGTIN-96

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Gateway – Trigger-Modi, Ortung, Nahbereichs-Lesen (erledigt, siehe Commits 55dbb0d, 4175b79, 857f7fd)

> **Historisch:** Die Signaturen des Interfaces gelten unverändert (`startLocate(epc, mask)`, `readTidsNearby(epc, mask)`, `readEpcsNearby()` …); Aufrufer übergeben ab jetzt immer `LocateTarget.FULL_MASK`. Abweichend vom Code unten liest das Gateway nach Commit 857f7fd mit 5 % der Maximalleistung (`NEARBY_POWER_PERCENT = 5`) und die TID mit `count = 6` (96 Bit). Maßgeblich ist der Code im Repository.

Kein Unit-Test möglich (Zebra-SDK braucht Hardware). Verifikation: Kompilieren + Hardware-Spike in Task 3.

**Files:**
- Modify: `app/src/main/java/de/ca/qcc/device/zebra/RfidReaderGateway.kt`
- Modify: `app/src/main/java/de/ca/qcc/device/zebra/ZebraRfd8500Gateway.kt`

- [ ] **Step 1: Interface erweitern**

`app/src/main/java/de/ca/qcc/device/zebra/RfidReaderGateway.kt` komplett ersetzen:

```kotlin
package de.ca.qcc.device.zebra

import de.ca.qcc.domain.model.ReaderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

enum class TriggerMode { QCC, CAPTURE, LOCATE }

interface RfidReaderGateway {
    fun observeStatus(): Flow<ReaderStatus>
    fun observeTagScans(): Flow<String>
    fun observeTriggerEvents(): Flow<Unit> = emptyFlow() // Default empty implementation
    suspend fun discoverReaders(): List<String>
    suspend fun connect(readerId: String): Result<Unit>
    suspend fun disconnect()
    suspend fun triggerInventory()
    suspend fun setPower(level: Int): Result<Unit>

    // ── Duplicate search (defaults keep test fakes compiling) ──

    // QCC = trigger runs the QCC single-tag scan; other modes only report presses
    fun setTriggerMode(mode: TriggerMode) = Unit

    // true = pressed, false = released; emitted only while mode != QCC
    fun observeHandheldTrigger(): Flow<Boolean> = emptyFlow()

    // Relative distance 0..100 while locating
    fun observeProximity(): Flow<Int> = emptyFlow()

    suspend fun startLocate(epc: String, mask: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Locate not supported"))

    suspend fun stopLocate() = Unit

    // Distinct EPCs read at minimum power
    suspend fun readEpcsNearby(): Result<List<String>> =
        Result.failure(UnsupportedOperationException("Nearby read not supported"))

    // Distinct TIDs of tags matching epc/mask, read at minimum power
    suspend fun readTidsNearby(epc: String, mask: String): Result<List<String>> =
        Result.failure(UnsupportedOperationException("TID read not supported"))
}
```

- [ ] **Step 2: Imports im Zebra-Gateway ergänzen**

In `ZebraRfd8500Gateway.kt` zu den Imports hinzufügen:

```kotlin
import com.zebra.rfid.api3.ACCESS_OPERATION_CODE
import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS
import com.zebra.rfid.api3.AccessFilter
import com.zebra.rfid.api3.FILTER_MATCH_PATTERN
import com.zebra.rfid.api3.MEMORY_BANK
import com.zebra.rfid.api3.TagData
import com.zebra.rfid.api3.TagPatternBase
import kotlinx.coroutines.delay
```

- [ ] **Step 3: Felder ergänzen**

Direkt nach `private val inventoryActive = AtomicBoolean(false)` einfügen:

```kotlin
    // ── Duplicate search state ──
    @Volatile private var triggerMode = TriggerMode.QCC

    private val handheldTriggerFlow = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val proximityFlow = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val locateActive = AtomicBoolean(false)

    // Set while readEpcsNearby()/readTidsNearby() collect; receives every read TagData
    @Volatile private var nearbySink: ((TagData) -> Unit)? = null
```

Und nach `override fun observeTriggerEvents(): Flow<Unit> = triggerFlow.asSharedFlow()`:

```kotlin
    override fun setTriggerMode(mode: TriggerMode) {
        triggerMode = mode
    }

    override fun observeHandheldTrigger(): Flow<Boolean> = handheldTriggerFlow.asSharedFlow()

    override fun observeProximity(): Flow<Int> = proximityFlow.asSharedFlow()
```

- [ ] **Step 4: `eventReadNotify` erweitern**

Die Methode `eventReadNotify` ersetzen durch:

```kotlin
        // Called on the SDK thread when a tag is decoded
        override fun eventReadNotify(e: RfidReadEvents) {
            nearbySink?.let { sink ->
                reader?.Actions?.getReadTags(100)?.forEach(sink)
                return
            }

            if (locateActive.get()) {
                reader?.Actions?.getReadTags(100)
                    ?.filter { it.isContainsLocationInfo }
                    ?.forEach { proximityFlow.tryEmit(it.LocationInfo.relativeDistance.toInt()) }
                return
            }

            // QCC: only handle the first tag; compareAndSet ensures exactly-once delivery
            if (!inventoryActive.compareAndSet(true, false)) return

            // Stop immediately — we only want exactly 1 tag
            runCatching { reader?.Actions?.Inventory?.stop() }

            val tags = reader?.Actions?.getReadTags(100) ?: return
            val epc = tags.firstOrNull { !it.tagID.isNullOrBlank() }?.tagID ?: return
            tagFlow.tryEmit(epc)
        }
```

- [ ] **Step 5: Trigger-Weiterleitung und Disconnect anpassen**

Im `HANDHELD_TRIGGER_EVENT`-Zweig von `eventStatusNotify` die beiden Fälle ersetzen durch:

```kotlin
                        HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED -> {
                            if (triggerMode == TriggerMode.QCC) {
                                startInventoryInternal()
                                triggerFlow.tryEmit(Unit) // Emit trigger press event
                            } else {
                                handheldTriggerFlow.tryEmit(true)
                            }
                        }
                        HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED -> {
                            if (triggerMode == TriggerMode.QCC) {
                                // If trigger released before a tag was read, stop inventory
                                if (inventoryActive.compareAndSet(true, false)) {
                                    runCatching { reader?.Actions?.Inventory?.stop() }
                                }
                            } else {
                                handheldTriggerFlow.tryEmit(false)
                            }
                        }
```

Im `DISCONNECTION_EVENT`-Zweig direkt nach `inventoryActive.set(false)` einfügen:

```kotlin
                    locateActive.set(false)
                    nearbySink = null
                    proximityFlow.tryEmit(0)
```

- [ ] **Step 6: Ortung und Nahbereichs-Lesen implementieren**

Nach der Methode `triggerInventory()` einfügen:

```kotlin
    // ── Duplicate search ─────────────────────────────────────────────────────

    override suspend fun startLocate(epc: String, mask: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = requireConnectedReader()
                if (!locateActive.compareAndSet(false, true)) return@runCatching
                try {
                    r.Actions.TagLocationing.Perform(epc, mask, null)
                } catch (t: Throwable) {
                    locateActive.set(false)
                    throw t
                }
            }.readableFailure()
        }

    override suspend fun stopLocate() {
        withContext(Dispatchers.IO) {
            if (locateActive.compareAndSet(true, false)) {
                runCatching { reader?.Actions?.TagLocationing?.Stop() }
                proximityFlow.tryEmit(0)
            }
        }
    }

    override suspend fun readEpcsNearby(): Result<List<String>> = readNearby(
        start = { it.Actions.Inventory.perform() },
        stop = { it.Actions.Inventory.stop() },
        extract = { tag -> tag.tagID?.takeIf { it.isNotBlank() } }
    )

    override suspend fun readTidsNearby(epc: String, mask: String): Result<List<String>> = readNearby(
        start = { r ->
            val params = r.Actions.TagAccess.ReadAccessParams().apply {
                memoryBank = MEMORY_BANK.MEMORY_BANK_TID
                offset = 0
                count = 0 // 0 = whole bank; TID length differs by chip vendor
            }
            r.Actions.TagAccess.readEvent(params, epcFilter(epc, mask), null)
        },
        stop = { it.Actions.TagAccess.stopAccess() },
        extract = { tag ->
            tag.memoryBankData?.takeIf {
                tag.opCode == ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ &&
                    tag.opStatus == ACCESS_OPERATION_STATUS.ACCESS_SUCCESS &&
                    it.isNotBlank()
            }
        }
    )

    // Reads at minimum power for a short window so only tags directly at the reader answer
    private suspend fun readNearby(
        start: (RFIDReader) -> Unit,
        stop: (RFIDReader) -> Unit,
        extract: (TagData) -> String?
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val r = requireConnectedReader()
            check(!locateActive.get() && !inventoryActive.get()) { "Reader busy" }
            val found = LinkedHashSet<String>()
            nearbySink = { tag -> extract(tag)?.let { synchronized(found) { found.add(it.uppercase()) } } }
            try {
                applyAntennaConfig(r, 0)
                start(r)
                delay(NEARBY_READ_WINDOW_MS)
                runCatching { stop(r) }
            } finally {
                nearbySink = null
                runCatching { applyAntennaConfig(r, currentPowerIndex) }
            }
            synchronized(found) { found.toList() }
        }.readableFailure()
    }

    private fun epcFilter(epc: String, mask: String) = AccessFilter().apply {
        TagPatternA = TagPatternBase().apply {
            memoryBank = MEMORY_BANK.MEMORY_BANK_EPC
            bitOffset = 32 // skip CRC + PC words
            setTagPattern(epc)
            tagPatternBitCount = 96
            setTagMask(mask)
            tagMaskBitCount = 96
        }
        accessFilterMatchPattern = FILTER_MATCH_PATTERN.A
    }

    private fun requireConnectedReader(): RFIDReader =
        reader?.takeIf { it.isConnected } ?: throw IllegalStateException("Reader nicht verbunden")

    private fun <T> Result<T>.readableFailure(): Result<T> {
        val e = exceptionOrNull() as? OperationFailureException ?: return this
        return Result.failure(IllegalStateException("result=${e.results}, vendor=${e.vendorMessage}"))
    }

    private companion object {
        const val NEARBY_READ_WINDOW_MS = 700L
    }
```

- [ ] **Step 7: Kompilieren und bestehende Tests laufen lassen**

Run: `./gradlew assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle bestehenden Tests grün (die Fakes in `ScanViewModelTest` nutzen die Default-Implementierungen).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/de/ca/qcc/device/zebra/RfidReaderGateway.kt app/src/main/java/de/ca/qcc/device/zebra/ZebraRfd8500Gateway.kt
git commit -m "feat(zebra): add trigger modes, tag locate and nearby reads

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Hardware-Spike (Risiko R1) – wird NICHT committet (erledigt am 2026-09-30; Folgeänderungen in Commits 75be11a, 857f7fd)

> **Historisch:** Der Spike-Code unten verwendet noch `LocateTarget.FILTER_IGNORING_MASK`, das inzwischen entfernt ist. Ergebnisse siehe Abschnitt „Ergebnis“ am Ende dieses Tasks.

Ziel: Auf echter RFD8500 + CT37 prüfen, dass Ortung (exakt + maskiert), EPC-Anlesen und TID-Lesen funktionieren, **bevor** DB und UI darauf aufbauen. Spike-Code wird nach der Prüfung wieder gelöscht.

**Files (temporär):**
- Create: `app/src/main/java/de/ca/qcc/feature/spike/SpikeScreen.kt`
- Modify: `app/src/main/java/de/ca/qcc/app/MainActivity.kt` (temporär)

- [ ] **Step 1: Spike-Screen anlegen**

`app/src/main/java/de/ca/qcc/feature/spike/SpikeScreen.kt`:

```kotlin
package de.ca.qcc.feature.spike

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.domain.model.LocateTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpikeViewModel @Inject constructor(private val rfid: RfidReaderGateway) : ViewModel() {
    val log = MutableStateFlow(listOf<String>())
    private var epc: String? = null

    private fun add(line: String) {
        Log.d("QCC-SPIKE", line)
        log.value = (listOf(line) + log.value).take(80)
    }

    init {
        rfid.setTriggerMode(TriggerMode.CAPTURE)
        viewModelScope.launch { rfid.observeHandheldTrigger().collect { add("trigger pressed=$it") } }
        viewModelScope.launch { rfid.observeProximity().collect { add("proximity=$it") } }
    }

    fun readEpcs() = viewModelScope.launch {
        rfid.readEpcsNearby()
            .onSuccess { add("EPCs=$it"); epc = it.firstOrNull() ?: epc }
            .onFailure { add("EPC error: ${it.message}") }
    }

    fun locate(masked: Boolean) = viewModelScope.launch {
        val target = epc ?: return@launch add("no EPC yet")
        val mask = if (masked) LocateTarget.FILTER_IGNORING_MASK else LocateTarget.FULL_MASK
        rfid.startLocate(target, mask).onFailure { return@launch add("locate error: ${it.message}") }
        add("locate started masked=$masked")
        delay(10_000)
        rfid.stopLocate()
        add("locate stopped")
    }

    fun readTids() = viewModelScope.launch {
        val target = epc ?: return@launch add("no EPC yet")
        rfid.readTidsNearby(target, LocateTarget.FULL_MASK)
            .onSuccess { add("TIDs=$it") }
            .onFailure { add("TID error: ${it.message}") }
    }

    override fun onCleared() {
        rfid.setTriggerMode(TriggerMode.QCC)
    }
}

@Composable
fun SpikeScreen(vm: SpikeViewModel) {
    val log by vm.log.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { vm.readEpcs() }) { Text("1 Read EPCs nearby") }
        Button(onClick = { vm.locate(masked = false) }) { Text("2 Locate 10s exact") }
        Button(onClick = { vm.locate(masked = true) }) { Text("3 Locate 10s filter-masked") }
        Button(onClick = { vm.readTids() }) { Text("4 Read TIDs nearby") }
        LazyColumn { items(log) { Text(it) } }
    }
}
```

- [ ] **Step 2: Temporär in `MainActivity` einhängen**

In `AppNav()` im `NavHost` nach `composable(Screen.About.route) { … }` einfügen:

```kotlin
            composable("spike") {
                de.ca.qcc.feature.spike.SpikeScreen(hiltViewModel())
            }
```

Und im Drawer nach dem „About“-`DrawerItem`:

```kotlin
                    DrawerItem(
                        label = "Spike",
                        icon = Icons.Default.Info,
                        selected = currentRoute == "spike",
                        onClick = {
                            navController.navigate("spike")
                            scope.launch { drawerState.close() }
                        }
                    )
```

- [ ] **Step 3: Auf CT37 deployen**

Run:
```bash
./gradlew assembleDebug && ~/Library/Android/sdk/platform-tools/adb -s 24255X0032 install -r app/build/outputs/apk/debug/app-debug.apk && ~/Library/Android/sdk/platform-tools/adb -s 24255X0032 logcat -c
```
Expected: `Success`.

- [ ] **Step 4: Spike mit dem Anwender durchführen**

Anwender benötigt mindestens 2 Artikel mit doppeltem EPC. Checkliste (Log live: `adb -s 24255X0032 logcat -s QCC-SPIKE`):

| # | Aktion | Erwartung |
|---|---|---|
| a | 1 Duplikat direkt an Pistole, Button 1 | `EPCs=[<EPC>]` genau 1 Eintrag |
| b | Artikel ca. 30 cm entfernt, Button 1 | `EPCs=[]` (minimale Leistung reicht nur wenige cm) |
| c | Trigger drücken/loslassen | `trigger pressed=true` / `false` |
| d | Button 2, Pistole auf Artikel zu und weg bewegen | `proximity=` steigt/fällt spürbar |
| e | Button 3, gleiche Bewegung | wie d (Maske wird akzeptiert) |
| f | 1 Duplikat an Pistole, Button 4 | `TIDs=[<TID>]` genau 1 Eintrag |
| g | 2 Duplikate zusammen an Pistole, Button 4 | 2 unterschiedliche TIDs |
| h | Spike verlassen → Dashboard, Zebra-Trigger | QCC-Ablauf wie vorher |

- [ ] **Step 5: Ergebnis bewerten (Entscheidungspunkt)**

- Alles erfüllt → weiter mit Step 6.
- **e scheitert** (maskierte Ortung): Anwender informieren; SGTIN-Eingabe dann mit Filter 0 exakt suchen, Hinweis in UI – Spec anpassen, bevor weitergemacht wird.
- **f/g liefert leer oder Fehler**: in `readTidsNearby` `count = 6` statt `0` testen (96-Bit-TID); falls ok, diese Änderung committen.
- **b liefert Tags**: Lesefenster/Leistung passt nicht – Anwender fragen; ggf. `NEARBY_READ_WINDOW_MS` senken.
- **d liefert keine Werte**: STOP, mit Anwender neu planen (Fallback: RSSI-basierte Nähe, Spec Ansatz 2).

Ergebnisse in `docs/superpowers/specs/2026-09-29-duplicate-finder-design.md` unter „10) Risiken“ bei R1 als „Spike-Ergebnis 2026-…: …“ vermerken.

- [ ] **Step 6: Spike-Code entfernen**

Run:
```bash
rm -r app/src/main/java/de/ca/qcc/feature/spike
git checkout app/src/main/java/de/ca/qcc/app/MainActivity.kt
git status --short
```
Expected: Keine Spike-Dateien mehr; nur ggf. Gateway-Fix aus Step 5 und Spec-Notiz geändert.

- [ ] **Step 7: Commit (nur Spec-Notiz und ggf. Gateway-Fix)**

```bash
git add docs/superpowers/specs/2026-09-29-duplicate-finder-design.md app/src/main/java/de/ca/qcc/device/zebra/ZebraRfd8500Gateway.kt
git commit -m "docs: record duplicate finder hardware spike results

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

#### Ergebnis (Hardware-Spike 2026-09-30, RFD8500 + CT37)

| # | Prüfung | Ergebnis |
|---|---|---|
| a | EPC anlesen mit Leistungsindex 0 | unzuverlässig (ca. 2 von 6 Versuchen) |
| a' | EPC anlesen mit 5 % der Maximalleistung (Index 15 von 300) | zuverlässig (3/3 direkt an der Pistole) |
| b | 5 % Leistung, Artikel ca. 30 cm entfernt | nichts gelesen (gewünscht) |
| c | Trigger-Events in Nicht-QCC-Modus | pressed=true/false kommen zuverlässig an |
| d | TagLocationing exakt (Maske 24×F) | funktioniert, 4–5 Werte/s, 38–99 je nach Abstand |
| e | TagLocationing mit Maske `FF1F…` (Filterbits ausgeblendet) | Fehler `Invalid EPC Mask` |
| e' | Masken `F×12 + 0×12` und `F×23 + 0` | akzeptiert und funktionieren (max 91 / 83) |
| e'' | Maske `FF00 + F×20` | `Invalid EPC Mask` |
| f | TID lesen (TID-Bank, offset 0, count 0) | funktioniert, liefert aber 96-Bit-TID + 12 angehängte Nullen: `E280119120006EFD14DA033D000000000000` |
| g | Zwei Duplikate gleichzeitig | nicht getestet (nur ein Duplikat verfügbar) → in Task 11 nachholen |
| h | QCC-Ablauf danach | unverändert, funktioniert |

Echter Duplikat-EPC: `3034F8242800C10300000011` → Filterwert **1**, Partition 5.

Entscheidungen:
1. Die RFD8500 akzeptiert nur Masken, die vom MSB an lückenlos 1 sind. Die Filterbit-Maske ist nicht umsetzbar.
2. Bei SGTIN-Eingabe verwendet die App den Filterwert **1** als Standard, mit Auswahl **0–7** in der UI. Gesucht wird immer exakt (volle Maske). `FILTER_IGNORING_MASK` und `filterMasked` entfallen (Commit 75be11a).
3. Nahbereichs-Lesen (Anlesen + TID-Prüfung) mit 5 % der Maximalleistung statt Index 0 (Commit 857f7fd).
4. TID mit genau 6 Wörtern (96 Bit) statt `count = 0` (Commit 857f7fd).

Die Spec ist entsprechend aktualisiert (Abschnitte 3, 4, 5, 8, 10).

---

### Task 4: Room – Tabellen, DAO, Migration 6 → 7

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/de/ca/qcc/data/local/Entities.kt`
- Modify: `app/src/main/java/de/ca/qcc/data/local/Daos.kt`
- Modify: `app/src/main/java/de/ca/qcc/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/de/ca/qcc/data/local/Migrations.kt`
- Modify: `app/src/main/java/de/ca/qcc/di/AppModule.kt`
- Test: `app/src/test/java/de/ca/qcc/data/local/MigrationTest.kt`

- [ ] **Step 1: Test-Abhängigkeit und Schema-Assets**

In `app/build.gradle.kts` nach `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")`:

```kotlin
    testImplementation("androidx.room:room-testing:2.6.1")
```

Und im `android { … }`-Block den bestehenden `testOptions`-Block ersetzen durch:

```kotlin
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
    }
```

- [ ] **Step 2: Failing Migrationstest schreiben**

`app/src/test/java/de/ca/qcc/data/local/MigrationTest.kt`:

```kotlin
package de.ca.qcc.data.local

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate6To7_addsDuplicateTables_andKeepsScans() {
        helper.createDatabase(DB_NAME, 6).apply {
            execSQL(
                "INSERT INTO scans (timestamp, gtinQr, gtinTag, isMatch, sgtinQr, sgtinTag, qrRaw) " +
                    "VALUES (1, 'a', 'b', 1, 'x', 'y', 'z')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7)

        db.query("SELECT COUNT(*) FROM scans").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.execSQL("INSERT INTO duplicate_searches (startedAt, sgtin, epc) VALUES (1, 's', 'e')")
        db.execSQL("INSERT INTO duplicate_finds (searchId, foundAt, tid, findNo) VALUES (1, 2, 'T1', 1)")
        db.query("SELECT COUNT(*) FROM duplicate_finds").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.data.local.MigrationTest"`
Expected: FAIL – `Unresolved reference: MIGRATION_6_7`.

- [ ] **Step 4: Entities anlegen**

In `app/src/main/java/de/ca/qcc/data/local/Entities.kt` Imports ergänzen:

```kotlin
import androidx.room.ForeignKey
import androidx.room.Index
```

Am Dateiende anfügen:

```kotlin
@Entity(tableName = "duplicate_searches")
data class DuplicateSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val sgtin: String,
    val epc: String,
    val store: String? = null,
    val exportFileName: String? = null
)

@Entity(
    tableName = "duplicate_finds",
    foreignKeys = [
        ForeignKey(
            entity = DuplicateSearchEntity::class,
            parentColumns = ["id"],
            childColumns = ["searchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tid"], unique = true), Index(value = ["searchId"])]
)
data class DuplicateFindEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val searchId: Long,
    val foundAt: Long,
    val tid: String,
    val findNo: Int
)
```

- [ ] **Step 5: DAO anlegen**

Am Ende von `app/src/main/java/de/ca/qcc/data/local/Daos.kt` anfügen:

```kotlin
@Dao
interface DuplicateDao {
    @Insert
    suspend fun insertSearch(search: DuplicateSearchEntity): Long

    @Query("SELECT * FROM duplicate_searches WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpenSearch(): DuplicateSearchEntity?

    @Query("SELECT * FROM duplicate_searches WHERE id = :id")
    suspend fun getSearch(id: Long): DuplicateSearchEntity?

    @Query("UPDATE duplicate_searches SET endedAt = :endedAt, exportFileName = :fileName WHERE id = :id")
    suspend fun finishSearch(id: Long, endedAt: Long, fileName: String?)

    @Insert
    suspend fun insertFind(find: DuplicateFindEntity): Long

    @Query("SELECT * FROM duplicate_finds WHERE tid = :tid LIMIT 1")
    suspend fun findByTid(tid: String): DuplicateFindEntity?

    @Query("SELECT * FROM duplicate_finds WHERE searchId = :searchId ORDER BY findNo")
    suspend fun getFinds(searchId: Long): List<DuplicateFindEntity>

    @Query("SELECT COUNT(*) FROM duplicate_finds WHERE searchId = :searchId")
    suspend fun countFinds(searchId: Long): Int
}
```

- [ ] **Step 6: AppDatabase auf Version 7**

`app/src/main/java/de/ca/qcc/data/local/AppDatabase.kt` komplett ersetzen:

```kotlin
package de.ca.qcc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MismatchEntity::class,
        ScanEntity::class,
        DuplicateSearchEntity::class,
        DuplicateFindEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mismatchDao(): MismatchDao
    abstract fun scanDao(): ScanDao
    abstract fun duplicateDao(): DuplicateDao
}
```

- [ ] **Step 7: Migration schreiben**

Am Ende von `app/src/main/java/de/ca/qcc/data/local/Migrations.kt` anfügen:

```kotlin
// Adds the duplicate finder tables; existing tables are untouched.
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `duplicate_searches` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER,
                `sgtin` TEXT NOT NULL,
                `epc` TEXT NOT NULL,
                `store` TEXT,
                `exportFileName` TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `duplicate_finds` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `searchId` INTEGER NOT NULL,
                `foundAt` INTEGER NOT NULL,
                `tid` TEXT NOT NULL,
                `findNo` INTEGER NOT NULL,
                FOREIGN KEY(`searchId`) REFERENCES `duplicate_searches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_duplicate_finds_tid` ON `duplicate_finds` (`tid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_duplicate_finds_searchId` ON `duplicate_finds` (`searchId`)")
    }
}
```

- [ ] **Step 8: Migration registrieren**

In `app/src/main/java/de/ca/qcc/di/AppModule.kt` Import `import de.ca.qcc.data.local.MIGRATION_6_7` ergänzen und die Zeile `.addMigrations(...)` ersetzen durch:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

- [ ] **Step 9: Test laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.data.local.MigrationTest"`
Expected: PASS. `app/schemas/de.ca.qcc.data.local.AppDatabase/7.json` wurde erzeugt.
Falls `runMigrationsAndValidate` mit „Migration didn't properly handle“ scheitert: `createSql` aus `7.json` für beide Tabellen/Indizes 1:1 in `MIGRATION_6_7` übernehmen (`${TABLE_NAME}` durch Tabellennamen ersetzen) und erneut laufen lassen.

- [ ] **Step 10: Commit**

```bash
git add app/build.gradle.kts app/schemas/de.ca.qcc.data.local.AppDatabase/7.json app/src/main/java/de/ca/qcc/data/local app/src/main/java/de/ca/qcc/di/AppModule.kt app/src/test/java/de/ca/qcc/data/local/MigrationTest.kt
git commit -m "feat(db): add duplicate search tables (schema v7)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: CSV-Writer extrahieren und DuplicateSearchRepository

**Files:**
- Create: `app/src/main/java/de/ca/qcc/data/export/CsvDocumentWriter.kt`
- Modify: `app/src/main/java/de/ca/qcc/data/repository/Repositories.kt`
- Modify: `app/src/main/java/de/ca/qcc/domain/model/Models.kt`
- Create: `app/src/main/java/de/ca/qcc/data/repository/DuplicateSearchRepository.kt`
- Modify: `app/src/main/java/de/ca/qcc/di/AppModule.kt`
- Create: `app/src/test/java/de/ca/qcc/testutil/DuplicateFakes.kt`
- Test: `app/src/test/java/de/ca/qcc/data/repository/DuplicateSearchRepositoryTest.kt`

- [ ] **Step 1: Test-Hilfen anlegen**

`app/src/test/java/de/ca/qcc/testutil/DuplicateFakes.kt`:

```kotlin
package de.ca.qcc.testutil

import android.content.Context
import androidx.room.Room
import de.ca.qcc.data.export.CsvDocumentWriter
import de.ca.qcc.data.local.AppDatabase

fun inMemoryDb(context: Context): AppDatabase =
    Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

class FakeCsvWriter : CsvDocumentWriter {
    val files = mutableMapOf<String, String>()
    override suspend fun write(fileName: String, content: String) {
        files[fileName] = content
    }
}
```

- [ ] **Step 2: Failing Repository-Test schreiben**

`app/src/test/java/de/ca/qcc/data/repository/DuplicateSearchRepositoryTest.kt`:

```kotlin
package de.ca.qcc.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.domain.model.RegisterFindResult
import de.ca.qcc.testutil.FakeCsvWriter
import de.ca.qcc.testutil.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DuplicateSearchRepositoryTest {

    // SGTIN encoded with the default filter value 1
    private val target = LocateTarget(
        epc = "3034F824285A980300000006",
        sgtin = "4065546.092768.12884901894"
    )

    private lateinit var db: AppDatabase
    private lateinit var writer: FakeCsvWriter
    private lateinit var repository: DuplicateSearchRepository

    @Before
    fun setUp() {
        db = inMemoryDb(ApplicationProvider.getApplicationContext())
        writer = FakeCsvWriter()
        repository = DuplicateSearchRepository(db, writer)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun registerFind_numbersFindsSequentially_andNormalizesTid() = runTest {
        val id = repository.startSearch(target, store = "0815")

        assertEquals(RegisterFindResult.Saved(1), repository.registerFind(id, "e280aa"))
        assertEquals(RegisterFindResult.Saved(2), repository.registerFind(id, "E280BB"))
    }

    @Test
    fun registerFind_knownTid_isAlreadyFound_evenInLaterSearch() = runTest {
        val first = repository.startSearch(target, store = null)
        repository.registerFind(first, "E280AA")
        repository.finishSearch(first)

        val second = repository.startSearch(target, store = null)

        assertEquals(RegisterFindResult.AlreadyFound(1), repository.registerFind(second, "e280aa"))
    }

    @Test
    fun getOpenSearch_restoresTargetStoreAndCount() = runTest {
        val id = repository.startSearch(target, store = "0815")
        repository.registerFind(id, "E280AA")

        val open = repository.getOpenSearch()!!

        assertEquals(id, open.id)
        assertEquals(target, open.target)
        assertEquals("0815", open.store)
        assertEquals(1, open.foundCount)
    }

    @Test
    fun finishSearch_withFinds_writesCsv_andClosesSearch() = runTest {
        val id = repository.startSearch(target, store = "0815")
        repository.registerFind(id, "E280AA")
        repository.registerFind(id, "E280BB")

        val fileName = repository.finishSearch(id)!!

        assertTrue(fileName.matches(Regex("""duplicates_\d{8}_\d{6}\.csv""")))
        val lines = writer.files.getValue(fileName).trim().lines()
        assertEquals("Timestamp,SGTIN,EPC,TID,Find No,Store", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].endsWith(",4065546.092768.12884901894,3034F824285A980300000006,E280AA,1,0815"))
        assertTrue(lines[2].endsWith(",E280BB,2,0815"))
        assertNull(repository.getOpenSearch())
    }

    @Test
    fun finishSearch_withoutFinds_writesNoFile() = runTest {
        val id = repository.startSearch(target, store = null)

        assertNull(repository.finishSearch(id))
        assertTrue(writer.files.isEmpty())
        assertNull(repository.getOpenSearch())
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.data.repository.DuplicateSearchRepositoryTest"`
Expected: FAIL – `Unresolved reference: CsvDocumentWriter` / `DuplicateSearchRepository`.

- [ ] **Step 4: `CsvDocumentWriter` anlegen**

`app/src/main/java/de/ca/qcc/data/export/CsvDocumentWriter.kt`:

```kotlin
package de.ca.qcc.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CsvDocumentWriter {
    suspend fun write(fileName: String, content: String)
}

// Writes into the public Documents directory (MediaStore on API 29+)
class MediaStoreCsvDocumentWriter(private val context: Context) : CsvDocumentWriter {
    override suspend fun write(fileName: String, content: String): Unit = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: throw IllegalStateException("MediaStore insert failed")

            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: throw IllegalStateException("Could not open output stream")
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                .also { if (!it.exists()) it.mkdirs() }
            java.io.File(dir, fileName).writeText(content)
        }
    }
}

fun String.csvEscape(): String {
    val needsQuoting = contains(',') || contains('"') || contains('\n') || contains('\r')
    return if (needsQuoting) "\"${replace("\"", "\"\"")}\"" else this
}
```

- [ ] **Step 5: `MismatchRepository` auf den Writer umstellen**

In `app/src/main/java/de/ca/qcc/data/repository/Repositories.kt`:

1. Die private Funktion `private fun String.csvEscape(): String { … }` löschen.
2. Imports `android.content.ContentValues`, `android.os.Build`, `android.os.Environment`, `android.provider.MediaStore` entfernen; ergänzen:

```kotlin
import de.ca.qcc.data.export.MediaStoreCsvDocumentWriter
import de.ca.qcc.data.export.csvEscape
```

3. Den kompletten Block von `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {` bis einschließlich der schließenden `}` des `else`-Zweigs ersetzen durch:

```kotlin
            MediaStoreCsvDocumentWriter(context).write(fileName, csvContent)
```

- [ ] **Step 6: Domain-Modelle ergänzen**

Am Ende von `app/src/main/java/de/ca/qcc/domain/model/Models.kt`:

```kotlin
data class DuplicateSearch(
    val id: Long,
    val target: LocateTarget,
    val store: String?,
    val foundCount: Int
)

sealed interface RegisterFindResult {
    data class Saved(val findNo: Int) : RegisterFindResult
    data class AlreadyFound(val findNo: Int) : RegisterFindResult
}
```

- [ ] **Step 7: Repository implementieren**

`app/src/main/java/de/ca/qcc/data/repository/DuplicateSearchRepository.kt`:

```kotlin
package de.ca.qcc.data.repository

import de.ca.qcc.data.export.CsvDocumentWriter
import de.ca.qcc.data.export.csvEscape
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.data.local.DuplicateFindEntity
import de.ca.qcc.data.local.DuplicateSearchEntity
import de.ca.qcc.domain.model.DuplicateSearch
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.domain.model.RegisterFindResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class DuplicateSearchRepository @Inject constructor(
    private val db: AppDatabase,
    private val csvWriter: CsvDocumentWriter
) {
    private val dao get() = db.duplicateDao()

    suspend fun startSearch(target: LocateTarget, store: String?): Long =
        dao.insertSearch(
            DuplicateSearchEntity(
                startedAt = System.currentTimeMillis(),
                sgtin = target.sgtin,
                epc = target.epc,
                store = store
            )
        )

    suspend fun getOpenSearch(): DuplicateSearch? {
        val search = dao.getOpenSearch() ?: return null
        return DuplicateSearch(
            id = search.id,
            target = search.toTarget(),
            store = search.store,
            foundCount = dao.countFinds(search.id)
        )
    }

    // TID is globally unique: a tag found in any earlier search is reported, not counted again
    suspend fun registerFind(searchId: Long, tid: String): RegisterFindResult {
        val normalized = tid.uppercase()
        dao.findByTid(normalized)?.let { return RegisterFindResult.AlreadyFound(it.findNo) }
        val findNo = dao.countFinds(searchId) + 1
        dao.insertFind(
            DuplicateFindEntity(
                searchId = searchId,
                foundAt = System.currentTimeMillis(),
                tid = normalized,
                findNo = findNo
            )
        )
        return RegisterFindResult.Saved(findNo)
    }

    // Returns the exported file name, or null when the search had no finds
    suspend fun finishSearch(searchId: Long): String? {
        val search = dao.getSearch(searchId) ?: return null
        val finds = dao.getFinds(searchId)
        val fileName = if (finds.isEmpty()) {
            null
        } else {
            val name = "duplicates_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
            csvWriter.write(name, buildDuplicatesCsv(search, finds))
            name
        }
        dao.finishSearch(searchId, System.currentTimeMillis(), fileName)
        return fileName
    }

    // The stored EPC already carries the chosen filter value; locating is always exact
    private fun DuplicateSearchEntity.toTarget() = LocateTarget(epc = epc, sgtin = sgtin)
}

// Column headers stay English regardless of app language
internal fun buildDuplicatesCsv(search: DuplicateSearchEntity, finds: List<DuplicateFindEntity>): String {
    val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return buildString {
        appendLine("Timestamp,SGTIN,EPC,TID,Find No,Store")
        finds.forEach { find ->
            appendLine(
                listOf(
                    tsFormat.format(Date(find.foundAt)),
                    search.sgtin,
                    search.epc,
                    find.tid,
                    find.findNo.toString(),
                    search.store.orEmpty()
                ).joinToString(",") { it.csvEscape() }
            )
        }
    }
}
```

- [ ] **Step 8: Writer in Hilt bereitstellen**

In `app/src/main/java/de/ca/qcc/di/AppModule.kt` Imports ergänzen:

```kotlin
import de.ca.qcc.data.export.CsvDocumentWriter
import de.ca.qcc.data.export.MediaStoreCsvDocumentWriter
```

Im `object AppModule` nach `provideMismatchRepository` einfügen:

```kotlin
    @Provides
    fun provideCsvDocumentWriter(@ApplicationContext context: Context): CsvDocumentWriter =
        MediaStoreCsvDocumentWriter(context)
```

- [ ] **Step 9: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (neue Repository-Tests + alle bisherigen).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/de/ca/qcc/data app/src/main/java/de/ca/qcc/domain/model/Models.kt app/src/main/java/de/ca/qcc/di/AppModule.kt app/src/test/java/de/ca/qcc/testutil app/src/test/java/de/ca/qcc/data/repository
git commit -m "feat(data): add duplicate search repository and shared CSV writer

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Näherungs-Feedback und Filial-Speicher

**Files:**
- Create: `app/src/main/java/de/ca/qcc/feature/duplicates/ProximityFeedback.kt`
- Create: `app/src/main/java/de/ca/qcc/data/local/StorePreferences.kt`
- Modify: `app/src/main/java/de/ca/qcc/di/AppModule.kt`
- Test: `app/src/test/java/de/ca/qcc/feature/duplicates/BeepIntervalTest.kt`

- [ ] **Step 1: Failing Test schreiben**

`app/src/test/java/de/ca/qcc/feature/duplicates/BeepIntervalTest.kt`:

```kotlin
package de.ca.qcc.feature.duplicates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeepIntervalTest {

    @Test
    fun noSignal_isSilent() {
        assertNull(beepIntervalMs(0))
        assertNull(beepIntervalMs(-5))
    }

    @Test
    fun interval_shrinksLinearlyFrom1000To80() {
        assertEquals(1000L, beepIntervalMs(1))
        assertEquals(545L, beepIntervalMs(50))
        assertEquals(80L, beepIntervalMs(100))
    }

    @Test
    fun valuesAbove100_areClamped() {
        assertEquals(80L, beepIntervalMs(150))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.feature.duplicates.BeepIntervalTest"`
Expected: FAIL – `Unresolved reference: beepIntervalMs`.

- [ ] **Step 3: Feedback implementieren**

`app/src/main/java/de/ca/qcc/feature/duplicates/ProximityFeedback.kt`:

```kotlin
package de.ca.qcc.feature.duplicates

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

interface ProximityFeedback {
    fun update(proximity: Int)
    fun stop()
    fun release()
}

// Pause between beeps: silent at 0, 1000 ms at 1 %, 80 ms at 100 %
fun beepIntervalMs(proximity: Int): Long? =
    if (proximity <= 0) null else 1000L - (proximity.coerceAtMost(100) - 1) * 920L / 99

class ToneProximityFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) : ProximityFeedback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var proximity = 0
    private var loop: Job? = null
    private var tone: ToneGenerator? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Synchronized
    override fun update(proximity: Int) {
        this.proximity = proximity
        if (loop?.isActive != true) loop = scope.launch { beepLoop() }
    }

    @Synchronized
    override fun stop() {
        loop?.cancel()
        loop = null
        proximity = 0
    }

    override fun release() {
        stop()
        tone?.release()
        tone = null
    }

    private suspend fun beepLoop() {
        while (currentCoroutineContext().isActive) {
            val interval = beepIntervalMs(proximity)
            if (interval == null) {
                delay(IDLE_POLL_MS)
                continue
            }
            beep()
            delay(interval)
        }
    }

    private fun beep() {
        runCatching {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private companion object {
        const val IDLE_POLL_MS = 100L
        const val BEEP_MS = 40
        const val VIBRATE_MS = 25L
    }
}
```

- [ ] **Step 4: Filial-Speicher anlegen**

`app/src/main/java/de/ca/qcc/data/local/StorePreferences.kt`:

```kotlin
package de.ca.qcc.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class StorePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("duplicate_search", Context.MODE_PRIVATE)

    var lastStore: String
        get() = prefs.getString(KEY_LAST_STORE, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_STORE, value).apply()
        }

    private companion object {
        const val KEY_LAST_STORE = "last_store"
    }
}
```

- [ ] **Step 5: Feedback in Hilt binden**

In `app/src/main/java/de/ca/qcc/di/AppModule.kt` Imports ergänzen:

```kotlin
import de.ca.qcc.feature.duplicates.ProximityFeedback
import de.ca.qcc.feature.duplicates.ToneProximityFeedback
```

In `abstract class GatewayModule` nach `bindRfidReader` einfügen:

```kotlin
    @Binds
    abstract fun bindProximityFeedback(impl: ToneProximityFeedback): ProximityFeedback
```

- [ ] **Step 6: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.feature.duplicates.BeepIntervalTest" assembleDebug`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/de/ca/qcc/feature/duplicates/ProximityFeedback.kt app/src/main/java/de/ca/qcc/data/local/StorePreferences.kt app/src/main/java/de/ca/qcc/di/AppModule.kt app/src/test/java/de/ca/qcc/feature/duplicates/BeepIntervalTest.kt
git commit -m "feat(duplicates): add proximity beep feedback and store preference

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: DuplicateSearchViewModel

**Files:**
- Modify: `app/src/test/java/de/ca/qcc/testutil/DuplicateFakes.kt`
- Create: `app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModel.kt`
- Test: `app/src/test/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModelTest.kt`

- [ ] **Step 1: Fakes ergänzen**

Am Ende von `app/src/test/java/de/ca/qcc/testutil/DuplicateFakes.kt` anfügen (Imports oben ergänzen):

```kotlin
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.domain.model.ReaderStatus
import de.ca.qcc.feature.duplicates.ProximityFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
```

```kotlin
class FakeDuplicateReader : RfidReaderGateway {
    val status = MutableStateFlow(ReaderStatus(isConnected = true))
    val proximity = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val trigger = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)
    var mode = TriggerMode.QCC
    var epcsNearby: Result<List<String>> = Result.success(emptyList())
    var tidsNearby: Result<List<String>> = Result.success(emptyList())
    val locateCalls = mutableListOf<Pair<String, String>>()
    var locating = false

    override fun observeStatus(): Flow<ReaderStatus> = status
    override fun observeTagScans(): Flow<String> = emptyFlow()
    override suspend fun discoverReaders(): List<String> = emptyList()
    override suspend fun connect(readerId: String): Result<Unit> = Result.success(Unit)
    override suspend fun disconnect() = Unit
    override suspend fun triggerInventory() = Unit
    override suspend fun setPower(level: Int): Result<Unit> = Result.success(Unit)
    override fun setTriggerMode(mode: TriggerMode) { this.mode = mode }
    override fun observeHandheldTrigger(): Flow<Boolean> = trigger
    override fun observeProximity(): Flow<Int> = proximity
    override suspend fun startLocate(epc: String, mask: String): Result<Unit> {
        locateCalls += epc to mask
        locating = true
        return Result.success(Unit)
    }
    override suspend fun stopLocate() { locating = false }
    override suspend fun readEpcsNearby(): Result<List<String>> = epcsNearby
    override suspend fun readTidsNearby(epc: String, mask: String): Result<List<String>> = tidsNearby
}

class FakeProximityFeedback : ProximityFeedback {
    val updates = mutableListOf<Int>()
    var stopCount = 0
    override fun update(proximity: Int) { updates += proximity }
    override fun stop() { stopCount++ }
    override fun release() = Unit
}
```

- [ ] **Step 2: Failing ViewModel-Test schreiben**

`app/src/test/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModelTest.kt`:

```kotlin
package de.ca.qcc.feature.duplicates

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.data.local.StorePreferences
import de.ca.qcc.data.repository.DuplicateSearchRepository
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.testutil.FakeCsvWriter
import de.ca.qcc.testutil.FakeDuplicateReader
import de.ca.qcc.testutil.FakeProximityFeedback
import de.ca.qcc.testutil.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DuplicateSearchViewModelTest {

    // SGTIN input with the default filter value 1 encodes to exactly this EPC
    private val epc = "3034F824285A980300000006"
    private val sgtin = "4065546.092768.12884901894"
    private val tid = "E2801191A503006B7A41A41F"

    private lateinit var db: AppDatabase
    private lateinit var writer: FakeCsvWriter
    private lateinit var repository: DuplicateSearchRepository
    private lateinit var prefs: StorePreferences
    private lateinit var reader: FakeDuplicateReader
    private lateinit var feedback: FakeProximityFeedback

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = inMemoryDb(app)
        writer = FakeCsvWriter()
        repository = DuplicateSearchRepository(db, writer)
        prefs = StorePreferences(app)
        reader = FakeDuplicateReader()
        feedback = FakeProximityFeedback()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DuplicateSearchViewModel(reader, repository, prefs, feedback)

    private fun DuplicateSearchViewModel.startSearchFor(input: String) {
        onInputChange(input)
        applyInput()
        startSearch()
    }

    @Test
    fun init_usesCaptureMode_andPrefillsLastStore() {
        prefs.lastStore = "0815"

        val vm = createViewModel()

        assertEquals(TriggerMode.CAPTURE, reader.mode)
        assertEquals("0815", vm.uiState.value.store)
        assertEquals(DuplicatePhase.TARGET, vm.uiState.value.phase)
    }

    @Test
    fun triggerInTargetPhase_withOneTag_setsTarget() = runTest {
        reader.epcsNearby = Result.success(listOf(epc))
        val vm = createViewModel()

        reader.trigger.emit(true)

        assertEquals(epc, vm.uiState.value.target?.epc)
        assertEquals(sgtin, vm.uiState.value.target?.sgtin)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun capture_withSeveralTags_asksToIsolate() {
        reader.epcsNearby = Result.success(listOf(epc, "3034F824285A980300000007"))
        val vm = createViewModel()

        vm.captureFromReader()

        assertNull(vm.uiState.value.target)
        assertEquals(DuplicateMessage.CaptureMultipleTags, vm.uiState.value.message)
    }

    @Test
    fun capture_withoutTag_reportsNoTag() {
        val vm = createViewModel()

        vm.captureFromReader()

        assertEquals(DuplicateMessage.CaptureNoTag, vm.uiState.value.message)
    }

    @Test
    fun invalidInput_showsInvalidTarget() {
        val vm = createViewModel()

        vm.onInputChange("not-a-target")
        vm.applyInput()

        assertNull(vm.uiState.value.target)
        assertEquals(DuplicateMessage.InvalidTarget, vm.uiState.value.message)
    }

    @Test
    fun sgtinInput_withDefaultFilter_encodesFilterOne() {
        val vm = createViewModel()

        vm.onInputChange(sgtin)
        vm.applyInput()

        assertEquals(LocateTarget.DEFAULT_SGTIN_FILTER, vm.uiState.value.sgtinFilter)
        assertEquals("3034F824285A980300000006", vm.uiState.value.target?.epc)
        assertEquals(sgtin, vm.uiState.value.target?.sgtin)
    }

    @Test
    fun sgtinInput_withFilterZero_encodesFilterZero() {
        val vm = createViewModel()

        vm.onSgtinFilterChange(0)
        vm.onInputChange(sgtin)
        vm.applyInput()

        assertEquals(0, vm.uiState.value.sgtinFilter)
        assertEquals("3014F824285A980300000006", vm.uiState.value.target?.epc)
    }

    @Test
    fun changingSgtinFilter_clearsExistingTarget() {
        val vm = createViewModel()
        vm.onInputChange(sgtin)
        vm.applyInput()
        assertNotNull(vm.uiState.value.target)

        vm.onSgtinFilterChange(2)

        assertNull(vm.uiState.value.target)
        assertNull(vm.uiState.value.message)
        assertEquals(2, vm.uiState.value.sgtinFilter)
    }

    @Test
    fun startSearch_switchesToLocateMode_andRemembersStore() {
        val vm = createViewModel()
        vm.onStoreChange(" 0815 ")

        vm.startSearchFor(sgtin)

        assertEquals(DuplicatePhase.SEARCH, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.searchId)
        assertEquals(TriggerMode.LOCATE, reader.mode)
        assertEquals("0815", prefs.lastStore)
    }

    @Test
    fun startSearch_withoutReader_isBlocked() {
        reader.status.value = reader.status.value.copy(isConnected = false)
        val vm = createViewModel()

        vm.startSearchFor(sgtin)

        assertEquals(DuplicatePhase.TARGET, vm.uiState.value.phase)
        assertEquals(DuplicateMessage.ReaderNotConnected, vm.uiState.value.message)
    }

    @Test
    fun holdingTrigger_locatesExactly_andDrivesFeedback() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(sgtin)

        reader.trigger.emit(true)
        assertEquals(epc to LocateTarget.FULL_MASK, reader.locateCalls.last())
        assertTrue(vm.uiState.value.locating)

        reader.proximity.emit(70)
        assertEquals(70, vm.uiState.value.proximity)
        assertEquals(listOf(70), feedback.updates)

        reader.trigger.emit(false)
        assertFalse(vm.uiState.value.locating)
        assertFalse(reader.locating)
        assertEquals(0, vm.uiState.value.proximity)
        assertTrue(feedback.stopCount > 0)
    }

    @Test
    fun continuousSearch_runsWithoutTrigger_andIgnoresRelease() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)

        vm.setContinuous(true)
        assertTrue(reader.locating)
        assertEquals(epc to LocateTarget.FULL_MASK, reader.locateCalls.last())

        reader.trigger.emit(false)
        assertTrue(vm.uiState.value.locating)
    }

    @Test
    fun readerDisconnect_duringSearch_stopsLocating() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        vm.setContinuous(true)

        reader.status.value = reader.status.value.copy(isConnected = false)

        assertFalse(vm.uiState.value.locating)
        assertFalse(vm.uiState.value.continuous)
        assertEquals(DuplicateMessage.ReaderNotConnected, vm.uiState.value.message)
    }

    @Test
    fun confirmFind_handlesAllOutcomes() {
        val vm = createViewModel()
        vm.startSearchFor(epc)

        reader.tidsNearby = Result.success(emptyList())
        vm.confirmFind()
        assertEquals(DuplicateMessage.NoTagAtReader, vm.uiState.value.message)

        reader.tidsNearby = Result.success(listOf("T1", "T2"))
        vm.confirmFind()
        assertEquals(DuplicateMessage.MultipleTags, vm.uiState.value.message)
        assertEquals(0, vm.uiState.value.foundCount)

        reader.tidsNearby = Result.success(listOf(tid.lowercase()))
        vm.confirmFind()
        assertEquals(DuplicateMessage.Saved(1), vm.uiState.value.message)
        assertEquals(1, vm.uiState.value.foundCount)
        assertEquals(tid, vm.uiState.value.lastTid)

        vm.confirmFind()
        assertEquals(DuplicateMessage.AlreadyFound(1), vm.uiState.value.message)
        assertEquals(1, vm.uiState.value.foundCount)

        reader.tidsNearby = Result.failure(IllegalStateException("boom"))
        vm.confirmFind()
        assertEquals(DuplicateMessage.ReaderError("boom"), vm.uiState.value.message)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun confirmFind_inContinuousMode_resumesLocating() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        vm.setContinuous(true)
        reader.tidsNearby = Result.success(listOf(tid))

        vm.confirmFind()

        assertTrue(vm.uiState.value.locating)
        assertEquals(2, reader.locateCalls.size)
    }

    @Test
    fun endSearch_withFinds_exports_andReturnsToTargetPhase() {
        val vm = createViewModel()
        vm.onStoreChange("0815")
        vm.onSgtinFilterChange(3)
        vm.startSearchFor(epc)
        reader.tidsNearby = Result.success(listOf(tid))
        vm.confirmFind()

        vm.endSearch()

        val state = vm.uiState.value
        val fileName = writer.files.keys.single()
        assertEquals(DuplicateMessage.Exported(fileName), state.message)
        assertEquals(DuplicatePhase.TARGET, state.phase)
        assertNull(state.target)
        assertEquals("0815", state.store)
        assertEquals(3, state.sgtinFilter)
        assertEquals(TriggerMode.CAPTURE, reader.mode)
    }

    @Test
    fun endSearch_withoutFinds_exportsNothing() {
        val vm = createViewModel()
        vm.startSearchFor(epc)

        vm.endSearch()

        assertEquals(DuplicateMessage.NothingExported, vm.uiState.value.message)
        assertTrue(writer.files.isEmpty())
    }

    @Test
    fun openSearch_isResumedOnStart() = runTest {
        val target = LocateTarget(epc, sgtin)
        val id = repository.startSearch(target, store = "0815")
        repository.registerFind(id, tid)

        val vm = createViewModel()

        val state = vm.uiState.value
        assertEquals(DuplicatePhase.SEARCH, state.phase)
        assertEquals(id, state.searchId)
        assertEquals(1, state.foundCount)
        assertEquals("0815", state.store)
        assertEquals(TriggerMode.LOCATE, reader.mode)
    }

    @Test
    fun screenStopped_stopsLocating_andRestoresQccMode() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        vm.setContinuous(true)

        vm.onScreenStopped()

        assertFalse(reader.locating)
        assertFalse(vm.uiState.value.continuous)
        assertEquals(TriggerMode.QCC, reader.mode)

        vm.onScreenStarted()
        assertEquals(TriggerMode.LOCATE, reader.mode)
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.ca.qcc.feature.duplicates.DuplicateSearchViewModelTest"`
Expected: FAIL – `Unresolved reference: DuplicateSearchViewModel`.

- [ ] **Step 4: ViewModel implementieren**

`app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModel.kt`:

```kotlin
package de.ca.qcc.feature.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ca.qcc.core.gs1.Gs1ParserService
import de.ca.qcc.data.local.StorePreferences
import de.ca.qcc.data.repository.DuplicateSearchRepository
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.domain.model.RegisterFindResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DuplicatePhase { TARGET, SEARCH }

// Typed so the UI can localize; the ViewModel never builds user-visible text
sealed interface DuplicateMessage {
    data class Saved(val findNo: Int) : DuplicateMessage
    data class AlreadyFound(val findNo: Int) : DuplicateMessage
    data object NoTagAtReader : DuplicateMessage
    data object MultipleTags : DuplicateMessage
    data object CaptureNoTag : DuplicateMessage
    data object CaptureMultipleTags : DuplicateMessage
    data object InvalidTarget : DuplicateMessage
    data object ReaderNotConnected : DuplicateMessage
    data class ReaderError(val detail: String) : DuplicateMessage
    data class Exported(val fileName: String) : DuplicateMessage
    data object NothingExported : DuplicateMessage
    data class ExportFailed(val detail: String) : DuplicateMessage
}

data class DuplicateUiState(
    val phase: DuplicatePhase = DuplicatePhase.TARGET,
    val input: String = "",
    val sgtinFilter: Int = LocateTarget.DEFAULT_SGTIN_FILTER,
    val target: LocateTarget? = null,
    val store: String = "",
    val searchId: Long? = null,
    val foundCount: Int = 0,
    val lastTid: String? = null,
    val proximity: Int = 0,
    val locating: Boolean = false,
    val continuous: Boolean = false,
    val busy: Boolean = false,
    val readerConnected: Boolean = false,
    val message: DuplicateMessage? = null
)

@HiltViewModel
class DuplicateSearchViewModel @Inject constructor(
    private val rfid: RfidReaderGateway,
    private val repository: DuplicateSearchRepository,
    private val storePreferences: StorePreferences,
    private val feedback: ProximityFeedback
) : ViewModel() {

    private val parser = Gs1ParserService()

    private val _uiState = MutableStateFlow(DuplicateUiState(store = storePreferences.lastStore))
    val uiState: StateFlow<DuplicateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            rfid.observeStatus().collect { status ->
                _uiState.update { it.copy(readerConnected = status.isConnected) }
                // Connection lost mid-search: stop locating; finds are already persisted
                if (!status.isConnected && _uiState.value.locating) {
                    _uiState.update { it.copy(continuous = false, message = DuplicateMessage.ReaderNotConnected) }
                    stopLocating()
                }
            }
        }
        viewModelScope.launch { rfid.observeProximity().collect(::onProximity) }
        viewModelScope.launch { rfid.observeHandheldTrigger().collect(::onTrigger) }
        viewModelScope.launch {
            repository.getOpenSearch()?.let { open ->
                _uiState.update {
                    it.copy(
                        phase = DuplicatePhase.SEARCH,
                        input = open.target.epc,
                        target = open.target,
                        store = open.store.orEmpty(),
                        searchId = open.id,
                        foundCount = open.foundCount
                    )
                }
            }
            applyTriggerMode()
        }
    }

    // ── Screen lifecycle ──

    fun onScreenStarted() = applyTriggerMode()

    fun onScreenStopped() {
        _uiState.update { it.copy(continuous = false) }
        viewModelScope.launch { stopLocating() }
        rfid.setTriggerMode(TriggerMode.QCC)
    }

    override fun onCleared() {
        feedback.release()
        rfid.setTriggerMode(TriggerMode.QCC)
    }

    // ── Phase 1: target ──

    fun onInputChange(text: String) =
        _uiState.update { it.copy(input = text, target = null, message = null) }

    // Only affects typed SGTINs; a hex EPC carries its own filter value
    fun onSgtinFilterChange(filter: Int) =
        _uiState.update { it.copy(sgtinFilter = filter, target = null, message = null) }

    fun onStoreChange(text: String) = _uiState.update { it.copy(store = text) }

    fun applyInput() {
        val state = _uiState.value
        runCatching { parser.parseLocateTarget(state.input, state.sgtinFilter) }
            .onSuccess { target -> _uiState.update { it.copy(target = target, message = null) } }
            .onFailure { _uiState.update { it.copy(target = null, message = DuplicateMessage.InvalidTarget) } }
    }

    fun captureFromReader() {
        val state = _uiState.value
        if (state.busy) return
        if (!state.readerConnected) {
            _uiState.update { it.copy(message = DuplicateMessage.ReaderNotConnected) }
            return
        }
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            rfid.readEpcsNearby()
                .onSuccess { epcs ->
                    when (epcs.size) {
                        0 -> _uiState.update { it.copy(message = DuplicateMessage.CaptureNoTag) }
                        1 -> {
                            _uiState.update { it.copy(input = epcs.single()) }
                            applyInput()
                        }
                        else -> _uiState.update { it.copy(message = DuplicateMessage.CaptureMultipleTags) }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(message = e.toReaderError()) } }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun startSearch() {
        val state = _uiState.value
        val target = state.target ?: return
        if (!state.readerConnected) {
            _uiState.update { it.copy(message = DuplicateMessage.ReaderNotConnected) }
            return
        }
        viewModelScope.launch {
            val store = state.store.trim()
            storePreferences.lastStore = store
            val searchId = repository.startSearch(target, store.ifBlank { null })
            _uiState.update {
                it.copy(
                    phase = DuplicatePhase.SEARCH,
                    store = store,
                    searchId = searchId,
                    foundCount = 0,
                    lastTid = null,
                    message = null
                )
            }
            applyTriggerMode()
        }
    }

    // ── Phase 2: search ──

    fun setContinuous(enabled: Boolean) {
        _uiState.update { it.copy(continuous = enabled) }
        viewModelScope.launch { if (enabled) startLocating() else stopLocating() }
    }

    fun confirmFind() {
        val state = _uiState.value
        val target = state.target ?: return
        val searchId = state.searchId ?: return
        if (state.busy) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            stopLocating()
            val message = rfid.readTidsNearby(target.epc, LocateTarget.FULL_MASK).fold(
                onSuccess = { tids ->
                    when (tids.size) {
                        0 -> DuplicateMessage.NoTagAtReader
                        1 -> registerFind(searchId, tids.single())
                        else -> DuplicateMessage.MultipleTags
                    }
                },
                onFailure = { it.toReaderError() }
            )
            _uiState.update { it.copy(busy = false, message = message) }
            if (_uiState.value.continuous) startLocating()
        }
    }

    fun endSearch() {
        val state = _uiState.value
        val searchId = state.searchId ?: return
        if (state.busy) return
        _uiState.update { it.copy(busy = true, continuous = false) }
        viewModelScope.launch {
            stopLocating()
            runCatching { repository.finishSearch(searchId) }
                .onSuccess { fileName ->
                    val current = _uiState.value
                    _uiState.value = DuplicateUiState(
                        sgtinFilter = current.sgtinFilter,
                        store = current.store,
                        readerConnected = current.readerConnected,
                        message = fileName?.let { DuplicateMessage.Exported(it) }
                            ?: DuplicateMessage.NothingExported
                    )
                    applyTriggerMode()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(busy = false, message = DuplicateMessage.ExportFailed(e.message ?: e.javaClass.simpleName))
                    }
                }
        }
    }

    // ── Internals ──

    private suspend fun registerFind(searchId: Long, tid: String): DuplicateMessage =
        when (val result = repository.registerFind(searchId, tid)) {
            is RegisterFindResult.Saved -> {
                _uiState.update { it.copy(foundCount = result.findNo, lastTid = tid.uppercase()) }
                DuplicateMessage.Saved(result.findNo)
            }
            is RegisterFindResult.AlreadyFound -> DuplicateMessage.AlreadyFound(result.findNo)
        }

    private fun onTrigger(pressed: Boolean) {
        val state = _uiState.value
        when (state.phase) {
            DuplicatePhase.TARGET -> if (pressed) captureFromReader()
            DuplicatePhase.SEARCH -> if (!state.continuous && !state.busy) {
                viewModelScope.launch { if (pressed) startLocating() else stopLocating() }
            }
        }
    }

    private fun onProximity(value: Int) {
        if (!_uiState.value.locating) return
        val proximity = value.coerceIn(0, 100)
        _uiState.update { it.copy(proximity = proximity) }
        feedback.update(proximity)
    }

    private suspend fun startLocating() {
        val state = _uiState.value
        val target = state.target ?: return
        if (state.locating || state.phase != DuplicatePhase.SEARCH) return
        _uiState.update { it.copy(locating = true) }
        rfid.startLocate(target.epc, LocateTarget.FULL_MASK).onFailure { e ->
            _uiState.update { it.copy(locating = false, continuous = false, message = e.toReaderError()) }
        }
    }

    private suspend fun stopLocating() {
        if (!_uiState.value.locating) return
        _uiState.update { it.copy(locating = false, proximity = 0) }
        feedback.stop()
        rfid.stopLocate()
    }

    private fun applyTriggerMode() {
        rfid.setTriggerMode(
            if (_uiState.value.phase == DuplicatePhase.SEARCH) TriggerMode.LOCATE else TriggerMode.CAPTURE
        )
    }

    private fun Throwable.toReaderError() =
        DuplicateMessage.ReaderError(message ?: javaClass.simpleName)
}
```

- [ ] **Step 5: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (alle Tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModel.kt app/src/test/java/de/ca/qcc/testutil/DuplicateFakes.kt app/src/test/java/de/ca/qcc/feature/duplicates/DuplicateSearchViewModelTest.kt
git commit -m "feat(duplicates): add duplicate search view model

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Texte EN/DE/PL

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-pl/strings.xml`

- [ ] **Step 1: Englisch (Standard)**

In `app/src/main/res/values/strings.xml` vor `</resources>` einfügen:

```xml
    <string name="find_duplicates">Find duplicates</string>
    <string name="dup_target_hint">EPC or SGTIN</string>
    <string name="dup_sgtin_filter">Filter value (SGTIN)</string>
    <string name="dup_sgtin_filter_hint">Usually 1 (retail item). Only relevant for typed SGTIN.</string>
    <string name="dup_read_with_reader">Read with reader</string>
    <string name="dup_hint_capture">Or pull the trigger with the item held to the reader</string>
    <string name="dup_store_optional">Store (optional)</string>
    <string name="dup_start_search">Start search</string>
    <string name="dup_target_sgtin">Target SGTIN</string>
    <string name="dup_epc">EPC</string>
    <string name="dup_store">Store</string>
    <string name="dup_proximity">proximity</string>
    <string name="dup_hint_hold_trigger">Hold trigger and sweep the reader</string>
    <string name="dup_hint_continuous">Continuous search active – sweep the reader</string>
    <string name="dup_continuous">Continuous search</string>
    <string name="dup_continuous_sub">Search without trigger</string>
    <string name="dup_found_count">Found (unique TIDs)</string>
    <string name="dup_last">Last</string>
    <string name="dup_confirm_find">Confirm find</string>
    <string name="dup_end_search">End search</string>
    <string name="dup_msg_saved">Find #%1$d saved – remove the item</string>
    <string name="dup_msg_already_found">Already found (#%1$d)</string>
    <string name="dup_msg_no_tag">No duplicate at the reader – wrong item?</string>
    <string name="dup_msg_multiple">Several duplicates detected – isolate the item</string>
    <string name="dup_msg_capture_none">No tag detected – hold the item directly to the reader</string>
    <string name="dup_msg_capture_multiple">Several tags detected – hold a single item to the reader</string>
    <string name="dup_msg_invalid_target">Enter a 24-character EPC (SGTIN-96) or an SGTIN like 4065546.092768.12884901894</string>
    <string name="dup_msg_reader_not_connected">Reader not connected</string>
    <string name="dup_msg_reader_error">Reader error: %1$s</string>
    <string name="dup_msg_exported">Exported: %1$s</string>
    <string name="dup_msg_nothing_exported">No duplicates found – nothing exported</string>
    <string name="dup_msg_export_failed">Export failed: %1$s</string>
```

- [ ] **Step 2: Deutsch**

In `app/src/main/res/values-de/strings.xml` vor `</resources>` einfügen:

```xml
    <string name="find_duplicates">Duplikate finden</string>
    <string name="dup_target_hint">EPC oder SGTIN</string>
    <string name="dup_sgtin_filter">Filterwert (SGTIN)</string>
    <string name="dup_sgtin_filter_hint">Meist 1 (Verkaufsartikel). Nur bei eingetippter SGTIN relevant.</string>
    <string name="dup_read_with_reader">Mit Pistole anlesen</string>
    <string name="dup_hint_capture">Oder Trigger drücken, Artikel direkt an der Pistole</string>
    <string name="dup_store_optional">Filiale (optional)</string>
    <string name="dup_start_search">Suche starten</string>
    <string name="dup_target_sgtin">Gesuchte SGTIN</string>
    <string name="dup_epc">EPC</string>
    <string name="dup_store">Filiale</string>
    <string name="dup_proximity">Nähe</string>
    <string name="dup_hint_hold_trigger">Trigger gedrückt halten und Pistole schwenken</string>
    <string name="dup_hint_continuous">Dauersuche aktiv – Pistole schwenken</string>
    <string name="dup_continuous">Dauersuche</string>
    <string name="dup_continuous_sub">Suche ohne Trigger</string>
    <string name="dup_found_count">Gefunden (eindeutige TIDs)</string>
    <string name="dup_last">Zuletzt</string>
    <string name="dup_confirm_find">Fund bestätigen</string>
    <string name="dup_end_search">Suche beenden</string>
    <string name="dup_msg_saved">Fund #%1$d gespeichert – Artikel entfernen</string>
    <string name="dup_msg_already_found">Bereits gefunden (#%1$d)</string>
    <string name="dup_msg_no_tag">Kein Duplikat am Reader – falscher Artikel?</string>
    <string name="dup_msg_multiple">Mehrere Duplikate erkannt – Artikel isolieren</string>
    <string name="dup_msg_capture_none">Kein Tag erkannt – Artikel direkt an die Pistole halten</string>
    <string name="dup_msg_capture_multiple">Mehrere Tags erkannt – nur einen Artikel an die Pistole halten</string>
    <string name="dup_msg_invalid_target">24-stelligen EPC (SGTIN-96) oder SGTIN wie 4065546.092768.12884901894 eingeben</string>
    <string name="dup_msg_reader_not_connected">Reader nicht verbunden</string>
    <string name="dup_msg_reader_error">Reader-Fehler: %1$s</string>
    <string name="dup_msg_exported">Exportiert: %1$s</string>
    <string name="dup_msg_nothing_exported">Keine Duplikate gefunden – nichts exportiert</string>
    <string name="dup_msg_export_failed">Export fehlgeschlagen: %1$s</string>
```

- [ ] **Step 3: Polnisch**

In `app/src/main/res/values-pl/strings.xml` vor `</resources>` einfügen:

```xml
    <string name="find_duplicates">Znajdź duplikaty</string>
    <string name="dup_target_hint">EPC lub SGTIN</string>
    <string name="dup_sgtin_filter">Wartość filtra (SGTIN)</string>
    <string name="dup_sgtin_filter_hint">Zwykle 1 (artykuł detaliczny). Dotyczy tylko wpisanego SGTIN.</string>
    <string name="dup_read_with_reader">Odczytaj czytnikiem</string>
    <string name="dup_hint_capture">Lub naciśnij spust, trzymając artykuł przy czytniku</string>
    <string name="dup_store_optional">Sklep (opcjonalnie)</string>
    <string name="dup_start_search">Rozpocznij wyszukiwanie</string>
    <string name="dup_target_sgtin">Szukany SGTIN</string>
    <string name="dup_epc">EPC</string>
    <string name="dup_store">Sklep</string>
    <string name="dup_proximity">bliskość</string>
    <string name="dup_hint_hold_trigger">Przytrzymaj spust i przesuwaj czytnikiem</string>
    <string name="dup_hint_continuous">Wyszukiwanie ciągłe aktywne – przesuwaj czytnikiem</string>
    <string name="dup_continuous">Wyszukiwanie ciągłe</string>
    <string name="dup_continuous_sub">Wyszukiwanie bez spustu</string>
    <string name="dup_found_count">Znalezione (unikalne TID)</string>
    <string name="dup_last">Ostatni</string>
    <string name="dup_confirm_find">Potwierdź znalezisko</string>
    <string name="dup_end_search">Zakończ wyszukiwanie</string>
    <string name="dup_msg_saved">Znalezisko #%1$d zapisane – usuń artykuł</string>
    <string name="dup_msg_already_found">Już znaleziono (#%1$d)</string>
    <string name="dup_msg_no_tag">Brak duplikatu przy czytniku – zły artykuł?</string>
    <string name="dup_msg_multiple">Wykryto kilka duplikatów – odizoluj artykuł</string>
    <string name="dup_msg_capture_none">Nie wykryto tagu – przyłóż artykuł bezpośrednio do czytnika</string>
    <string name="dup_msg_capture_multiple">Wykryto kilka tagów – przyłóż tylko jeden artykuł</string>
    <string name="dup_msg_invalid_target">Wprowadź 24-znakowy EPC (SGTIN-96) lub SGTIN, np. 4065546.092768.12884901894</string>
    <string name="dup_msg_reader_not_connected">Czytnik niepołączony</string>
    <string name="dup_msg_reader_error">Błąd czytnika: %1$s</string>
    <string name="dup_msg_exported">Wyeksportowano: %1$s</string>
    <string name="dup_msg_nothing_exported">Nie znaleziono duplikatów – nic nie wyeksportowano</string>
    <string name="dup_msg_export_failed">Eksport nieudany: %1$s</string>
```

- [ ] **Step 4: Build prüfen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-pl/strings.xml
git commit -m "feat(duplicates): add EN/DE/PL strings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: UI – Tacho und Bildschirm

**Files:**
- Create: `app/src/main/java/de/ca/qcc/feature/duplicates/ProximityGauge.kt`
- Create: `app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchScreen.kt`

Kein Unit-Test (Compose-UI ohne UI-Test-Setup im Projekt); Verifikation über Build und Hardware-Checkliste in Task 11.

- [ ] **Step 1: Tacho**

`app/src/main/java/de/ca/qcc/feature/duplicates/ProximityGauge.kt`:

```kotlin
package de.ca.qcc.feature.duplicates

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ca.qcc.R
import kotlin.math.min

private val GaugeTrack = Color(0xFFE6E6E6)
private val GaugeGradient = listOf(Color(0xFFE53935), Color(0xFFFBC02D), Color(0xFF2E7D32))

@Composable
fun ProximityGauge(proximity: Int, active: Boolean, modifier: Modifier = Modifier) {
    val sweep by animateFloatAsState(targetValue = proximity / 100f * 180f, label = "sweep")

    // Arc "breathes" in the beep rhythm
    val periodMs = (beepIntervalMs(proximity) ?: 1000L).toInt().coerceAtLeast(160)
    val breathing = active && proximity > 0
    val alpha by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = if (breathing) 0.55f else 1f,
        animationSpec = infiniteRepeatable(tween(periodMs / 2), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(modifier.fillMaxWidth().aspectRatio(2f), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val diameter = min(size.width, size.height * 2) - stroke
            val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = GaugeTrack,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.horizontalGradient(GaugeGradient),
                    startAngle = 180f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = alpha,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$proximity", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Text("% " + stringResource(R.string.dup_proximity), color = Color(0xFF555555))
        }
    }
}
```

- [ ] **Step 2: Bildschirm**

`app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchScreen.kt`:

```kotlin
package de.ca.qcc.feature.duplicates

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.ca.qcc.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateSearchScreen(
    state: DuplicateUiState,
    onInputChange: (String) -> Unit,
    onSgtinFilterChange: (Int) -> Unit,
    onApplyInput: () -> Unit,
    onCapture: () -> Unit,
    onStoreChange: (String) -> Unit,
    onStartSearch: () -> Unit,
    onContinuousChange: (Boolean) -> Unit,
    onConfirmFind: () -> Unit,
    onEndSearch: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val blackButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Black,
        contentColor = Color.White
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.find_duplicates),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.message?.let { message -> item { MessageCard(message) } }

            if (state.phase == DuplicatePhase.TARGET) {
                item {
                    WhiteCard {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = onInputChange,
                            label = { Text(stringResource(R.string.dup_target_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onApplyInput() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Filter value only matters for a typed SGTIN (gcp.itemRef.serial)
                        if (state.input.contains('.')) {
                            Spacer(Modifier.height(8.dp))
                            SgtinFilterSelector(
                                selected = state.sgtinFilter,
                                enabled = !state.busy,
                                onSelect = onSgtinFilterChange
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onApplyInput,
                            enabled = state.input.isNotBlank(),
                            colors = blackButtonColors,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.apply)) }
                        Button(
                            onClick = onCapture,
                            enabled = state.readerConnected && !state.busy,
                            colors = blackButtonColors,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.dup_read_with_reader)) }
                        Text(
                            stringResource(R.string.dup_hint_capture),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black
                        )
                    }
                }
                state.target?.let { target ->
                    item {
                        WhiteCard {
                            LabeledValue(stringResource(R.string.dup_target_sgtin), target.sgtin)
                            LabeledValue(stringResource(R.string.dup_epc), target.epc, monospace = true)
                        }
                    }
                }
                item {
                    WhiteCard {
                        OutlinedTextField(
                            value = state.store,
                            onValueChange = onStoreChange,
                            label = { Text(stringResource(R.string.dup_store_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Button(
                        onClick = onStartSearch,
                        enabled = state.target != null && state.readerConnected && !state.busy,
                        colors = blackButtonColors,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dup_start_search)) }
                }
            } else {
                val target = state.target
                if (target != null) {
                    item {
                        WhiteCard {
                            LabeledValue(stringResource(R.string.dup_target_sgtin), target.sgtin)
                            LabeledValue(stringResource(R.string.dup_epc), target.epc, monospace = true)
                            if (state.store.isNotBlank()) {
                                LabeledValue(stringResource(R.string.dup_store), state.store)
                            }
                        }
                    }
                }
                item {
                    WhiteCard {
                        ProximityGauge(proximity = state.proximity, active = state.locating)
                        Text(
                            stringResource(
                                if (state.continuous) R.string.dup_hint_continuous else R.string.dup_hint_hold_trigger
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF555555),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                item {
                    WhiteCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dup_continuous), color = Color.Black, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.dup_continuous_sub),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF555555)
                                )
                            }
                            Switch(
                                checked = state.continuous,
                                onCheckedChange = onContinuousChange,
                                enabled = !state.busy,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color.Black
                                )
                            )
                        }
                    }
                }
                item {
                    WhiteCard {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dup_found_count), style = MaterialTheme.typography.labelSmall, color = Color.Black)
                                Text("${state.foundCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            state.lastTid?.let { tid ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.dup_last), style = MaterialTheme.typography.labelSmall, color = Color.Black)
                                    Text(tid.shortTid(), fontFamily = FontFamily.Monospace, color = Color.Black)
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = onConfirmFind,
                        enabled = !state.busy,
                        colors = blackButtonColors,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dup_confirm_find)) }
                }
                item {
                    Button(
                        onClick = onEndSearch,
                        enabled = !state.busy,
                        colors = blackButtonColors,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dup_end_search)) }
                }
            }
        }
    }
}

@Composable
private fun WhiteCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SgtinFilterSelector(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.dup_sgtin_filter), style = MaterialTheme.typography.labelSmall, color = Color.Black)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        (0..7).forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text("$value", fontWeight = FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White,
                    labelColor = Color.Black,
                    selectedContainerColor = Color.Black,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
    Text(
        stringResource(R.string.dup_sgtin_filter_hint),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF555555)
    )
}

@Composable
private fun LabeledValue(label: String, value: String, monospace: Boolean = false) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Black)
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = Color.Black
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MessageCard(message: DuplicateMessage) {
    val isError = message !is DuplicateMessage.Saved &&
        message !is DuplicateMessage.Exported &&
        message !is DuplicateMessage.NothingExported
    WhiteCard {
        Text(
            message.text(),
            color = if (isError) Color.Red else Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DuplicateMessage.text(): String = when (this) {
    is DuplicateMessage.Saved -> stringResource(R.string.dup_msg_saved, findNo)
    is DuplicateMessage.AlreadyFound -> stringResource(R.string.dup_msg_already_found, findNo)
    DuplicateMessage.NoTagAtReader -> stringResource(R.string.dup_msg_no_tag)
    DuplicateMessage.MultipleTags -> stringResource(R.string.dup_msg_multiple)
    DuplicateMessage.CaptureNoTag -> stringResource(R.string.dup_msg_capture_none)
    DuplicateMessage.CaptureMultipleTags -> stringResource(R.string.dup_msg_capture_multiple)
    DuplicateMessage.InvalidTarget -> stringResource(R.string.dup_msg_invalid_target)
    DuplicateMessage.ReaderNotConnected -> stringResource(R.string.dup_msg_reader_not_connected)
    is DuplicateMessage.ReaderError -> stringResource(R.string.dup_msg_reader_error, detail)
    is DuplicateMessage.Exported -> stringResource(R.string.dup_msg_exported, fileName)
    DuplicateMessage.NothingExported -> stringResource(R.string.dup_msg_nothing_exported)
    is DuplicateMessage.ExportFailed -> stringResource(R.string.dup_msg_export_failed, detail)
}

private fun String.shortTid(): String = if (length > 10) "${take(4)}…${takeLast(4)}" else this
```

- [ ] **Step 3: Build prüfen**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/ca/qcc/feature/duplicates/ProximityGauge.kt app/src/main/java/de/ca/qcc/feature/duplicates/DuplicateSearchScreen.kt
git commit -m "feat(duplicates): add duplicate search screen and proximity gauge

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Navigation und Drawer

**Files:**
- Modify: `app/src/main/java/de/ca/qcc/navigation/Nav.kt`
- Modify: `app/src/main/java/de/ca/qcc/app/MainActivity.kt`

- [ ] **Step 1: Route anlegen**

In `app/src/main/java/de/ca/qcc/navigation/Nav.kt` nach `data object Dashboard : Screen("dashboard")`:

```kotlin
    data object Duplicates : Screen("duplicates")
```

- [ ] **Step 2: Imports in `MainActivity` ergänzen** (nur fehlende hinzufügen)

```kotlin
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.ca.qcc.feature.duplicates.DuplicateSearchScreen
import de.ca.qcc.feature.duplicates.DuplicateSearchViewModel
```

- [ ] **Step 3: Drawer-Eintrag**

In `AppNav()` direkt nach dem `DrawerItem` für `R.string.dashboard` einfügen:

```kotlin
                    DrawerItem(
                        label = stringResource(R.string.find_duplicates),
                        icon = Icons.Default.Search,
                        selected = currentRoute == Screen.Duplicates.route,
                        onClick = {
                            navController.navigate(Screen.Duplicates.route)
                            scope.launch { drawerState.close() }
                        }
                    )
```

- [ ] **Step 4: Destination**

Im `NavHost` nach `composable(Screen.Dashboard.route) { … }` einfügen:

```kotlin
            composable(Screen.Duplicates.route) {
                // Scoped to this destination, unlike the activity-wide QCC view models
                val duplicateViewModel: DuplicateSearchViewModel = hiltViewModel()
                val state by duplicateViewModel.uiState.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current

                // Stop locating and hand the trigger back to QCC when leaving or backgrounding
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> duplicateViewModel.onScreenStarted()
                            Lifecycle.Event.ON_STOP -> duplicateViewModel.onScreenStopped()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        duplicateViewModel.onScreenStopped()
                    }
                }

                DuplicateSearchScreen(
                    state = state,
                    onInputChange = duplicateViewModel::onInputChange,
                    onSgtinFilterChange = duplicateViewModel::onSgtinFilterChange,
                    onApplyInput = duplicateViewModel::applyInput,
                    onCapture = duplicateViewModel::captureFromReader,
                    onStoreChange = duplicateViewModel::onStoreChange,
                    onStartSearch = duplicateViewModel::startSearch,
                    onContinuousChange = duplicateViewModel::setContinuous,
                    onConfirmFind = duplicateViewModel::confirmFind,
                    onEndSearch = duplicateViewModel::endSearch,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
```

- [ ] **Step 5: Build und alle Tests**

Run: `./gradlew assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/de/ca/qcc/navigation/Nav.kt app/src/main/java/de/ca/qcc/app/MainActivity.kt
git commit -m "feat(duplicates): add find duplicates to navigation drawer

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Gesamtverifikation und Hardware-Abnahme

**Files:** keine Codeänderung erwartet.

- [ ] **Step 1: Tests, Lint, Build**

Run:
```bash
./gradlew clean :app:testDebugUnitTest lintDebug assembleDebug --console=plain 2>&1 | tail -20
grep -A3 'id="MissingTranslation"' app/build/reports/lint-results-debug.xml | grep -c 'dup_\|find_duplicates'
```
Expected: Alle Tests grün; Lint darf weiterhin die 8 **bestehenden** `MissingTranslation`-Fehler melden, aber `grep -c` liefert `0` (keine neuen String-Keys betroffen).

- [ ] **Step 2: Auf CT37 deployen**

```bash
~/Library/Android/sdk/platform-tools/adb -s 24255X0032 install -r app/build/outputs/apk/debug/app-debug.apk && ~/Library/Android/sdk/platform-tools/adb -s 24255X0032 shell monkey -p de.ca.qcc -c android.intent.category.LAUNCHER 1
```
Expected: `Success`, App startet (DB-Migration 6 → 7 läuft beim Start auf dem echten Gerät).

- [ ] **Step 3: Hardware-Checkliste mit dem Anwender**

| # | Prüfung | Erwartung |
|---|---|---|
| 1 | App-Sprache Englisch, Drawer | Eintrag „Find duplicates“ |
| 2 | Artikel an Pistole, Trigger drücken | EPC im Feld, SGTIN/EPC-Karte sichtbar |
| 3 | SGTIN manuell tippen | Filterauswahl 0–7 erscheint unter dem Feld, 1 ist vorausgewählt; bei EPC-Eingabe (ohne Punkt) keine Filterauswahl |
| 3a | SGTIN `4065546.000772.12884901905` (echtes Duplikat, EPC `3034F8242800C10300000011`) tippen, Filter auf 0 stellen, „Apply“ | Karte zeigt EPC `3014F8242800C10300000011` (würde den echten Tag nicht finden) |
| 3b | Filter zurück auf 1 stellen | SGTIN/EPC-Karte verschwindet sofort; nach „Apply“ zeigt sie EPC `3034F8242800C10300000011` – mit diesem Ziel weiter in Schritt 4, in Schritt 5 führt die Ortung zum echten Tag |
| 4 | Filiale eintragen, „Start search“ | Suchphase, Filiale in Karte |
| 5 | Trigger halten, auf Duplikat zu bewegen | Tacho steigt, Piepen schneller, Vibration |
| 6 | Trigger loslassen | Tacho 0, still |
| 7 | „Continuous search“ an | Suche ohne Trigger |
| 8 | Duplikat herausnehmen, an Pistole, „Confirm find“ | „Find #1 saved – remove the item“, Suche läuft weiter |
| 9 | Denselben Artikel erneut bestätigen | „Already found (#1)“ |
| 10 | Nicht-Duplikat an Pistole, bestätigen | „No duplicate at the reader – wrong item?“ |
| 11 | Zwei Duplikate zusammen an die Pistole, bestätigen (im Spike Schritt g nicht getestet, hier nachholen) | „Several duplicates detected – isolate the item“ |
| 11a | Anlesen und „Confirm find“ mehrfach direkt an der Pistole (5 % Leistung) | zuverlässig gelesen (mind. 5 von 5); Artikel ca. 30 cm entfernt wird nicht gelesen |
| 12 | App in Hintergrund und zurück | Suche gestoppt, Schalter aus, Suche offen |
| 13 | App beenden (Recents wegwischen), neu öffnen, „Find duplicates“ | offene Suche wird fortgesetzt |
| 14 | „End search“ | „Exported: duplicates_….csv“; Datei in Documents mit korrekten Spalten |
| 15 | Zum Dashboard, QCC-Zyklus mit Zebra-Trigger | QCC-Verhalten unverändert |
| 16 | Sprache Deutsch/Polnisch | alle neuen Texte übersetzt |
| 17 | Trigger kurz antippen (Drücken+sofort Loslassen) und schnell erneut drücken | Ortung startet/stoppt sauber, Tacho geht auf 0 |
| 18 | Während der Suche im Menü zu „Reader“ wechseln, dann zum Dashboard | QCC-Zyklus liefert wieder Tags (kein hängender Locate) |
| 19 | Zurück (Systemtaste) während Ortung läuft | danach QCC-Scan funktioniert |

- [ ] **Step 4: Abweichungen**

Jede fehlgeschlagene Prüfung → mit `superpowers:systematic-debugging` Ursache finden, Fix mit Test (wo möglich), eigener Commit, Checkliste wiederholen.

---

### Task 12: Dokumentation nachziehen

Die Spec wurde bereits nach dem Hardware-Spike (2026-09-30) an die Umsetzung angeglichen: SGTIN-Filterwert 1 mit Auswahl 0–7 und exakter Suche, Nahbereichs-Lesen mit 5 % der Maximalleistung, TID 96 Bit, Trigger-Weiterleitung im Gateway (`ScanViewModel` unverändert), sofortiges Anlesen per Trigger in Phase 1, keine eigene Spalte für Filter-Maskierung in `duplicate_searches`, Spike-Ergebnis bei R1. Hier bleibt nur der Abschluss.

**Files:**
- Modify: `docs/superpowers/specs/2026-09-29-duplicate-finder-design.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Spec-Status setzen**

In der Spec die Zeile `- Status: Freigegeben, angepasst nach Hardware-Spike (2026-09-30)` ersetzen durch `- Status: Umgesetzt`. Falls die Hardware-Abnahme (Task 11) weitere Abweichungen ergeben hat, diese in den betroffenen Abschnitten nachtragen.

- [ ] **Step 2: CLAUDE.md aktualisieren**

In `CLAUDE.md`:
1. Abschnitt „Database“: `schema version 6` → `schema version 7`; Satz „Two tables: …“ ersetzen durch: `Four tables: scans (every comparison result), mismatches (mismatch-only subset), duplicate_searches and duplicate_finds (duplicate finder; TID is globally unique).`
2. Abschnitt „Key architectural decisions“, Absatz „Zebra gateway is single-tag mode by design.“ ergänzen um: `The gateway also has trigger modes (QCC / CAPTURE / LOCATE, see TriggerMode). Trigger routing happens in the gateway: only QCC runs the single-tag scan and emits observeTriggerEvents(), so ScanViewModel is unchanged. The duplicate finder uses CAPTURE/LOCATE via observeHandheldTrigger(), TagLocationing (always exact, full mask – the RFD8500 only accepts masks that are contiguous from the MSB) and nearby reads at 5 % of max power (EPC capture, 96-bit TID).`
3. Abschnitt „Key architectural decisions“, Absatz „GS1 parsing is fully in Gs1ParserService.“, Liste ergänzen um: `- Locate target from hex EPC or typed SGTIN (parseLocateTarget); a typed SGTIN is encoded as SGTIN-96 with an explicit filter value (default 1, selectable 0–7 in the UI)`.
4. Unter „Navigation“ ergänzen: `The duplicate finder (feature/duplicates, route "duplicates") scopes its ViewModel to its destination and resets the trigger mode to QCC on ON_STOP. In its target phase the trigger is in CAPTURE mode and reads a tag immediately.`
5. Im Abschnitt „Build Commands“ unter den Test-Befehlen ergänzen: `Unit tests use Robolectric + in-memory Room (see app/src/test/.../testutil).`

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-09-29-duplicate-finder-design.md CLAUDE.md
git commit -m "docs: update spec status and CLAUDE.md for duplicate finder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
