package de.ca.qcc.feature.duplicates

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import de.ca.qcc.R
import de.ca.qcc.domain.model.LocateTarget
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 360x600dp: CT37-sized display minus status and navigation bar
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h600dp-xhdpi")
class DuplicateSearchScreenLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val searchState = DuplicateUiState(
        phase = DuplicatePhase.SEARCH,
        target = LocateTarget(epc = "3034F824285A980300000006", sgtin = "4065546.092768.12884901894"),
        store = "0815",
        searchId = 1,
        foundCount = 12,
        lastTid = "E2801191A503006B7A41A41F",
        readerConnected = true
    )

    private fun show(state: DuplicateUiState) = rule.setContent {
        MaterialTheme {
            DuplicateSearchScreen(
                state = state,
                onInputChange = {}, onSgtinFilterChange = {}, onApplyInput = {}, onStoreChange = {},
                onStartSearch = {}, onContinuousChange = {}, onConfirmFind = {}, onEndSearch = {},
                onMessageDismissed = {}, onOpenDrawer = {}
            )
        }
    }

    @Test
    fun searchPhase_showsEverythingWithoutScrolling() {
        show(searchState)

        rule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        listOf(
            R.string.dup_target_sgtin, R.string.dup_epc, R.string.dup_tid, R.string.dup_store,
            R.string.dup_continuous, R.string.dup_found_count, R.string.dup_confirm_find, R.string.dup_end_search
        ).forEach { rule.onNodeWithText(string(it)).assertIsDisplayed() }
    }

    @Test
    fun searchPhase_gaugeKeepsUsableSize() {
        show(searchState)

        val height = rule.onNodeWithTag("gauge").getUnclippedBoundsInRoot().height
        assertTrue("gauge only $height tall", height >= 80.dp)
    }

    @Test
    fun searchPhase_showsTidBeforeFirstFindAsDash() {
        show(searchState.copy(lastTid = null))

        rule.onNodeWithText(string(R.string.dup_tid)).assertIsDisplayed()
        rule.onNodeWithText("-").assertIsDisplayed()
    }
}
