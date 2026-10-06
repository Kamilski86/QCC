package de.ca.qcc.device

import de.ca.qcc.device.honeywell.HoneywellIh45Gateway
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.SwitchingRfidReaderGateway
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.domain.model.ReaderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingRfidReaderGatewayTest {

    private class FakeReader(var names: List<String>) : RfidReaderGateway {
        val status = MutableStateFlow(ReaderStatus(isConnected = false))
        val tags = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val calls = mutableListOf<String>()
        var mode: TriggerMode? = null

        override fun observeStatus(): Flow<ReaderStatus> = status
        override fun observeTagScans(): Flow<String> = tags
        override suspend fun discoverReaders(): List<String> = names
        override suspend fun connect(readerId: String): Result<Unit> {
            calls += "connect:$readerId"
            status.value = ReaderStatus(isConnected = true)
            return Result.success(Unit)
        }
        override suspend fun disconnect() {
            calls += "disconnect"
            status.value = ReaderStatus(isConnected = false)
        }
        override suspend fun triggerInventory() { calls += "inventory" }
        override suspend fun setPower(level: Int): Result<Unit> { calls += "power:$level"; return Result.success(Unit) }
        override fun setTriggerMode(mode: TriggerMode) { this.mode = mode }
    }

    private class Fixture(scope: TestScope) {
        val zebra = FakeReader(listOf("RFD8500"))
        val ih45 = FakeReader(emptyList())
        val usb = MutableStateFlow(false)
        val gateway = SwitchingRfidReaderGateway(zebra, ih45, usb, CoroutineScope(StandardTestDispatcher(scope.testScheduler)))

        // Let the gateway start collecting the USB state before a test changes it
        init { scope.testScheduler.advanceUntilIdle() }
    }

    @Test
    fun withoutIh45TheZebraIsUsed() = runTest {
        val f = Fixture(this)
        val readers = f.gateway.discoverReaders()
        f.gateway.connect(readers.first())
        assertEquals(listOf("RFD8500"), readers)
        assertEquals(listOf("connect:RFD8500"), f.zebra.calls.filter { it.startsWith("connect") })
        assertTrue(f.ih45.calls.none { it.startsWith("connect") })
    }

    @Test
    fun ih45ListedFirstAndZebraDisconnectedBeforeItConnects() = runTest {
        val f = Fixture(this)
        f.ih45.names = listOf("Honeywell-IH45")
        f.gateway.discoverReaders().let { f.gateway.connect("RFD8500") } // Zebra connected first
        val readers = f.gateway.discoverReaders()
        f.gateway.connect(readers.first())

        assertEquals(listOf("Honeywell-IH45", "RFD8500"), readers)
        assertEquals(listOf("connect:RFD8500", "disconnect"), f.zebra.calls)
        assertEquals(listOf("disconnect", "connect:Honeywell-IH45"), f.ih45.calls) // other reader is always released
    }

    @Test
    fun flowsAndCallsFollowTheActiveReader() = runTest {
        val f = Fixture(this)
        f.ih45.names = listOf("Honeywell-IH45")
        f.gateway.connect(f.gateway.discoverReaders().first())
        f.gateway.triggerInventory()
        f.gateway.setPower(7)

        assertEquals(listOf("connect:Honeywell-IH45", "inventory", "power:7"), f.ih45.calls)
        assertTrue(f.zebra.calls.none { it == "inventory" })
        assertTrue(f.gateway.observeStatus().first().isConnected)
    }

    @Test
    fun pluggingTheIh45InSwitchesFromZebraAndKeepsThePower() = runTest {
        val f = Fixture(this)
        f.gateway.connect(f.gateway.discoverReaders().first())
        f.gateway.setPower(9)

        f.ih45.names = listOf("Honeywell-IH45")
        f.usb.value = true
        advanceUntilIdle()

        assertTrue(f.zebra.calls.contains("disconnect"))
        assertEquals(listOf("disconnect", "connect:Honeywell-IH45", "power:9"), f.ih45.calls)
    }

    @Test
    fun unpluggingTheIh45FallsBackToTheZebra() = runTest {
        val f = Fixture(this)
        f.ih45.names = listOf("Honeywell-IH45")
        f.usb.value = true
        advanceUntilIdle() // the hot-plug connects the IH45

        f.ih45.names = emptyList()
        f.usb.value = false
        advanceUntilIdle()

        assertTrue(f.zebra.calls.contains("connect:RFD8500"))
        assertTrue(f.ih45.calls.contains("disconnect"))
    }

    @Test
    fun triggerModeReachesBothReaders() = runTest {
        val f = Fixture(this)
        f.gateway.setTriggerMode(TriggerMode.CAPTURE)
        assertEquals(TriggerMode.CAPTURE, f.zebra.mode)
        assertEquals(TriggerMode.CAPTURE, f.ih45.mode)
    }

    @Test
    fun alreadyConnectedReaderIsNotReconnected() = runTest {
        val f = Fixture(this)
        f.gateway.connect("RFD8500")
        f.gateway.connect("RFD8500")
        assertEquals(1, f.zebra.calls.count { it.startsWith("connect") })
        assertFalse(f.ih45.calls.any { it.startsWith("connect") })
    }
}
