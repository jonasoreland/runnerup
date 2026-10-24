package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeNavigationScaffoldTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun finalWorkoutInstructionsCanScrollFullyAboveMeasuredNavigationBar() {
    composeRule.setContent {
      val currentDensity = LocalDensity.current
      CompositionLocalProvider(
          LocalDensity provides Density(currentDensity.density, fontScale = 1.6f)) {
        HomeNavigationScaffold(
            modifier = Modifier.height(420.dp),
            navigationBar = {
              Box(Modifier.fillMaxWidth().height(104.dp).testTag("home-navigation"))
            }) {
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            repeat(24) { index -> androidx.compose.material3.Text("Instruction $index") }
            androidx.compose.material3.Text("Final workout instruction")
          }
        }
      }
    }

    val finalInstruction = composeRule.onNodeWithText("Final workout instruction")
    finalInstruction.performScrollTo()
    val instructionBounds = finalInstruction.fetchSemanticsNode().boundsInRoot
    val navigationBounds = composeRule.onNodeWithTag("home-navigation").fetchSemanticsNode().boundsInRoot

    assertTrue(
        "Final instruction should end above the floating navigation bar: $instructionBounds vs $navigationBounds",
        instructionBounds.bottom <= navigationBounds.top)
  }
}
