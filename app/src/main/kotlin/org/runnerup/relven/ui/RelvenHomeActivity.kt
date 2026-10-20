package org.runnerup.relven.ui

import android.content.Intent
import android.os.Bundle
import android.database.Cursor
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.os.ConfigurationCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.runnerup.common.util.Constants.DB
import org.runnerup.db.DBHelper
import org.runnerup.workout.RelvenPlanStore
import org.runnerup.view.MainLayout
import org.runnerup.relven.exercise.ExerciseCatalogRepository
import org.runnerup.relven.strength.CompletedStrengthSession
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.StrengthSessionStore

/** Production home screen; RunnerUp remains authoritative for workout controls. */
class RelvenHomeActivity : ComponentActivity() {
  companion object {
    private const val HOME_DESTINATION_STATE = "relven.home.destination"
  }

  private var recentActivity by mutableStateOf<RecentActivity?>(null)
  private var sessionStatus by mutableStateOf<HomeSessionStatus?>(null)
  private var todayPlan by mutableStateOf<TodayPlanState>(TodayPlanState.Loading)
  private var activePlanState by mutableStateOf<ActivePlanLoadState>(ActivePlanLoadState.Loading)
  private var planRevisionStatus by mutableStateOf<PlanRevisionStatus>(PlanRevisionStatus.Idle)
  private var exerciseCatalogLoadState by mutableStateOf<ExerciseCatalogLoadState>(ExerciseCatalogLoadState.Loading)
  private var currentTimeMillis by mutableLongStateOf(System.currentTimeMillis())
  private var destination by mutableStateOf(HomeDestination.Today)
  private var trackerSessionBinding: HomeTrackerSessionBinding? = null
  private var strengthSession by mutableStateOf<StrengthSession?>(null)
  private var viewedStrengthCompletion by mutableStateOf<CompletedStrengthSession?>(null)
  private var completedStrengthSessions by mutableStateOf<Map<String, CompletedStrengthSession>>(emptyMap())
  private var strengthSaveError by mutableStateOf(false)
  private var strengthTransitionSaving by mutableStateOf(false)
  private var pendingStrengthSessionChoice by mutableStateOf<PendingStrengthSessionChoice?>(null)
  private var strengthRecoveryNotice by mutableStateOf(false)
  private var strengthFinishing by mutableStateOf(false)
  private val strengthStoreMutex = Mutex()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    destination = homeDestinationFromSaved(savedInstanceState?.getString(HOME_DESTINATION_STATE))
    loadExerciseCatalog()
    setContent {
      RelvenTheme {
        val currentStrengthSession = strengthSession
        val completionSummary = viewedStrengthCompletion
        if (currentStrengthSession != null || completionSummary != null) {
          StrengthSessionScreen(
              session = currentStrengthSession ?: requireNotNull(completionSummary).state,
              completedAtMillis = completionSummary?.finishedAtMillis,
              saveError = strengthSaveError,
              isSavingProgress = strengthTransitionSaving,
              isSavingCompletion = strengthFinishing,
              onTick = { updateStrengthSession { it.tick() } },
              onLogRepetitions = { count -> updateStrengthSession { it.completeRepetitions(count) } },
              onSkipRest = { updateStrengthSession { it.skipRest() } },
              onPause = { updateStrengthSession { it.pause() } },
              onResume = { updateStrengthSession { it.resume() } },
              onFinish = ::finishStrengthSession,
              onClose = ::closeStrengthSession)
        } else RelvenHome(
            recentActivity = recentActivity,
            sessionStatus = sessionStatus,
            todayPlan = todayPlan,
            activePlanState = activePlanState,
            exerciseCatalogLoadState = exerciseCatalogLoadState,
            planRevisionStatus = planRevisionStatus,
            currentTimeMillis = currentTimeMillis,
            destination = destination,
            onSelectDestination = { destination = it },
            onOpenActiveSession = { startActivity(runActivityIntent(this)) },
            onOpenWorkout = { startActivity(Intent(this, MainLayout::class.java)) },
            onOpenHistory = {
              startActivity(
                  Intent(this, MainLayout::class.java)
                      .putExtra(MainLayout.EXTRA_INITIAL_TAB, 1))
            },
            onReschedulePlan = ::rescheduleActivePlan,
            onAddWorkoutToPlan = ::addWorkoutToActivePlan,
            onAddRestToPlan = ::addRestToActivePlan,
            onRemovePlanEntry = ::removeActivePlanEntry,
            onRenamePlan = ::renameActivePlan,
            onRenameWorkout = ::renameActivePlanWorkout,
            onEditRunningEnd = ::editActivePlanRunningEnd,
            onEditRunningTarget = ::editActivePlanRunningTarget,
            onRetryExerciseCatalog = ::loadExerciseCatalog,
            completedStrengthSessions = completedStrengthSessions,
            onStartStrength = ::startStrengthSession,
            onViewStrengthCompletion = { viewedStrengthCompletion = it },
            strengthRecoveryNotice = strengthRecoveryNotice,
            onDismissStrengthRecoveryNotice = { strengthRecoveryNotice = false },
            pendingStrengthSessionChoice = pendingStrengthSessionChoice,
            onContinueExistingStrengthSession = ::continueExistingStrengthSession,
            onCancelExistingStrengthSession = { pendingStrengthSessionChoice = null })
      }
    }
  }

  override fun onStart() {
    super.onStart()
    trackerSessionBinding =
        HomeTrackerSessionBinding(this) { status ->
          runOnUiThread { sessionStatus = status }
        }.also { it.start() }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString(HOME_DESTINATION_STATE, destination.name)
    super.onSaveInstanceState(outState)
  }

  override fun onResume() {
    super.onResume()
    todayPlan = TodayPlanState.Loading
    activePlanState = ActivePlanLoadState.Loading
    planRevisionStatus = PlanRevisionStatus.Idle
    currentTimeMillis = System.currentTimeMillis()
    lifecycleScope.launch {
      val homeData =
          withContext(Dispatchers.IO) {
            val planState = loadActivePlan()
            val plan = (planState as? ActivePlanLoadState.Loaded)?.plan
            val completions = buildMap {
              plan?.days()?.forEach { scheduled ->
                try {
                  StrengthSessionStore.completed(applicationContext, scheduled.id())?.let {
                    put(scheduled.id(), it)
                  }
                } catch (error: Exception) {
                  Log.w("RelvenHome", "Could not load strength completion", error)
                }
              }
            }
            HomeLoad(
                loadRecentActivity(),
                planState,
                try {
                  strengthStoreMutex.withLock {
                    StrengthSessionStore.loadInProgress(applicationContext)
                  }
                } catch (error: Exception) {
                  Log.w("RelvenHome", "Could not restore strength session", error)
                  if (error is StrengthSessionStore.InvalidSavedSessionException) {
                    strengthRecoveryNotice = true
                  }
                  null
                },
                completions)
          }
      recentActivity = homeData.recentActivity
      activePlanState = homeData.activePlan
      completedStrengthSessions = homeData.completedStrengthSessions
      strengthSession = homeData.inProgressStrengthSession
      todayPlan =
          when (val planState = homeData.activePlan) {
            ActivePlanLoadState.Loading -> TodayPlanState.Loading
            ActivePlanLoadState.NoActivePlan -> TodayPlanState.NoActivePlan
            ActivePlanLoadState.Unavailable -> TodayPlanState.Unavailable
            is ActivePlanLoadState.Loaded ->
                todayPlanState(planState.plan, currentTimeMillis)
          }
    }
  }

  private fun startStrengthSession(planId: String, entry: PlanCalendarEntry.Workout) {
    val workout = entry.strengthDetails ?: return
    if (entry.completion != null) return
    lifecycleScope.launch {
      try {
        val session = withContext(Dispatchers.IO) {
          strengthStoreMutex.withLock {
            val saved = StrengthSessionStore.loadInProgress(applicationContext)
            if (saved != null) saved
            else StrengthSession.start(planId, entry.sessionId, workout).also {
              StrengthSessionStore.saveInProgress(applicationContext, it)
            }
          }
        }
        val requestedPlanId = planId
        if (session.matchesScheduledEntry(requestedPlanId, entry.sessionId)) {
          strengthSaveError = false
          strengthSession = session
          viewedStrengthCompletion = null
        } else {
          pendingStrengthSessionChoice =
              PendingStrengthSessionChoice(session, workout.name())
        }
      } catch (error: Exception) {
        Log.w("RelvenHome", "Could not start strength session", error)
        strengthSaveError = true
      }
    }
  }

  private fun updateStrengthSession(transform: (StrengthSession) -> StrengthSession) {
    if (strengthTransitionSaving) return
    val next = strengthSession?.let(transform) ?: return
    strengthTransitionSaving = true
    strengthSaveError = false
    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) {
          strengthStoreMutex.withLock {
            StrengthSessionStore.saveInProgress(applicationContext, next)
          }
        }
        strengthSession = next
      } catch (error: Exception) {
        Log.w("RelvenHome", "Could not save strength session progress", error)
        strengthSaveError = true
      } finally {
        strengthTransitionSaving = false
      }
    }
  }

  private fun finishStrengthSession() {
    if (strengthFinishing) return
    val finished = strengthSession?.takeIf { it.isComplete } ?: return
    strengthFinishing = true
    val finishedAt = System.currentTimeMillis()
    lifecycleScope.launch {
      try {
        withContext(Dispatchers.IO) {
          strengthStoreMutex.withLock {
            StrengthSessionStore.complete(applicationContext, finished, finishedAt)
          }
        }
        val completion = CompletedStrengthSession(finished, finishedAt)
        completedStrengthSessions = completedStrengthSessions + (finished.scheduledEntryId to completion)
        strengthSession = null
        viewedStrengthCompletion = completion
        strengthSaveError = false
        strengthFinishing = false
      } catch (error: Exception) {
        Log.w("RelvenHome", "Could not complete strength session", error)
        strengthSaveError = true
        strengthFinishing = false
      }
    }
  }

  private fun closeStrengthSession() {
    val current = strengthSession
    if (current != null && current.phase != StrengthSession.Phase.PAUSED && !current.isComplete) {
      if (strengthTransitionSaving) return
      val paused = current.pause()
      strengthTransitionSaving = true
      lifecycleScope.launch {
        try {
          withContext(Dispatchers.IO) {
            strengthStoreMutex.withLock {
              StrengthSessionStore.saveInProgress(applicationContext, paused)
            }
          }
          strengthSession = null
          viewedStrengthCompletion = null
          strengthSaveError = false
        } catch (error: Exception) {
          Log.w("RelvenHome", "Could not pause strength session before closing", error)
          strengthSaveError = true
        } finally {
          strengthTransitionSaving = false
        }
      }
      return
    }
    strengthSession = null
    viewedStrengthCompletion = null
  }

  private fun continueExistingStrengthSession() {
    pendingStrengthSessionChoice?.let { choice ->
      strengthSession = choice.session
      viewedStrengthCompletion = null
      strengthSaveError = false
    }
    pendingStrengthSessionChoice = null
  }

  private fun loadActivePlan(): ActivePlanLoadState {
    return try {
      RelvenPlanStore.activePlan(applicationContext)?.let(ActivePlanLoadState::Loaded)
          ?: ActivePlanLoadState.NoActivePlan
    } catch (error: Exception) {
      Log.w("RelvenHome", "Could not load active plan", error)
      ActivePlanLoadState.Unavailable
    }
  }

  private fun loadExerciseCatalog() {
    exerciseCatalogLoadState = ExerciseCatalogLoadState.Loading
    val languageTag =
        ConfigurationCompat.getLocales(resources.configuration)
            .get(0)
            ?.toLanguageTag()
            ?: Locale.getDefault().toLanguageTag()
    lifecycleScope.launch {
      exerciseCatalogLoadState =
          withContext(Dispatchers.IO) {
            loadExerciseCatalogState(
                { ExerciseCatalogRepository(applicationContext).load(languageTag) }) { error ->
                  Log.w("RelvenHome", "Could not load the bundled exercise catalog", error)
                }
          }
    }
  }

  private fun rescheduleActivePlan(sessionId: String, targetDate: String) {
    savePlanRevision { plan ->
      RelvenPlanStore.rescheduleActivePlan(applicationContext, plan, sessionId, targetDate)
    }
  }

  private fun addWorkoutToActivePlan(date: String, workoutId: String) {
    savePlanRevision { plan ->
      RelvenPlanStore.addWorkoutToActivePlan(
          applicationContext, plan, date, workoutId, java.util.UUID.randomUUID().toString())
    }
  }

  private fun addRestToActivePlan(date: String) {
    savePlanRevision { plan ->
      RelvenPlanStore.addRestToActivePlan(
          applicationContext, plan, date, java.util.UUID.randomUUID().toString())
    }
  }

  private fun removeActivePlanEntry(sessionId: String) {
    savePlanRevision { plan ->
      RelvenPlanStore.removeActivePlanEntry(applicationContext, plan, sessionId)
    }
  }

  private fun renameActivePlan(name: String) {
    savePlanRevision { plan ->
      RelvenPlanStore.renameActivePlan(applicationContext, plan, name)
    }
  }

  private fun renameActivePlanWorkout(workoutId: String, name: String) {
    savePlanRevision { plan ->
      RelvenPlanStore.renameWorkoutInActivePlan(applicationContext, plan, workoutId, name)
    }
  }

  private fun editActivePlanRunningEnd(workoutId: String, segmentId: String, end: org.json.JSONObject) {
    savePlanRevision { plan ->
      RelvenPlanStore.editRunningSegmentEndInActivePlan(applicationContext, plan, workoutId, segmentId, end)
    }
  }

  private fun editActivePlanRunningTarget(workoutId: String, segmentId: String, target: org.json.JSONObject?) {
    savePlanRevision { plan ->
      RelvenPlanStore.editRunningSegmentTargetInActivePlan(applicationContext, plan, workoutId, segmentId, target)
    }
  }

  private fun savePlanRevision(
      createRevision: (org.runnerup.workout.RelvenPlanImporter.Plan) ->
          org.runnerup.workout.RelvenPlanImporter.Plan) {
    val sourcePlan = (activePlanState as? ActivePlanLoadState.Loaded)?.plan
    if (sourcePlan == null) {
      planRevisionStatus = PlanRevisionStatus.Failed
      return
    }
    planRevisionStatus = PlanRevisionStatus.Saving
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              createRevision(sourcePlan)
            }
          }
      result.fold(
          onSuccess = { revision ->
            activePlanState = ActivePlanLoadState.Loaded(revision)
            todayPlan = todayPlanState(revision, currentTimeMillis)
            planRevisionStatus = PlanRevisionStatus.Saved
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not save plan revision", error)
            planRevisionStatus =
                if (error is IllegalArgumentException) PlanRevisionStatus.Conflict
                else PlanRevisionStatus.Failed
          })
    }
  }

  override fun onStop() {
    trackerSessionBinding?.stop()
    trackerSessionBinding = null
    super.onStop()
  }

  private fun loadRecentActivity(): RecentActivity? {
    try {
      val db = DBHelper.getReadableDatabase(applicationContext)
      val columns = arrayOf(DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME)
      db.query(DB.ACTIVITY.TABLE, columns, "deleted = 0", null, null, null,
          "${DB.ACTIVITY.START_TIME} DESC", "1").use { cursor: Cursor ->
        if (!cursor.moveToFirst()) return null
        return RecentActivity(
            startedAtSeconds = cursor.getLong(0),
            distanceMeters = if (cursor.isNull(1)) null else cursor.getDouble(1),
            durationSeconds = if (cursor.isNull(2)) null else cursor.getLong(2))
      }
    } catch (error: RuntimeException) {
      Log.w("RelvenHome", "Could not load recent activity", error)
      return null
    }
  }
}

private data class RecentActivity(
    val startedAtSeconds: Long,
    val distanceMeters: Double?,
    val durationSeconds: Long?)

private data class HomeLoad(
    val recentActivity: RecentActivity?,
    val activePlan: ActivePlanLoadState,
    val inProgressStrengthSession: StrengthSession?,
    val completedStrengthSessions: Map<String, CompletedStrengthSession>)

private data class PendingStrengthSessionChoice(
    val session: StrengthSession,
    val requestedWorkoutName: String)

internal enum class HomeDestination {
  Today,
  Plan,
  Exercises
}

internal fun homeDestinationFromSaved(saved: String?): HomeDestination =
    saved?.let { value -> HomeDestination.entries.firstOrNull { it.name == value } }
        ?: HomeDestination.Today

@Composable
private fun RelvenHome(
    recentActivity: RecentActivity?,
    sessionStatus: HomeSessionStatus?,
    todayPlan: TodayPlanState,
    activePlanState: ActivePlanLoadState,
    exerciseCatalogLoadState: ExerciseCatalogLoadState,
    planRevisionStatus: PlanRevisionStatus,
    currentTimeMillis: Long,
    destination: HomeDestination,
    onSelectDestination: (HomeDestination) -> Unit,
    onOpenActiveSession: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onReschedulePlan: (String, String) -> Unit,
    onAddWorkoutToPlan: (String, String) -> Unit,
    onAddRestToPlan: (String) -> Unit,
    onRemovePlanEntry: (String) -> Unit,
    onRenamePlan: (String) -> Unit,
    onRenameWorkout: (String, String) -> Unit,
    onEditRunningEnd: (String, String, org.json.JSONObject) -> Unit,
    onEditRunningTarget: (String, String, org.json.JSONObject?) -> Unit,
    onRetryExerciseCatalog: () -> Unit,
    completedStrengthSessions: Map<String, CompletedStrengthSession>,
    onStartStrength: (String, PlanCalendarEntry.Workout) -> Unit,
    onViewStrengthCompletion: (CompletedStrengthSession) -> Unit,
    strengthRecoveryNotice: Boolean,
    onDismissStrengthRecoveryNotice: () -> Unit,
    pendingStrengthSessionChoice: PendingStrengthSessionChoice?,
    onContinueExistingStrengthSession: () -> Unit,
    onCancelExistingStrengthSession: () -> Unit) {
  Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
  Box(Modifier.fillMaxSize()) {
    if (destination == HomeDestination.Today) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
      Spacer(Modifier.height(20.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = RelvenColors.Orange, modifier = Modifier.size(44.dp)) {
          Box(contentAlignment = Alignment.Center) {
            Text("R", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
          }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
          Text("Relven", fontWeight = FontWeight.Bold, fontSize = 17.sp)
          Text("Din träning", color = RelvenColors.MutedText, fontSize = 12.sp)
        }
      }
      sessionStatus?.let { status ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = RelvenColors.Surface,
            border = BorderStroke(1.dp, RelvenColors.Orange.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                status.label,
                color = RelvenColors.Orange,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp))
            Button(
                onClick = onOpenActiveSession,
                colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
              Text("Öppna pass")
            }
          }
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Idag", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Redo för nästa pass?",
            fontSize = 15.sp,
            color = RelvenColors.MutedText)
      }
      TodayPlanCard(todayPlan)
      Surface(
          shape = RoundedCornerShape(30.dp),
          color = RelvenColors.Surface,
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
          modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("Löpning", fontSize = 19.sp, fontWeight = FontWeight.Bold)
          Text(
              "Välj ett fritt pass eller bygg intervaller i träningsvyn.",
              fontSize = 14.sp,
              color = RelvenColors.MutedText)
          Button(
              onClick = onOpenWorkout,
              colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
              modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Öppna träningsvyn")
          }
        }
      }
      if (recentActivity != null) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = RelvenColors.Surface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Senaste passet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("sv-SE"))
                    .format(Date(recentActivity.startedAtSeconds * 1000)),
                color = RelvenColors.MutedText,
                fontSize = 13.sp)
            val distance = recentActivity.distanceMeters?.let {
              String.format(Locale.forLanguageTag("sv-SE"), "%.2f km", it / 1000.0)
            } ?: "–"
            val minutes = recentActivity.durationSeconds?.let { "${it / 60} min" } ?: "–"
            Text("$distance  ·  $minutes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
    } else if (destination == HomeDestination.Plan) {
      PlanCalendarScreen(
          loadState = activePlanState,
          revisionStatus = planRevisionStatus,
          nowMillis = currentTimeMillis,
          completedStrengthSessions = completedStrengthSessions,
          onStartStrength = onStartStrength,
          onViewStrengthCompletion = onViewStrengthCompletion,
          onOpenWorkoutManager = onOpenWorkout,
          onReschedule = onReschedulePlan,
          onAddWorkout = onAddWorkoutToPlan,
          onAddRest = onAddRestToPlan,
          onRemoveEntry = onRemovePlanEntry,
          onRenamePlan = onRenamePlan,
          onRenameWorkout = onRenameWorkout,
          onEditRunningEnd = onEditRunningEnd,
          onEditRunningTarget = onEditRunningTarget)
    } else {
      ExerciseCatalogScreen(exerciseCatalogLoadState, onRetry = onRetryExerciseCatalog)
    }
    HomeNavigationBar(
        destination = destination,
        onSelectDestination = onSelectDestination,
        onOpenWorkout = onOpenWorkout,
        onOpenHistory = onOpenHistory,
        modifier = Modifier.align(Alignment.BottomCenter))
    if (strengthRecoveryNotice) {
      AlertDialog(
          onDismissRequest = onDismissStrengthRecoveryNotice,
          title = { Text(stringResource(org.runnerup.common.R.string.Strength_session_recovery_title)) },
          text = { Text(stringResource(org.runnerup.common.R.string.Strength_session_recovery_message)) },
          confirmButton = {
            TextButton(onClick = onDismissStrengthRecoveryNotice) {
              Text(stringResource(org.runnerup.common.R.string.Strength_session_exit))
            }
          })
    }
    if (!strengthRecoveryNotice && pendingStrengthSessionChoice != null) {
      AlertDialog(
          onDismissRequest = onCancelExistingStrengthSession,
          title = { Text(stringResource(org.runnerup.common.R.string.Strength_session_existing_title)) },
          text = {
            Text(
                stringResource(
                    org.runnerup.common.R.string.Strength_session_existing_message,
                    pendingStrengthSessionChoice.session.workout.name(),
                    pendingStrengthSessionChoice.requestedWorkoutName))
          },
          confirmButton = {
            TextButton(onClick = onContinueExistingStrengthSession) {
              Text(stringResource(org.runnerup.common.R.string.Strength_session_existing_continue))
            }
          },
          dismissButton = {
            TextButton(onClick = onCancelExistingStrengthSession) {
              Text(stringResource(org.runnerup.common.R.string.Strength_session_existing_cancel))
            }
          })
    }
  }
  }
}

@Composable
private fun HomeNavigationBar(
    destination: HomeDestination,
    onSelectDestination: (HomeDestination) -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
  Surface(
      color = Color(0xDD0E141E),
      shape = RoundedCornerShape(28.dp),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
      modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
      HomeNavigationItem(
          selected = destination == HomeDestination.Today,
          icon = { tint -> Icon(Icons.Default.Home, contentDescription = null, tint = tint) },
          label = stringResource(org.runnerup.common.R.string.Home_nav_today),
          onClick = { onSelectDestination(HomeDestination.Today) },
          modifier = Modifier.weight(1f))
      HomeNavigationItem(
          selected = destination == HomeDestination.Plan,
          icon = { tint -> Icon(Icons.Default.DateRange, contentDescription = null, tint = tint) },
          label = stringResource(org.runnerup.common.R.string.Home_nav_plan),
          onClick = { onSelectDestination(HomeDestination.Plan) },
          modifier = Modifier.weight(1f))
      HomeNavigationItem(
          selected = destination == HomeDestination.Exercises,
          icon = { tint -> Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = tint) },
          label = stringResource(org.runnerup.common.R.string.Home_nav_exercises),
          onClick = { onSelectDestination(HomeDestination.Exercises) },
          modifier = Modifier.weight(1f))
      HomeNavigationItem(
          selected = false,
          icon = { tint ->
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = tint)
          },
          label = stringResource(org.runnerup.common.R.string.Home_nav_start),
          onClick = onOpenWorkout,
          modifier = Modifier.weight(1f))
      HomeNavigationItem(
          selected = false,
          icon = { tint -> Icon(Icons.Default.History, contentDescription = null, tint = tint) },
          label = stringResource(org.runnerup.common.R.string.Home_nav_history),
          onClick = onOpenHistory,
          modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun HomeNavigationItem(
    selected: Boolean,
    icon: @Composable (Color) -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  val tint = if (selected) RelvenColors.Orange else RelvenColors.MutedText
  Column(
      modifier = modifier.heightIn(min = 64.dp).clickable(role = Role.Tab, onClick = onClick)
          .semantics { this.selected = selected },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center) {
    icon(tint)
    Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun TodayPlanCard(state: TodayPlanState) {
  Surface(
      shape = RoundedCornerShape(24.dp),
      color = RelvenColors.Surface,
      border = BorderStroke(1.dp, RelvenColors.Orange.copy(alpha = 0.25f)),
      modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
          stringResource(org.runnerup.common.R.string.Today_plan_title),
          color = RelvenColors.Orange,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold)
      when (state) {
        TodayPlanState.Loading ->
            Text(
                stringResource(org.runnerup.common.R.string.Today_plan_loading),
                color = RelvenColors.MutedText,
                fontSize = 14.sp)
        TodayPlanState.NoActivePlan -> {
          Text(
              stringResource(org.runnerup.common.R.string.No_active_plan),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold)
          Text(
              stringResource(org.runnerup.common.R.string.Activate_a_plan_hint),
              color = RelvenColors.MutedText,
              fontSize = 14.sp)
        }
        TodayPlanState.Unavailable -> {
          Text(
              stringResource(org.runnerup.common.R.string.Today_plan_unavailable),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold)
          Text(
              stringResource(org.runnerup.common.R.string.Today_plan_unavailable_hint),
              color = RelvenColors.MutedText,
              fontSize = 14.sp)
        }
        is TodayPlanState.NoPlannedItem -> {
          Text(
              stringResource(org.runnerup.common.R.string.No_plan_item_today),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold)
          Text(
              state.planName,
              color = RelvenColors.MutedText,
              fontSize = 14.sp)
        }
        is TodayPlanState.RestDay -> {
          Text(
              stringResource(org.runnerup.common.R.string.Rest_day),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold)
          Text(
              state.planName,
              color = RelvenColors.MutedText,
              fontSize = 14.sp)
          if (state.notes.isNotBlank()) {
            Text(state.notes, color = RelvenColors.MutedText, fontSize = 14.sp)
          }
        }
        is TodayPlanState.PlannedWorkout -> {
          Text(state.workoutName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
          Text(
              stringResource(org.runnerup.common.R.string.Planned_run_in_plan, state.planName),
              color = RelvenColors.MutedText,
              fontSize = 14.sp)
          if (state.notes.isNotBlank()) {
            Text(state.notes, color = RelvenColors.MutedText, fontSize = 14.sp)
          }
        }
      }
    }
  }
}
