package org.runnerup.relven.ui

import android.content.ComponentName
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import org.runnerup.common.tracker.TrackerState
import org.runnerup.relven.ui.WearConnectionProvider.PreparationStatus
import org.runnerup.relven.ui.WearConnectionProvider.HeartRateReadinessStatus
import org.runnerup.relven.ui.WearConnectionProvider.WorkoutPreparationCallback
import org.runnerup.tracker.Tracker
import org.runnerup.view.RunActivity
import org.runnerup.common.util.Constants
import org.runnerup.workout.Sport

internal class WorkoutPreparationActivity : ComponentActivity() {
  private lateinit var state: WorkoutPreparationStateHolder
  private var tracker: Tracker? = null
  private var bound = false
  private var trackerConnection = WorkoutPreparationTrackerConnectionState()
  private var trackerReady by mutableStateOf(false)
  private var gpsReadiness by
      mutableStateOf(GpsPreparationUiState(GpsPreparationStatus.NOT_REQUIRED))
  private val gpsReadinessPublisher = GpsReadinessPublisher()
  private var selectedWorkoutAvailable by mutableStateOf(true)
  private var transferStarted = false
  private var restoreSelectedWorkout = false
  private var trackerStartIssued = false
  private var sessionCommandReceiverRegistered = false
  private var finishAfterCancel = false
  private var finishAfterStartResolution = false
  private var restoredStartCommand: org.runnerup.common.wear.WorkoutSessionProtocol.Command? = null
  private val sessionCommandReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val payload = intent.getByteArrayExtra(Constants.Intents.EXTRA_WORKOUT_SESSION_COMMAND) ?: return
          val command =
              try {
                org.runnerup.common.wear.WorkoutSessionProtocol.decodeCommand(payload)
              } catch (_: java.io.IOException) {
                return
              }
          if (command.sessionId != state.value.sessionId) return
          if (Constants.Intents.WORKOUT_SESSION_COMMAND_REJECTED == intent.action) {
            if (command.actor != org.runnerup.common.wear.WorkoutSessionProtocol.Actor.PHONE &&
                command.actor != org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH) return
            handleRejectedSessionCommand(command)
            return
          }
          if (command.actor != org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH) return
          if (tracker?.state != TrackerState.CONNECTED || !selectedWorkoutAvailable) return
          val activeTracker = tracker ?: return
          if (command.action == org.runnerup.common.wear.WorkoutSessionProtocol.Action.CANCEL) {
            val applied = activeTracker.applyWearSessionCommand(command)
            if ((applied == org.runnerup.common.wear.WorkoutSessionProtocol.Result.ACCEPTED ||
                    applied == org.runnerup.common.wear.WorkoutSessionProtocol.Result.DUPLICATE) &&
                state.acceptAuthoritativeCancel(command)) {
              WearConnectionProvider.clearGpsReadiness(
                  this@WorkoutPreparationActivity, state.value.sessionId)
              val shouldFinish = finishAfterCancel
              countdownHandler.removeCallbacksAndMessages(null)
              finishAfterCancel = false
              if (shouldFinish) {
                WearConnectionProvider.cancelWorkoutPreparation(this@WorkoutPreparationActivity, state.value.sessionId)
                resetPreparationTrackerIfOwned()
                finish()
              }
            }
            return
          }
          if (!isScheduledWatchStartForSession(command, state.value.sessionId)) return
          val applied = activeTracker.applyWearSessionCommand(command)
          if (applied == org.runnerup.common.wear.WorkoutSessionProtocol.Result.ACCEPTED ||
              applied == org.runnerup.common.wear.WorkoutSessionProtocol.Result.DUPLICATE) {
            if (!state.acceptAuthoritativeStart(command)) return
            countdownHandler.removeCallbacks(watchStartRequestTimeout)
            countdownHandler.removeCallbacks(countdownTick)
            if (finishAfterStartResolution) {
              finishAfterStartResolution = false
              finishAfterCancel = true
              cancelCountdown("Avbryter passet")
            } else {
              countdownHandler.post(countdownTick)
            }
          }
        }
      }
  private val countdownHandler = Handler(Looper.getMainLooper())
  private val watchStartRequestTimeout: Runnable =
      object : Runnable {
        override fun run() {
          if (state.value.phase == WorkoutPreparationPhase.START_REQUESTED) {
            state.onStartRequestStillPending("Klockan svarar inte · försöker igen")
            state.lastCommand?.let { tracker?.requestWearSessionCommand(it) }
            countdownHandler.postDelayed(this, CANCEL_RETRY_INTERVAL_MS)
          }
        }
      }
  private val watchCancelRequestTimeout: Runnable =
      object : Runnable {
        override fun run() {
          if (state.value.phase == WorkoutPreparationPhase.CANCEL_REQUESTED) {
            state.onCancelRequestStillPending("Klockan svarar inte · väntar på bekräftelse")
            state.lastCommand?.let { tracker?.requestWearSessionCommand(it) }
            countdownHandler.postDelayed(this, CANCEL_RETRY_INTERVAL_MS)
          }
        }
      }
  private val countdownTick = object : Runnable {
    override fun run() {
      if (isFinishing || isDestroyed || state.value.phase != WorkoutPreparationPhase.COUNTDOWN) return
      val now = SystemClock.elapsedRealtime()
      state.updateCountdown(now)
      if (!state.isStartDue(now)) {
        countdownHandler.postDelayed(this, COUNTDOWN_REFRESH_MS)
        return
      }
      if (trackerStartIssued) return
      val activeTracker = tracker
      if (activeTracker == null || !selectedWorkoutAvailable) {
        cancelCountdown("Anslutningen till passmotorn försvann")
        return
      }
      trackerStartIssued = true
      try {
        if (!activeTracker.startIfConnected()) {
          trackerStartIssued = false
          cancelCountdown("Anslutningen till passmotorn försvann")
          return
        }
        if (!state.completeStart(SystemClock.elapsedRealtime())) {
          // Tracker started only after the absolute deadline; state transition is still monotonic.
          state.completeStart(state.value.plannedStartElapsedRealtimeMs)
        }
      } catch (error: RuntimeException) {
        trackerStartIssued = false
        cancelCountdown("Kunde inte starta passet")
        return
      }
      WearConnectionProvider.clearGpsReadiness(this@WorkoutPreparationActivity, state.value.sessionId)
      WearConnectionProvider.cancelWorkoutPreparation(this@WorkoutPreparationActivity, state.value.sessionId)
      runActivityLauncher.launch(Intent(this@WorkoutPreparationActivity, RunActivity::class.java))
    }
  }

  private val runActivityLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        WearConnectionProvider.cancelWorkoutPreparation(this, state.value.sessionId)
        WearConnectionProvider.clearGpsReadiness(this, state.value.sessionId)
        setResult(result.resultCode, result.data)
        finish()
      }

  private val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          tracker = (service as? Tracker.LocalBinder)?.service
          tracker?.let { activeTracker ->
            if (restoreSelectedWorkout) {
              restoreSelectedWorkout = false
              selectedWorkoutAvailable = restoreSelectedWorkoutFromIntent(activeTracker)
            }
            connectTrackerForPreparation()
            activeTracker.setGpsReadinessListener { runOnUiThread(::updateGpsReadiness) }
            if (state.value.phase != WorkoutPreparationPhase.PREPARING) {
              activeTracker.prepareWearWorkoutSession(
                  state.value.sessionId, state.value.watchReady)
              restoreCountdownIfNeeded()
            }
          }
          updateTrackerState()
          tracker?.registerTrackerStateListener(trackerStateListener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          tracker?.unregisterTrackerStateListener(trackerStateListener)
          tracker?.setGpsReadinessListener(null)
          trackerConnection.onServiceDisconnected()
          tracker = null
          trackerReady = false
          updateGpsReadiness()
        }
      }

  private val trackerStateListener =
      object : org.runnerup.common.util.ValueModel.ChangeListener<TrackerState> {
        override fun onValueChanged(
            instance: org.runnerup.common.util.ValueModel<TrackerState>,
            oldValue: TrackerState,
            newValue: TrackerState
        ) {
          runOnUiThread {
            if (WorkoutPreparationTrackerPolicy.shouldRetryAfterCleanup(newValue)) {
              trackerConnection.onTrackerReset()
              connectTrackerForPreparation()
            }
            updateTrackerState()
          }
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
    trackerConnection =
        WorkoutPreparationTrackerConnectionState(
            initiallyOwnedByPreparation =
                savedInstanceState?.getBoolean(STATE_TRACKER_CONNECTION_INITIATED, false) ?: false)
    updateGpsReadiness()
    restoredStartCommand =
        savedInstanceState?.getByteArray(STATE_ACCEPTED_START)?.let { bytes ->
          try {
            org.runnerup.common.wear.WorkoutSessionProtocol.decodeCommand(bytes)
          } catch (_: java.io.IOException) {
            null
          }
        }
    restoreSelectedWorkout = savedInstanceState != null
    selectedWorkoutAvailable = !restoreSelectedWorkout
    setContent {
      RelvenTheme {
        WorkoutPreparationScreen(
            state = state.value,
            trackerReady = trackerReady,
            gpsReadiness = gpsReadiness,
            selectedWorkoutAvailable = selectedWorkoutAvailable,
            onStart = ::startWorkout,
            onCancel = ::cancelPreparation)
      }
    }
    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() = cancelPreparation()
        })
    bound = bindService(Intent(this, Tracker::class.java), connection, Context.BIND_AUTO_CREATE)
    ContextCompat.registerReceiver(
        this,
        sessionCommandReceiver,
        IntentFilter().apply {
          addAction(Constants.Intents.WORKOUT_SESSION_COMMAND)
          addAction(Constants.Intents.WORKOUT_SESSION_COMMAND_REJECTED)
        },
        ContextCompat.RECEIVER_NOT_EXPORTED)
    sessionCommandReceiverRegistered = true
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
        workoutJson,
        intent.getIntExtra(EXTRA_WORKOUT_TYPE, Constants.WORKOUT_TYPE.BASIC) !=
            Constants.WORKOUT_TYPE.BASIC,
        object : WorkoutPreparationCallback {
          override fun onResult(result: WearConnectionProvider.WorkoutPreparationResult) {
            if (isFinishing || isDestroyed) return
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
            tracker?.prepareWearWorkoutSession(sessionId, ready)
            publishGpsReadinessIfReady()
            restoreCountdownIfNeeded()
          }

          override fun onHeartRateReadiness(status: HeartRateReadinessStatus) {
            val readiness =
                when (status) {
                  HeartRateReadinessStatus.READY -> WearHeartRateReadiness.READY
                  HeartRateReadinessStatus.SENSOR_UNAVAILABLE ->
                    WearHeartRateReadiness.SENSOR_UNAVAILABLE
                  HeartRateReadinessStatus.PERMISSION_REQUIRED ->
                    WearHeartRateReadiness.PERMISSION_REQUIRED
                  HeartRateReadinessStatus.BACKGROUND_PERMISSION_REQUIRED ->
                    WearHeartRateReadiness.BACKGROUND_PERMISSION_REQUIRED
                  HeartRateReadinessStatus.UNKNOWN -> WearHeartRateReadiness.UNKNOWN
                }
            if (!isFinishing && !isDestroyed) {
              state.onWatchHeartRateReadiness(sessionId, readiness)
            }
          }
        })
  }

  private fun updateTrackerState() {
    trackerReady = tracker?.state == TrackerState.CONNECTED && selectedWorkoutAvailable
    updateGpsReadiness()
  }

  private fun connectTrackerForPreparation() {
    val activeTracker = tracker ?: return
    if (isFinishing || isDestroyed) return
    if (!trackerConnection.shouldRequestConnection(activeTracker.state)) return
    trackerConnection.onConnectionRequested()
    activeTracker.connect()
  }

  private fun updateGpsReadiness() {
    val required =
        !Sport.isWithoutGps(
            intent.getIntExtra(EXTRA_SPORT, Constants.DB.ACTIVITY.SPORT_RUNNING))
    if (!required) {
      gpsReadiness =
          GpsPreparationPresentation.create(
              required = false,
              permissionGranted = false,
              providerAvailable = false,
              providerEnabled = false,
              updatesActive = false,
              locationReceived = false,
              accuracyMeters = null)
      publishGpsReadinessIfReady()
      return
    }
    val activeTracker = tracker
    if (activeTracker == null) {
      gpsReadiness = GpsPreparationUiState(GpsPreparationStatus.SEARCHING)
      publishGpsReadinessIfReady()
      return
    }
    val accuracy = activeTracker.gpsAccuracyMeters.takeIf { it.isFinite() && it > 0f }
    gpsReadiness =
        GpsPreparationPresentation.create(
            required = true,
            permissionGranted = activeTracker.hasGpsLocationPermission(),
            providerAvailable = activeTracker.isGpsProviderAvailable(),
            providerEnabled = activeTracker.isGpsProviderEnabled(),
            updatesActive = activeTracker.areGpsUpdatesActive(),
            locationReceived = activeTracker.hasGpsLocationFix(),
            accuracyMeters = accuracy)
    publishGpsReadinessIfReady()
  }

  private fun publishGpsReadinessIfReady() {
    if (!::state.isInitialized || !state.value.watchReady) return
    val publication = gpsReadinessPublisher.next(gpsReadiness, SystemClock.elapsedRealtime()) ?: return
    val payload =
        try {
          org.runnerup.common.wear.GpsPreparationProtocol.encode(
              state.value.sessionId,
              publication.revision,
              publication.status,
              publication.accuracyMeters)
        } catch (invalid: java.io.IOException) {
          android.util.Log.w("WorkoutPreparation", "Could not encode GPS readiness", invalid)
          return
        }
    WearConnectionProvider.publishGpsReadiness(applicationContext, payload)
  }

  private fun startWorkout() {
    val activeTracker = tracker ?: return
    if (activeTracker.state != TrackerState.CONNECTED || !selectedWorkoutAvailable) return
    activeTracker.prepareWearWorkoutSession(state.value.sessionId, state.value.watchReady)
    if (state.authority == org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH) {
      val request = state.createStartRequest(SystemClock.elapsedRealtime()) ?: return
      if (!state.markStartRequested(request)) return
      if (!activeTracker.requestWearSessionCommand(request)) {
        state.onStartRequestFailed("Klockan kunde inte nås · försök igen")
        return
      }
      countdownHandler.removeCallbacks(watchStartRequestTimeout)
      countdownHandler.postDelayed(watchStartRequestTimeout, START_REQUEST_TIMEOUT_MS)
      return
    }
    val command = state.createStartCommand(SystemClock.elapsedRealtime()) ?: return
    val result = activeTracker.applyWearSessionCommand(command)
    if (result == org.runnerup.common.wear.WorkoutSessionProtocol.Result.REJECTED) return
    if (!state.acceptAuthoritativeStart(command)) return
    beginAcceptedCountdown()
  }

  private fun restoreCountdownIfNeeded() {
    val command = restoredStartCommand ?: tracker?.lastAcceptedWatchSessionCommand ?: return
    if (!selectedWorkoutAvailable ||
        state.value.phase != WorkoutPreparationPhase.READY ||
        command.sessionId != state.value.sessionId ||
        command.action != org.runnerup.common.wear.WorkoutSessionProtocol.Action.START ||
        command.actor != state.authority) return
    val result = tracker?.applyWearSessionCommand(command)
    if (result == org.runnerup.common.wear.WorkoutSessionProtocol.Result.REJECTED || result == null) return
    if (state.acceptAuthoritativeStart(command)) beginAcceptedCountdown()
    restoredStartCommand = null
  }

  private fun beginAcceptedCountdown() {
    countdownHandler.removeCallbacks(countdownTick)
    countdownHandler.post(countdownTick)
  }

  private fun cancelPreparation() {
    if (state.value.phase == WorkoutPreparationPhase.START_REQUESTED &&
        state.authority == org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH) {
      finishAfterStartResolution = true
      state.onStartRequestStillPending("Väntar på klockan innan passet kan avbrytas")
      state.lastCommand?.let { tracker?.requestWearSessionCommand(it) }
      countdownHandler.removeCallbacks(watchStartRequestTimeout)
      countdownHandler.postDelayed(watchStartRequestTimeout, CANCEL_RETRY_INTERVAL_MS)
      return
    }
    if (state.value.phase == WorkoutPreparationPhase.COUNTDOWN &&
        state.authority == org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH) {
      finishAfterCancel = true
      cancelCountdown("Passförberedelsen avbröts")
      return
    }
    cancelCountdown("Passförberedelsen avbröts")
    WearConnectionProvider.cancelWorkoutPreparation(this, state.value.sessionId)
    WearConnectionProvider.clearGpsReadiness(this, state.value.sessionId)
    resetPreparationTrackerIfOwned()
    finish()
  }

  private fun resetPreparationTrackerIfOwned(afterRejectedStart: Boolean = false) {
    val activeTracker = tracker ?: return
    val shouldReset =
        if (afterRejectedStart) {
          WorkoutPreparationTrackerPolicy.shouldResetAfterRejectedStart(
              cancelWasRequested = true,
              connectionInitiatedByPreparation = trackerConnection.initiatedByPreparation,
              state = activeTracker.state)
        } else {
          WorkoutPreparationTrackerPolicy.shouldResetAfterCancellation(
              connectionInitiatedByPreparation = trackerConnection.initiatedByPreparation,
              state = activeTracker.state)
        }
    if (!shouldReset) return
    trackerConnection.onReset()
    activeTracker.reset()
  }

  private fun cancelCountdown(status: String) {
    val command = state.cancelCountdown(status, SystemClock.elapsedRealtime()) ?: return
    if (state.authority == org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH) {
      countdownHandler.removeCallbacks(watchCancelRequestTimeout)
      tracker?.holdWearSessionStart()
      tracker?.requestWearSessionCommand(command)
      countdownHandler.postDelayed(watchCancelRequestTimeout, CANCEL_RETRY_INTERVAL_MS)
    } else {
      tracker?.applyWearSessionCommand(command)
    }
  }

  private fun handleRejectedSessionCommand(
      command: org.runnerup.common.wear.WorkoutSessionProtocol.Command
  ) {
    val pending = state.lastCommand ?: return
    if (pending.commandId != command.commandId ||
        pending.action != command.action ||
        pending.actor != command.actor) return
    countdownHandler.removeCallbacks(watchStartRequestTimeout)
    countdownHandler.removeCallbacks(watchCancelRequestTimeout)
    val activeTracker = tracker
    if (command.action == org.runnerup.common.wear.WorkoutSessionProtocol.Action.START) {
      if (command.actor == org.runnerup.common.wear.WorkoutSessionProtocol.Actor.WATCH) {
        state.resetAfterRejectedAuthoritativeStart("Passet kunde inte starta · försök igen")
      } else if (state.value.phase == WorkoutPreparationPhase.START_REQUESTED) {
        state.onStartRequestFailed("Klockan avböjde start · försök igen")
      }
      if (finishAfterStartResolution) {
        finishAfterStartResolution = false
        resetPreparationTrackerIfOwned(afterRejectedStart = true)
        WearConnectionProvider.cancelWorkoutPreparation(this, state.value.sessionId)
        finish()
      }
      return
    }
    if (command.action == org.runnerup.common.wear.WorkoutSessionProtocol.Action.CANCEL &&
        state.value.phase == WorkoutPreparationPhase.CANCEL_REQUESTED) {
      activeTracker?.releaseWearSessionStart()
      if (activeTracker?.state == TrackerState.CONNECTED) {
        finishAfterCancel = false
        state.onCancelRequestFailed()
        beginAcceptedCountdown()
      } else if (activeTracker?.state == TrackerState.STARTED ||
          activeTracker?.state == TrackerState.PAUSED) {
        finishAfterCancel = false
        WearConnectionProvider.cancelWorkoutPreparation(this, state.value.sessionId)
        runActivityLauncher.launch(Intent(this, RunActivity::class.java))
      } else {
        state.onCancelRequestStillPending("Passets tillstånd ändrades · försök igen")
      }
    }
  }

  private fun restoreSelectedWorkoutFromIntent(activeTracker: Tracker): Boolean {
    return try {
      val restored =
          WorkoutPreparationWorkoutRestorer.restore(
              this,
              intent.getStringExtra(EXTRA_WORKOUT_JSON).orEmpty(),
              intent.getIntExtra(EXTRA_SPORT, Constants.DB.ACTIVITY.SPORT_RUNNING),
              intent.getIntExtra(EXTRA_WORKOUT_TYPE, Constants.WORKOUT_TYPE.BASIC))
      activeTracker.setWorkout(restored)
      true
    } catch (error: Exception) {
      android.util.Log.e("WorkoutPreparation", "Unable to restore selected workout", error)
      false
    }
  }

  override fun onDestroy() {
    countdownHandler.removeCallbacksAndMessages(null)
    if (sessionCommandReceiverRegistered) {
      unregisterReceiver(sessionCommandReceiver)
      sessionCommandReceiverRegistered = false
    }
    tracker?.unregisterTrackerStateListener(trackerStateListener)
    tracker?.setGpsReadinessListener(null)
    if (bound) {
      unbindService(connection)
      bound = false
    }
    super.onDestroy()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean(STATE_TRACKER_CONNECTION_INITIATED, trackerConnection.initiatedByPreparation)
    if (::state.isInitialized && state.value.phase == WorkoutPreparationPhase.COUNTDOWN) {
      state.lastCommand?.let { command ->
        try {
          outState.putByteArray(
              STATE_ACCEPTED_START,
              org.runnerup.common.wear.WorkoutSessionProtocol.encodeCommand(command))
        } catch (_: java.io.IOException) {
          // The session remains safe; restoring an invalid command would be less safe than retrying.
        }
      }
    }
    super.onSaveInstanceState(outState)
  }

  companion object {
    private const val COUNTDOWN_REFRESH_MS = 50L
    private const val START_REQUEST_TIMEOUT_MS = 5_000L
    private const val CANCEL_RETRY_INTERVAL_MS = 1_000L
    private const val STATE_ACCEPTED_START = "org.runnerup.relven.preparation.accepted_start"
    private const val STATE_TRACKER_CONNECTION_INITIATED =
        "org.runnerup.relven.preparation.tracker_connection_initiated"
    private const val EXTRA_SESSION_ID = "org.runnerup.relven.preparation.session_id"
    private const val EXTRA_WORKOUT_NAME = "org.runnerup.relven.preparation.workout_name"
    private const val EXTRA_UPCOMING_STEP = "org.runnerup.relven.preparation.upcoming_step"
    private const val EXTRA_WORKOUT_JSON = "org.runnerup.relven.preparation.workout_json"
    private const val EXTRA_SPORT = "org.runnerup.relven.preparation.sport"
    private const val EXTRA_WORKOUT_TYPE = "org.runnerup.relven.preparation.workout_type"

    @JvmStatic
    fun intent(
        context: Context,
        sessionId: String,
        workoutName: String,
        upcomingStep: String,
        workoutJson: String,
        sport: Int,
        workoutType: Int
    ): Intent =
        Intent(context, WorkoutPreparationActivity::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId)
            .putExtra(EXTRA_WORKOUT_NAME, workoutName)
            .putExtra(EXTRA_UPCOMING_STEP, upcomingStep)
            .putExtra(EXTRA_WORKOUT_JSON, workoutJson)
            .putExtra(EXTRA_SPORT, sport)
            .putExtra(EXTRA_WORKOUT_TYPE, workoutType)
  }
}
