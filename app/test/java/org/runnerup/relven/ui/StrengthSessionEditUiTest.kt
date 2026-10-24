package org.runnerup.relven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.workout.RelvenStrengthWorkoutParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StrengthSessionEditUiTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun savedSetCanBeCorrectedWithoutLosingItsOptionalDetails() {
    val workout =
        RelvenStrengthWorkoutParser.Workout(
            "workout",
            "Strength",
            "strength",
            listOf(
                RelvenStrengthWorkoutParser.Exercise(
                    "push-up-step",
                    "push-up",
                    1,
                    1,
                    "Push-up",
                    "Controlled repetitions",
                    1,
                    1,
                    20,
                    0,
                    null)),
            "{}")
    val finished =
        StrengthSession.start("plan", "entry", workout)
            .completeResult(
                StrengthSession.RepetitionResultInput(repetitions = 10, rpe = 7.0, painRating = 2, notes = "Steady"))
    var edited: StrengthSession.SetResult? = null

    composeRule.setContent {
      StrengthSessionScreen(
          session = finished,
          completedAtMillis = 100,
          saveError = false,
          isSavingProgress = false,
          isSavingCompletion = false,
          onEditResult = { edited = it },
          onTick = {},
          onLogRepetitions = {},
          onSkipPendingResult = {},
          onSkipExercise = {},
          onSkipRest = {},
          onPause = {},
          onResume = {},
          onFinish = {},
          onClose = {})
    }

    composeRule.onNodeWithText("Edit").performScrollTo().performClick()
    composeRule.onNodeWithText("15").performScrollTo().performClick()
    composeRule.onNodeWithText("Save change").performScrollTo().performClick()

    composeRule.runOnIdle {
      val result = requireNotNull(edited)
      assertEquals(15, result.repetitions)
      assertEquals(7.0, result.rpe!!, 0.0)
      assertEquals(2, result.painRating)
      assertEquals("Steady", result.notes)
    }
  }

  @Test
  fun finishingIsDisabledWhileASetCorrectionIsBeingSaved() {
    val workout =
        RelvenStrengthWorkoutParser.Workout(
            "workout",
            "Strength",
            "strength",
            listOf(
                RelvenStrengthWorkoutParser.Exercise(
                    "push-up-step", "push-up", 1, 1, "Push-up", "Controlled repetitions", 1, 1, 20, 0, null)),
            "{}")
    val finished =
        StrengthSession.start("plan", "entry", workout)
            .completeResult(StrengthSession.RepetitionResultInput(repetitions = 10))

    composeRule.setContent {
      StrengthSessionScreen(
          session = finished,
          completedAtMillis = null,
          saveError = false,
          isSavingProgress = true,
          isSavingCompletion = false,
          onEditResult = {},
          onTick = {},
          onLogRepetitions = {},
          onSkipPendingResult = {},
          onSkipExercise = {},
          onSkipRest = {},
          onPause = {},
          onResume = {},
          onFinish = {},
          onClose = {})
    }

    composeRule.onNodeWithText("Save completed session").assertIsNotEnabled()
  }
}
