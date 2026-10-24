package org.runnerup.relven.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import org.runnerup.common.tracker.TrackerState
import org.runnerup.relven.ui.WearConnectionProvider.PreparationStatus
import org.runnerup.tracker.Tracker
import org.runnerup.view.RunActivity

internal class WorkoutPreparationActivity : ComponentActivity() {
  private lateinit var state: WorkoutPreparationStateHolder
  private var tracker: Tracker? = null
  private var bound = false
  private var trackerReady by mutableStateOf(false)
  private var transferStarted = false

  private val runActivityLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        setResult(result.resultCode, result.data)
        finish()
      }

  private val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          tracker = (service as? Tracker.LocalBinder)?.service
          tracker?.let { activeTracker ->
            if (WorkoutPreparationTrackerPolicy.shouldConnect(activeTracker.state)) {
              activeTracker.connect()
            }
          }
          updateTrackerState()
          tracker?.registerTrackerStateListener(trackerStateListener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          tracker?.unregisterTrackerStateListener(trackerStateListener)
          tracker = null
          trackerReady = false
        }
      }

  private val trackerStateListener =
      object : org.runnerup.common.util.ValueModel.ChangeListener<TrackerState> {
        override fun onValueChanged(
            instance: org.runnerup.common.util.ValueModel<TrackerState>,
            oldValue: TrackerState,
            newValue: TrackerState
        ) {
          runOnUiThread(::updateTrackerState)
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
    val workoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME)
    val upcomingStep = intent.getStringExtra(EXTRA_UPCOMING_STEP)
    if (sessionId.isNullOrBlank() || workoutName.isNullOrBlank() || upcomingStep.isNullOrBlank()) {
      finish()
      return
    }
    state = WorkoutPreparationStateHolder(sessionId, workoutName, upcomingStep)
    setContent {
      RelvenTheme {
        WorkoutPreparationScreen(
            state = state.value,
            trackerReady = trackerReady,
            onStart = ::startWorkout,
            onCancel = ::cancelPreparation)
      }
    }
    bound = bindService(Intent(this, Tracker::class.java), connection, Context.BIND_AUTO_CREATE)
    prepareWatch()
  }

  private fun prepareWatch() {
    if (transferStarted) return
    transferStarted = true
    val sessionId = state.value.sessionId
    val workoutJson = intent.getStringExtra(EXTRA_WORKOUT_JSON).orEmpty()
    WearConnectionProvider.prepareWorkout(
        this,
        sessionId,
        state.value.workoutName,
        state.value.upcomingStep,
        workoutJson) { result ->
          if (isFinishing || isDestroyed) return@prepareWorkout
          val ready = result.status == PreparationStatus.PREPARED
          val status =
              when (result.status) {
                PreparationStatus.PREPARED -> "Klockan redo"
                PreparationStatus.NO_WATCH -> "Ingen klocka ansluten · fortsätt på mobilen"
                PreparationStatus.TIMEOUT -> "Klockan svarar inte · fortsätt på mobilen"
                PreparationStatus.REJECTED -> "Klockan kunde inte läsa passet · fortsätt på mobilen"
                PreparationStatus.PROTOCOL_MISMATCH ->
                  "Uppdatera Relven på klockan · fortsätt på mobilen"
                PreparationStatus.UNSUPPORTED -> "Klockstöd saknas · fortsätt på mobilen"
                PreparationStatus.TRANSPORT_ERROR ->
                  "Klockan kunde inte nås · fortsätt på mobilen"
              }
          state.onWatchPreparationFinished(ready, status)
        }
  }

  private fun updateTrackerState() {
    trackerReady = tracker?.state == TrackerState.CONNECTED
  }

  private fun startWorkout() {
    val activeTracker = tracker ?: return
    if (activeTracker.state != TrackerState.CONNECTED || !state.requestStart()) return
    try {
      activeTracker.start()
    } catch (error: RuntimeException) {
      state.onStartFailed()
      return
    }
    runActivityLauncher.launch(Intent(this, RunActivity::class.java))
  }

  private fun cancelPreparation() {
    WearConnectionProvider.cancelWorkoutPreparation(this, state.value.sessionId)
    finish()
  }

  override fun onDestroy() {
    tracker?.unregisterTrackerStateListener(trackerStateListener)
    if (bound) {
      unbindService(connection)
      bound = false
    }
    super.onDestroy()
  }

  companion object {
    private const val EXTRA_SESSION_ID = "org.runnerup.relven.preparation.session_id"
    private const val EXTRA_WORKOUT_NAME = "org.runnerup.relven.preparation.workout_name"
    private const val EXTRA_UPCOMING_STEP = "org.runnerup.relven.preparation.upcoming_step"
    private const val EXTRA_WORKOUT_JSON = "org.runnerup.relven.preparation.workout_json"

    @JvmStatic
    fun intent(
        context: Context,
        sessionId: String,
        workoutName: String,
        upcomingStep: String,
        workoutJson: String
    ): Intent =
        Intent(context, WorkoutPreparationActivity::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_WORKOUT_NAME, workoutName)
            .putExtra(EXTRA_UPCOMING_STEP, upcomingStep)
            .putExtra(EXTRA_WORKOUT_JSON, workoutJson)
  }
}
