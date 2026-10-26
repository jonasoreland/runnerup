package org.runnerup.service

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAccuracy
import androidx.health.services.client.data.LocationData
import androidx.health.services.client.data.SampleDataPoint
import java.time.Instant
import java.util.concurrent.ExecutionException
import org.runnerup.R
import org.runnerup.common.util.Constants
import org.runnerup.common.wear.WorkoutSessionProtocol
import org.runnerup.common.wear.WorkoutTelemetryOutboxStore
import org.runnerup.common.wear.WorkoutTelemetryProtocol
import org.runnerup.view.MainActivity

/** Owns watch-origin telemetry only; workout state and duration remain in WorkoutSessionProtocol. */
@RequiresApi(30)
class WearExerciseRecorderService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private val controller = WearWorkoutRecorderController()
  private lateinit var exerciseClient: ExerciseClient
  private lateinit var outboxStore: WorkoutTelemetryOutboxStore
  private var callback: ExerciseUpdateCallback? = null
  private var sessionId: String? = null
  private var telemetrySequence = 0L
  private var exerciseStarted = false
  private var callbackRegistered = false
  private var requestedDataTypes: Set<DataType<*, *>> = emptySet()

  private val scheduledStart = Runnable { beginHealthExercise() }

  override fun onCreate() {
    super.onCreate()
    exerciseClient = HealthServices.getClient(this).exerciseClient
    outboxStore = WorkoutTelemetryOutboxStore(this)
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      ensureForeground()
    } catch (error: SecurityException) {
      stopSelf(startId)
      return START_NOT_STICKY
    } catch (error: IllegalStateException) {
      stopSelf(startId)
      return START_NOT_STICKY
    }
    val encoded = intent?.getByteArrayExtra(EXTRA_COMMAND)
    if (encoded == null) {
      restoreRecorderState()
      return START_STICKY
    }
    val command =
      try {
        WorkoutSessionProtocol.decodeCommand(encoded)
      } catch (malformed: java.io.IOException) {
        stopSelf(startId)
        return START_NOT_STICKY
      }
    applyAcceptedCommand(command)
    return START_STICKY
  }

  private fun applyAcceptedCommand(command: WorkoutSessionProtocol.Command) {
    val previous = controller.snapshot()
    val effect = controller.accept(command, SystemClock.elapsedRealtime())
    if (effect == WearWorkoutRecorderController.Effect.NONE) return
    if (!persistControllerState()) {
      controller.restore(previous)
      return
    }
    when (effect) {
      WearWorkoutRecorderController.Effect.START -> {
        sessionId = command.sessionId
        telemetrySequence =
          try {
            outboxStore.lastSequence(command.sessionId)
          } catch (storageFailure: IllegalStateException) {
            updateNotification(unavailable = true)
            return
          }
        scheduleStart(command.plannedStartElapsedRealtimeMs)
      }
      WearWorkoutRecorderController.Effect.PAUSE -> {
        handler.removeCallbacks(scheduledStart)
        if (exerciseStarted) runHealthOperation(exerciseClient::pauseExerciseAsync)
        updateNotification(unavailable = false)
      }
      WearWorkoutRecorderController.Effect.RESUME -> {
        if (exerciseStarted) runHealthOperation(exerciseClient::resumeExerciseAsync)
        else scheduleStart(SystemClock.elapsedRealtime())
        updateNotification(unavailable = false)
      }
      WearWorkoutRecorderController.Effect.FINISH -> finishExercise()
      WearWorkoutRecorderController.Effect.NONE -> Unit
    }
  }

  private fun scheduleStart(deadlineElapsedRealtimeMs: Long) {
    handler.removeCallbacks(scheduledStart)
    val delay = deadlineElapsedRealtimeMs - SystemClock.elapsedRealtime()
    if (delay <= 0) beginHealthExercise() else handler.postDelayed(scheduledStart, delay)
  }

  private fun beginHealthExercise() {
    val activeSessionId = sessionId ?: return
    val now = SystemClock.elapsedRealtime()
    controller.advanceTo(now)
    if (!persistControllerState()) return

    val locationReady = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && isGpsEnabled()
    val heartRateReady = hasHeartRateForegroundAndBackgroundPermissions()
    if (!locationReady && !heartRateReady) {
      updateNotification(unavailable = true)
      return
    }

    val capabilitiesFuture = exerciseClient.getCapabilitiesAsync()
    capabilitiesFuture.addListener(
      {
        val capabilities = getFutureResult(capabilitiesFuture) ?: run {
          updateNotification(unavailable = true)
          return@addListener
        }
        if (ExerciseType.RUNNING !in capabilities.supportedExerciseTypes) {
          updateNotification(unavailable = true)
          return@addListener
        }
        val running = capabilities.getExerciseTypeCapabilities(ExerciseType.RUNNING)
        val types = linkedSetOf<DataType<*, *>>()
        if (locationReady && DataType.LOCATION in running.supportedDataTypes) {
          types.add(DataType.LOCATION)
        }
        if (heartRateReady && DataType.HEART_RATE_BPM in running.supportedDataTypes) {
          types.add(DataType.HEART_RATE_BPM)
        }
        if (types.isEmpty()) {
          updateNotification(unavailable = true)
          return@addListener
        }
        requestedDataTypes = types
        checkCurrentExerciseBeforeStarting(
          activeSessionId,
          locationReady,
          controller.snapshot().phase != WearWorkoutRecorderController.Phase.PAUSED,
        )
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  @SuppressLint("RestrictedApi")
  private fun checkCurrentExerciseBeforeStarting(
    activeSessionId: String,
    locationReady: Boolean,
    allowStart: Boolean,
  ) {
    val exerciseInfoFuture = exerciseClient.getCurrentExerciseInfoAsync()
    exerciseInfoFuture.addListener(
      {
        val info = getFutureResult(exerciseInfoFuture) ?: run {
          updateNotification(unavailable = true)
          return@addListener
        }
        when (info.exerciseTrackedStatus) {
          ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> {
            sessionId = activeSessionId
            exerciseStarted = true
            registerCallback()
            if (!allowStart) runHealthOperation(exerciseClient::pauseExerciseAsync)
            updateNotification(unavailable = false)
          }
          ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS -> updateNotification(unavailable = true)
          ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS ->
            if (allowStart) startNewHealthExercise(locationReady)
            else updateNotification(unavailable = false)
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun startNewHealthExercise(locationEnabled: Boolean) {
    val dataTypes = requestedDataTypes
    if (dataTypes.isEmpty()) {
      updateNotification(unavailable = true)
      return
    }
    registerCallback()
    try {
      val config =
        ExerciseConfig.Builder(ExerciseType.RUNNING)
          .setDataTypes(dataTypes)
          .setIsAutoPauseAndResumeEnabled(false)
          .setIsGpsEnabled(locationEnabled && DataType.LOCATION in dataTypes)
          .build()
      val startFuture = exerciseClient.startExerciseAsync(config)
      startFuture.addListener(
        {
          if (!futureSucceeded(startFuture)) {
            unregisterCallback()
            updateNotification(unavailable = true)
          } else {
            exerciseStarted = true
            updateNotification(unavailable = false)
          }
        },
        ContextCompat.getMainExecutor(this),
      )
    } catch (error: SecurityException) {
      unregisterCallback()
      updateNotification(unavailable = true)
    } catch (error: IllegalArgumentException) {
      unregisterCallback()
      updateNotification(unavailable = true)
    }
  }

  private fun registerCallback() {
    if (callbackRegistered) return
    callback =
      object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
          callbackRegistered = false
          updateNotification(unavailable = true)
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
          if (update.exerciseStateInfo.state.isEnded) {
            exerciseStarted = false
            updateNotification(unavailable = true)
            return
          }
          persistUpdateSamples(update)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onAvailabilityChanged(
          dataType: DataType<*, *>,
          availability: androidx.health.services.client.data.Availability,
        ) {
          if (availability is androidx.health.services.client.data.LocationAvailability) {
            updateNotification(
              unavailable =
                availability == androidx.health.services.client.data.LocationAvailability.NO_GNSS ||
                  availability == androidx.health.services.client.data.LocationAvailability.UNAVAILABLE,
            )
          }
        }
      }
    exerciseClient.setUpdateCallback(ContextCompat.getMainExecutor(this), callback!!)
    callbackRegistered = true
  }

  private fun persistUpdateSamples(update: ExerciseUpdate) {
    val currentSession = sessionId ?: return
    val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
    update.latestMetrics.getData(DataType.LOCATION).forEach { point ->
      val location = point.value
      val elapsedNanos = point.timeDurationFromBoot.toNanos()
      if (!controller.shouldRecord(currentSession, elapsedNanos)) return@forEach
      appendLocationSample(currentSession, point, location, elapsedNanos, bootInstant)
    }
    update.latestMetrics.getData(DataType.HEART_RATE_BPM).forEach { point ->
      val elapsedNanos = point.timeDurationFromBoot.toNanos()
      if (!controller.shouldRecord(currentSession, elapsedNanos)) return@forEach
      appendHeartRateSample(currentSession, point, elapsedNanos, bootInstant)
    }
  }

  private fun appendLocationSample(
    currentSession: String,
    point: SampleDataPoint<LocationData>,
    location: LocationData,
    elapsedNanos: Long,
    bootInstant: Instant,
  ) {
    val accuracy = point.accuracy as? LocationAccuracy
    val sample =
      WorkoutTelemetryProtocol.Sample(
        currentSession,
        telemetrySequence + 1,
        elapsedNanos,
        point.getTimeInstant(bootInstant).toEpochMilli().coerceAtLeast(0),
        location.latitude,
        location.longitude,
        accuracy?.horizontalPositionErrorMeters?.takeIf { it.isFinite() && it in 0.0..1000.0 },
        location.altitude.takeIf { it.isFinite() && it in -1000.0..10000.0 },
        null,
        null,
      )
    appendPersistedSample(currentSession, sample)
  }

  private fun appendHeartRateSample(
    currentSession: String,
    point: SampleDataPoint<Double>,
    elapsedNanos: Long,
    bootInstant: Instant,
  ) {
    val sample =
      WorkoutTelemetryProtocol.Sample(
        currentSession,
        telemetrySequence + 1,
        elapsedNanos,
        point.getTimeInstant(bootInstant).toEpochMilli().coerceAtLeast(0),
        null,
        null,
        null,
        null,
        null,
        point.value.takeIf { it.isFinite() && it in 20.0..250.0 } ?: return,
      )
    appendPersistedSample(currentSession, sample)
  }

  private fun appendPersistedSample(
    currentSession: String,
    sample: WorkoutTelemetryProtocol.Sample,
  ) {
    val result = outboxStore.append(currentSession, sample)
    if (result == org.runnerup.common.wear.WorkoutTelemetryOutbox.AppendResult.APPENDED) {
      telemetrySequence = sample.sequence
      sendBroadcast(Intent(Constants.Intents.WORKOUT_TELEMETRY_AVAILABLE).setPackage(packageName))
    } else {
      updateNotification(unavailable = true)
    }
  }

  private fun restoreRecorderState() {
    val preferences = getSharedPreferences(STATE_PREFERENCES, MODE_PRIVATE)
    val storedSession = preferences.getString(KEY_SESSION_ID, null) ?: run {
      stopSelf()
      return
    }
    val storedPhase =
      runCatching {
        WearWorkoutRecorderController.Phase.valueOf(
          preferences.getString(KEY_PHASE, WearWorkoutRecorderController.Phase.IDLE.name)!!,
        )
      }.getOrNull() ?: run {
        stopSelf()
        return
      }
    if (storedPhase == WearWorkoutRecorderController.Phase.IDLE ||
      storedPhase == WearWorkoutRecorderController.Phase.FINISHED
    ) {
      stopSelf()
      return
    }
    sessionId = storedSession
    telemetrySequence =
      try {
        outboxStore.lastSequence(storedSession)
      } catch (storageFailure: IllegalStateException) {
        updateNotification(unavailable = true)
        stopSelf()
        return
      }
    controller.restore(
      WearWorkoutRecorderController.Snapshot(
        storedSession,
        preferences.getLong(KEY_SEQUENCE, 0),
        preferences.getLong(KEY_START_DEADLINE, 0),
        preferences.getLong(KEY_ACTIVE_SINCE, 0),
        storedPhase,
      ),
    )
    if (storedPhase == WearWorkoutRecorderController.Phase.COUNTDOWN) {
      scheduleStart(preferences.getLong(KEY_START_DEADLINE, 0))
      return
    }
    beginHealthExercise()
  }

  @SuppressLint("UseKtx")
  private fun persistControllerState(): Boolean {
    val snapshot = controller.snapshot()
    val preferences = getSharedPreferences(STATE_PREFERENCES, MODE_PRIVATE)
    return preferences.edit()
      .putString(KEY_SESSION_ID, snapshot.sessionId)
      .putLong(KEY_SEQUENCE, snapshot.lastSequence)
      .putLong(KEY_START_DEADLINE, snapshot.startDeadlineElapsedRealtimeMs)
      .putLong(KEY_ACTIVE_SINCE, snapshot.activeSinceElapsedRealtimeMs)
      .putString(KEY_PHASE, snapshot.phase.name)
      .commit()
  }

  private fun finishExercise() {
    handler.removeCallbacks(scheduledStart)
    if (exerciseStarted) {
      runHealthOperation(exerciseClient::endExerciseAsync) {
        exerciseStarted = false
        unregisterCallback()
        stopRecorder()
      }
    } else {
      stopRecorder()
    }
  }

  private fun runHealthOperation(
    operation: () -> com.google.common.util.concurrent.ListenableFuture<Void>,
    onSuccess: () -> Unit = {},
  ) {
    try {
      val future = operation()
      future.addListener(
        {
          if (futureSucceeded(future)) onSuccess()
          else updateNotification(unavailable = true)
        },
        ContextCompat.getMainExecutor(this),
      )
    } catch (error: Exception) {
      updateNotification(unavailable = true)
    }
  }

  private fun <T> getFutureResult(
    future: com.google.common.util.concurrent.ListenableFuture<T>,
  ): T? =
    try {
      future.get()
    } catch (error: ExecutionException) {
      null
    } catch (error: InterruptedException) {
      Thread.currentThread().interrupt()
      null
    }

  private fun futureSucceeded(
    future: com.google.common.util.concurrent.ListenableFuture<*>,
  ): Boolean =
    try {
      future.get()
      true
    } catch (error: ExecutionException) {
      false
    } catch (error: InterruptedException) {
      Thread.currentThread().interrupt()
      false
    }

  private fun unregisterCallback() {
    callback?.let { exerciseClient.clearUpdateCallbackAsync(it) }
    callbackRegistered = false
    callback = null
  }

  private fun stopRecorder() {
    unregisterCallback()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun ensureForeground() {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.workout_recorder_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ),
    )
    val notification = buildNotification(unavailable = false)
    val types = getForegroundServiceTypes()
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
  }

  private fun updateNotification(unavailable: Boolean) {
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, buildNotification(unavailable))
  }

  private fun buildNotification(unavailable: Boolean) =
    NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.relven_logo)
      .setContentTitle(getString(R.string.workout_recorder_notification_title))
      .setContentText(
        getString(
          if (unavailable) R.string.workout_recorder_notification_unavailable
          else R.string.workout_recorder_notification_body,
        ),
      )
      .setContentIntent(
        PendingIntent.getActivity(
          this,
          0,
          Intent(this, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
      )
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .build()

  private fun getForegroundServiceTypes(): Int {
    var types = 0
    if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
      types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
      hasHeartRateForegroundAndBackgroundPermissions()
    ) {
      types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
    }
    return types
  }

  private fun hasHeartRateForegroundAndBackgroundPermissions(): Boolean {
    val permission =
      if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
      else Manifest.permission.BODY_SENSORS
    if (!hasPermission(permission)) return false
    val backgroundPermission =
      when {
        Build.VERSION.SDK_INT >= 36 -> "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        Build.VERSION.SDK_INT >= 33 -> Manifest.permission.BODY_SENSORS_BACKGROUND
        else -> null
      }
    return backgroundPermission == null || hasPermission(backgroundPermission)
  }

  private fun hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

  private fun isGpsEnabled(): Boolean =
    try {
      getSystemService(LocationManager::class.java)
        ?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    } catch (error: Exception) {
      false
    }

  override fun onDestroy() {
    handler.removeCallbacks(scheduledStart)
    unregisterCallback()
    super.onDestroy()
  }

  companion object {
    private const val CHANNEL_ID = "relven_workout_recorder"
    private const val NOTIFICATION_ID = 38
    private const val STATE_PREFERENCES = "wear_workout_recorder_state_v1"
    private const val EXTRA_COMMAND = "org.runnerup.extra.WORKOUT_SESSION_COMMAND"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_SEQUENCE = "sequence"
    private const val KEY_START_DEADLINE = "start_deadline"
    private const val KEY_ACTIVE_SINCE = "active_since"
    private const val KEY_PHASE = "phase"

  }
}
