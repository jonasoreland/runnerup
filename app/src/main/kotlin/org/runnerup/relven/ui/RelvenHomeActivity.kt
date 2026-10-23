package org.runnerup.relven.ui

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.edit
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
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.runnerup.BuildConfig
import org.runnerup.R
import org.runnerup.common.util.Constants.DB
import org.runnerup.content.ActivityProvider
import org.runnerup.db.DBHelper
import org.runnerup.db.ActivityCleaner
import org.runnerup.db.PathSimplifier
import org.runnerup.workout.RelvenPlanStore
import org.runnerup.workout.RelvenStrengthWorkoutParser
import org.runnerup.workout.RelvenPlanImport
import org.runnerup.workout.RelvenPlanKit
import org.runnerup.workout.RelvenPlanImporter
import org.runnerup.workout.Sport
import org.runnerup.view.StartFragment
import org.runnerup.view.ManualActivity
import org.runnerup.util.FileNameHelper
import org.runnerup.importer.ActivityFileImporter
import org.runnerup.importer.ActivityImportStore
import org.runnerup.importer.ActivityImportException
import org.runnerup.importer.ImportedActivity
import org.runnerup.relven.exercise.ExerciseCatalogRepository
import org.runnerup.relven.exercise.ExerciseFavoritesStore
import org.runnerup.relven.exercise.CustomExerciseInput
import org.runnerup.relven.exercise.CollectionImportResult
import org.runnerup.relven.exercise.readExerciseCollection
import org.runnerup.relven.analysis.ProgressLoadState
import org.runnerup.relven.analysis.ProgressRequestVersion
import org.runnerup.relven.analysis.ProgressSummary
import org.runnerup.relven.analysis.RecordedActivity
import org.runnerup.relven.analysis.ActivityWindowSummary
import org.runnerup.relven.analysis.progressLoadState
import org.runnerup.relven.analysis.summarizeActivities
import org.runnerup.relven.strength.CompletedStrengthSession
import org.runnerup.relven.strength.StrengthSession
import org.runnerup.relven.strength.StrengthSessionStore
import java.util.UUID

/** Production home screen; RunnerUp remains authoritative for workout controls. */
class RelvenHomeActivity : FragmentActivity(), GymWorkoutStarter {
  companion object {
    private const val EXTRA_OPEN_PLAN_LIBRARY = "relven.home.open_plan_library"
    private const val HOME_DESTINATION_STATE = "relven.home.destination"
    private const val PLAN_LIBRARY_OPEN_STATE = "relven.home.plan_library_open"
    private const val HISTORY_ACTIVITY_DETAIL_STATE = "relven.home.history_activity_detail"
    private const val PENDING_PLAN_ACTIVATION_PREFERENCES = "relven.pending-plan-activation"
    private const val PENDING_PLAN_ACTIVATION_KEY = "source_json"

    @JvmStatic
    fun openPlanLibraryIntent(context: Context): Intent =
        Intent(context, RelvenHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_PLAN_LIBRARY, true)
  }

  private var recentActivity by mutableStateOf<RecentActivity?>(null)
  private var progressState by mutableStateOf<ProgressLoadState>(ProgressLoadState.Loading)
  private val progressRequests = ProgressRequestVersion()
  private var sessionStatus by mutableStateOf<HomeSessionStatus?>(null)
  private var todayPlan by mutableStateOf<TodayPlanState>(TodayPlanState.Loading)
  private var activePlanState by mutableStateOf<ActivePlanLoadState>(ActivePlanLoadState.Loading)
  private var planLibraryState by mutableStateOf(PlanLibraryState())
  private var planLibraryMessage by mutableStateOf<String?>(null)
  private var pendingPlanImport by mutableStateOf<PendingPlanImport?>(null)
  private var planLibraryBusy by mutableStateOf(false)
  private var planCreationOpen by mutableStateOf(false)
  private var planCreationError by mutableStateOf<String?>(null)
  private var pendingPlanActivationJson by mutableStateOf<String?>(null)
  private var planRevisionStatus by mutableStateOf<PlanRevisionStatus>(PlanRevisionStatus.Idle)
  private var favoriteWorkouts by mutableStateOf<List<FavoriteWorkout>>(emptyList())
  private var exerciseCatalogLoadState by mutableStateOf<ExerciseCatalogLoadState>(ExerciseCatalogLoadState.Loading)
  private var exerciseCollectionMessage by mutableStateOf<String?>(null)
  private var exerciseCollectionBusy by mutableStateOf(false)
  private val exerciseCatalogMutex = Mutex()
  private var favoriteExerciseIds by mutableStateOf<Set<String>>(emptySet())
  private var exerciseProgressionState by mutableStateOf<ExerciseProgressionLoadState>(ExerciseProgressionLoadState.Loading)
  private var currentTimeMillis by mutableLongStateOf(System.currentTimeMillis())
  private var destination by mutableStateOf(HomeDestination.Today)
  private var planLibraryOpen by mutableStateOf(false)
  private var activityHistoryState by mutableStateOf<ActivityHistoryLoadState>(ActivityHistoryLoadState.Loading)
  private var activityHistoryRequest = 0
  private var activityDetailId by mutableStateOf<Long?>(null)
  private var activityDetailState by mutableStateOf<ActivityDetailLoadState?>(null)
  private var activityEditSaveState by mutableStateOf<ActivityEditSaveState>(ActivityEditSaveState.Idle)
  private var activityDetailRequest = 0
  private var interruptedRecoveryInProgress by mutableStateOf(false)
  private var trackerSessionBinding: HomeTrackerSessionBinding? = null
  private var strengthSession by mutableStateOf<StrengthSession?>(null)
  private var viewedStrengthCompletion by mutableStateOf<CompletedStrengthSession?>(null)
  private var completedStrengthSessions by mutableStateOf<Map<String, CompletedStrengthSession>>(emptyMap())
  private var strengthSaveError by mutableStateOf(false)
  private var strengthTransitionSaving by mutableStateOf(false)
  private var pendingStrengthSessionChoice by mutableStateOf<PendingStrengthSessionChoice?>(null)
  private var strengthRecoveryNotice by mutableStateOf(false)
  private var strengthFinishing by mutableStateOf(false)
  private var pendingPlannedRunningWorkout by mutableStateOf<org.runnerup.workout.Workout?>(null)
  private val strengthStoreMutex = Mutex()
  private var pendingActivityImport by mutableStateOf<PendingActivityImport?>(null)
  private var activityImportMessage by mutableStateOf<String?>(null)
  private lateinit var activityDocumentPicker: ActivityResultLauncher<Array<String>>
  private lateinit var exerciseCollectionImportPicker: ActivityResultLauncher<Array<String>>
  private lateinit var exerciseCollectionExportPicker: ActivityResultLauncher<String>
  private lateinit var planImportPicker: ActivityResultLauncher<Array<String>>

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    activityDocumentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
          if (uri != null) readActivityImport(uri)
        }
    exerciseCollectionImportPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
          if (uri != null) importExerciseCollection(uri) else exerciseCollectionBusy = false
        }
    exerciseCollectionExportPicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
          if (uri != null) exportExerciseCollection(uri) else exerciseCollectionBusy = false
        }
    planImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) readPlanImport(uri)
    }
    val openPlanLibrary = intent.getBooleanExtra(EXTRA_OPEN_PLAN_LIBRARY, false)
    if (openPlanLibrary) intent.removeExtra(EXTRA_OPEN_PLAN_LIBRARY)
    destination =
        homeDestinationFromRequest(
            savedInstanceState?.getString(HOME_DESTINATION_STATE), openPlanLibrary)
    planLibraryOpen =
        openPlanLibrary || (savedInstanceState?.getBoolean(PLAN_LIBRARY_OPEN_STATE) ?: false)
    pendingPlanActivationJson = savedInstanceState?.getString("relven.home.pending_plan_activation")
        ?: getSharedPreferences(PENDING_PLAN_ACTIVATION_PREFERENCES, MODE_PRIVATE)
            .getString(PENDING_PLAN_ACTIVATION_KEY, null)
    planCreationOpen = savedInstanceState?.getBoolean("relven.home.plan_creation_open")
        ?: (pendingPlanActivationJson != null)
    if (pendingPlanActivationJson != null) {
      planCreationError = getString(R.string.plan_create_activation_error)
    }
    activityDetailId =
        savedInstanceState?.takeIf { it.containsKey(HISTORY_ACTIVITY_DETAIL_STATE) }
            ?.getLong(HISTORY_ACTIVITY_DETAIL_STATE)
    favoriteExerciseIds = ExerciseFavoritesStore(applicationContext).read()
    loadFavoriteWorkouts()
    loadExerciseCatalog()
    setContent {
      RelvenTheme {
        pendingActivityImport?.let { pending ->
          ActivityImportReviewDialog(
              pending = pending,
              onDismiss = { pendingActivityImport = null },
              onConfirm = { saveActivityImport(pending) })
        }
        activityImportMessage?.let { message ->
          AlertDialog(
              onDismissRequest = { activityImportMessage = null },
              text = { Text(message) },
              confirmButton = {
                TextButton(onClick = { activityImportMessage = null }) {
                  Text(stringResource(org.runnerup.common.R.string.Home_import_ok))
                }
              })
        }
        exerciseCollectionMessage?.let { message ->
          AlertDialog(
              onDismissRequest = { exerciseCollectionMessage = null },
              text = { Text(message) },
              confirmButton = {
                TextButton(onClick = { exerciseCollectionMessage = null }) {
                  Text(stringResource(org.runnerup.common.R.string.Home_import_ok))
                }
              })
        }
        pendingPlanImport?.let { pending ->
          PlanImportReviewDialog(
              pending = pending,
              busy = planLibraryBusy,
              onDismiss = { pendingPlanImport = null },
              onSave = { savePlanImport(pending) })
        }
        planLibraryMessage?.let { message ->
          AlertDialog(
              onDismissRequest = { planLibraryMessage = null },
              text = { Text(message) },
              confirmButton = {
                TextButton(onClick = { planLibraryMessage = null }) {
                  Text(stringResource(org.runnerup.common.R.string.Home_import_ok))
                }
              })
        }
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
        } else if (activityDetailState != null) {
          ActivityDetailScreen(
              loadState = requireNotNull(activityDetailState),
              editSaveState = activityEditSaveState,
              recoveryActionInProgress = interruptedRecoveryInProgress,
              onBack = ::closeHistoryActivityDetail,
              onRetry = { activityDetailId?.let(::loadHistoryActivityDetail) },
              onOpenMap = ::openHistoryActivityMap,
              onBeginEdit = { activityEditSaveState = ActivityEditSaveState.Idle },
              onSaveActivityEdit = ::saveHistoryActivityEdit,
              onShareActivity = ::shareHistoryActivity,
              onOpenMoreActions = ::openHistoryActivityUpload,
              onSaveInterruptedActivity = ::saveInterruptedHistoryActivity,
              onDiscardInterruptedActivity = ::discardInterruptedHistoryActivity)
        } else RelvenHome(
            recentActivity = recentActivity,
            sessionStatus = sessionStatus,
            todayPlan = todayPlan,
            activePlanState = activePlanState,
            exerciseCatalogLoadState = exerciseCatalogLoadState,
            exerciseProgressionState = exerciseProgressionState,
            progressState = progressState,
            activityHistoryState = activityHistoryState,
            planRevisionStatus = planRevisionStatus,
            currentTimeMillis = currentTimeMillis,
            pendingPlannedRunningWorkout = pendingPlannedRunningWorkout,
            destination = destination,
            startFragmentManager = supportFragmentManager,
            onSelectDestination = ::selectHomeDestination,
            onRetryProgress = ::loadProgress,
            onRetryHistory = ::loadActivityHistory,
            onAddActivity = { startActivity(Intent(this, ManualActivity::class.java)) },
            onImportActivity = { activityDocumentPicker.launch(arrayOf("*/*")) },
            onOpenActivity = ::openHistoryActivity,
            onOpenActiveSession = { startActivity(runActivityIntent(this)) },
            onOpenWorkout = { selectHomeDestination(HomeDestination.Start) },
            planLibraryState = planLibraryState,
            favoriteWorkouts = favoriteWorkouts,
            planLibraryOpen = planLibraryOpen,
            planLibraryBusy = planLibraryBusy,
            planCreationOpen = planCreationOpen,
            planCreationError = planCreationError,
            onRetryPlanLibrary = ::loadPlanLibrary,
            onImportPlan = { planImportPicker.launch(arrayOf("application/json", "text/json", "*/*")) },
            onOpenPlanCreator = {
              planCreationError = null
              planCreationOpen = true
            },
            onClosePlanCreator = {
              planCreationError = null
              planCreationOpen = false
              planLibraryOpen = pendingPlanActivationJson != null
            },
            onPlanDraftChanged = { planCreationError = null },
            onCreatePlan = ::createPlan,
            planActivationPending = pendingPlanActivationJson != null,
            onRetryPlanActivation = ::retryPendingPlanActivation,
            onCreatePlanWithAi = ::copyPlanKitInstructions,
            onActivatePlan = ::activateSavedPlan,
            onClearActivePlan = ::clearActivePlan,
            onArchivePlan = ::archiveSavedPlan,
            onRestoreArchivedPlan = ::restoreArchivedPlan,
            onOpenPlanLibrary = {
              planLibraryOpen = true
              selectHomeDestination(HomeDestination.Plan)
            },
            onOpenPlanCalendar = {
              planLibraryOpen = false
              selectHomeDestination(HomeDestination.Plan)
            },
            onOpenHistory = {
              selectHomeDestination(HomeDestination.History)
            },
            onReschedulePlan = ::rescheduleActivePlan,
            onAddWorkoutToPlan = ::addWorkoutToActivePlan,
            onAddRestToPlan = ::addRestToActivePlan,
            onRemovePlanEntry = ::removeActivePlanEntry,
            onRenamePlan = ::renameActivePlan,
            onRenameWorkout = ::renameActivePlanWorkout,
            onSaveFavoriteWorkout = ::saveFavoriteWorkout,
            onStartFavoriteWorkout = ::startFavoriteWorkout,
            onRemoveFavoriteWorkout = ::removeFavoriteWorkout,
            onEditRunningEnd = ::editActivePlanRunningEnd,
            onEditRunningTarget = ::editActivePlanRunningTarget,
            onRetryExerciseCatalog = ::loadExerciseCatalog,
            onSaveCustomExercise = ::saveCustomExercise,
            onSetCustomExerciseArchived = ::setCustomExerciseArchived,
            onImportCustomExercises = ::chooseExerciseCollectionImport,
            onExportCustomExercises = ::chooseExerciseCollectionExport,
            exerciseCollectionBusy = exerciseCollectionBusy,
            favoriteExerciseIds = favoriteExerciseIds,
            onToggleExerciseFavorite = ::toggleExerciseFavorite,
            onRetryExerciseProgression = ::reloadExerciseProgression,
            completedStrengthSessions = completedStrengthSessions,
            onStartStrength = ::startStrengthSession,
            onViewStrengthCompletion = { viewedStrengthCompletion = it },
            onStartPlannedRunningWorkout = ::startPlannedRunningWorkout,
            strengthRecoveryNotice = strengthRecoveryNotice,
            onDismissStrengthRecoveryNotice = { strengthRecoveryNotice = false },
            pendingStrengthSessionChoice = pendingStrengthSessionChoice,
            onContinueExistingStrengthSession = ::continueExistingStrengthSession,
            onCancelExistingStrengthSession = { pendingStrengthSessionChoice = null })
      }
    }
    activityDetailId?.let(::loadHistoryActivityDetail)
  }

  override fun onStart() {
    super.onStart()
    trackerSessionBinding =
        HomeTrackerSessionBinding(this) { status ->
          runOnUiThread { sessionStatus = status }
        }.also { it.start() }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.getBooleanExtra(EXTRA_OPEN_PLAN_LIBRARY, false)) {
      intent.removeExtra(EXTRA_OPEN_PLAN_LIBRARY)
      setIntent(intent)
      selectHomeDestination(HomeDestination.Plan)
      planLibraryOpen = true
      planCreationOpen = false
    } else {
      setIntent(intent)
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString(HOME_DESTINATION_STATE, destination.name)
    outState.putBoolean(PLAN_LIBRARY_OPEN_STATE, planLibraryOpen)
    outState.putBoolean("relven.home.plan_creation_open", planCreationOpen)
    pendingPlanActivationJson?.let { outState.putString("relven.home.pending_plan_activation", it) }
    activityDetailId?.let { outState.putLong(HISTORY_ACTIVITY_DETAIL_STATE, it) }
    super.onSaveInstanceState(outState)
  }

  override fun onResume() {
    super.onResume()
    if (destination == HomeDestination.History) loadActivityHistory()
    activityDetailId?.let { activityId ->
      if (activityDetailState is ActivityDetailLoadState.Loaded) {
        loadHistoryActivityDetail(activityId)
      }
    }
    todayPlan = TodayPlanState.Loading
    activePlanState = ActivePlanLoadState.Loading
    planLibraryState = PlanLibraryState()
    loadPlanLibrary()
    loadFavoriteWorkouts()
    planRevisionStatus = PlanRevisionStatus.Idle
    currentTimeMillis = System.currentTimeMillis()
    progressState = ProgressLoadState.Loading
    val progressRequest = progressRequests.begin()
    val progressAsOfMillis = currentTimeMillis
    lifecycleScope.launch {
      val homeData =
          withContext(Dispatchers.IO) {
            val planState = loadActivePlan()
            val plan = (planState as? ActivePlanLoadState.Loaded)?.plan
            val strengthHistory =
                try {
                  strengthStoreMutex.withLock {
                    StrengthSessionStore.completedSessions(applicationContext)
                  }
                } catch (error: Exception) {
                  Log.w("RelvenHome", "Could not load strength history", error)
                  null
                }
            val completions = buildMap<String, CompletedStrengthSession> {
              strengthHistory?.sessions?.forEach { completion ->
                val scheduledId = completion.state.scheduledEntryId
                val current = get(scheduledId)
                if (current == null || completion.finishedAtMillis > current.finishedAtMillis) {
                  put(scheduledId, completion)
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
                completions,
                strengthHistory?.let(::loadedExerciseProgressionState)
                    ?: ExerciseProgressionLoadState.Unavailable,
                runCatching { loadProgressSummary(progressAsOfMillis) })
          }
      recentActivity = homeData.recentActivity
      activePlanState = homeData.activePlan
      completedStrengthSessions = homeData.completedStrengthSessions
      exerciseProgressionState = homeData.exerciseProgressionState
      if (progressRequests.isCurrent(progressRequest)) {
        progressState = progressLoadState(homeData.progressSummary)
      }
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

  private fun selectHomeDestination(next: HomeDestination) {
    if (next != HomeDestination.Start) pendingPlannedRunningWorkout = null
    if (next != HomeDestination.Plan) {
      planLibraryOpen = false
      planCreationOpen = false
      planCreationError = null
    }
    if (destination == HomeDestination.Start && next != HomeDestination.Start) {
      val startFragment =
          supportFragmentManager.findFragmentById(org.runnerup.R.id.relven_start_fragment_container)
      if (startFragment != null) {
        if (supportFragmentManager.isStateSaved) {
          supportFragmentManager.beginTransaction().remove(startFragment).commitAllowingStateLoss()
        } else {
          supportFragmentManager.commitNow { remove(startFragment) }
        }
      }
    }
    destination = next
    if (next == HomeDestination.History) loadActivityHistory()
  }

  private fun openHistoryActivity(id: Long) {
    activityDetailId = id
    loadHistoryActivityDetail(id)
  }

  private fun loadPlanLibrary() {
    planLibraryState = PlanLibraryState()
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val plans = RelvenPlanStore.list(applicationContext)
          val active = RelvenPlanStore.activePlan(applicationContext)
          PlanLibraryState(
              loading = false,
              plans = plans,
              activePlanId = active?.id(),
              archivedRootPlanIds = RelvenPlanStore.archivedPlanRootIds(applicationContext))
        }
      }
      planLibraryState = result.getOrElse {
        Log.w("RelvenHome", "Could not load saved plans", it)
        PlanLibraryState(loading = false, unavailable = true)
      }
    }
  }

  private fun loadFavoriteWorkouts() {
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching { FavoriteWorkoutStore.read(applicationContext) }
      }
      favoriteWorkouts = result.getOrElse { error ->
        Log.w("RelvenHome", "Could not load favorite workouts", error)
        planLibraryMessage = getString(R.string.plan_favorite_error)
        emptyList()
      }
    }
  }

  private fun saveFavoriteWorkout(name: String, workoutJson: String) {
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          FavoriteWorkoutStore.save(applicationContext, name, workoutJson)
          FavoriteWorkoutStore.read(applicationContext)
        }
      }
      result.fold(
          onSuccess = { savedFavorites ->
            favoriteWorkouts = savedFavorites
            planLibraryMessage = getString(R.string.plan_favorite_saved)
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not save favorite workout", error)
            planLibraryMessage = getString(R.string.plan_favorite_error)
          })
    }
  }

  private fun removeFavoriteWorkout(id: String) {
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          FavoriteWorkoutStore.remove(applicationContext, id)
          FavoriteWorkoutStore.read(applicationContext)
        }
      }
      result.fold(
          onSuccess = { favoriteWorkouts = it },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not remove favorite workout", error)
            planLibraryMessage = getString(R.string.plan_favorite_error)
          })
    }
  }

  private fun startFavoriteWorkout(favorite: FavoriteWorkout) {
    try {
      val document = org.json.JSONObject(favorite.workoutJson)
      when (favorite.sport) {
        "running" -> startPlannedRunningWorkout(
            org.runnerup.workout.RelvenWorkoutImporter.parse(favorite.workoutJson))
        "strength", "prehab" -> {
          val workout = org.runnerup.workout.RelvenStrengthWorkoutParser.parse(favorite.workoutJson)
          val entry = PlanCalendarEntry.Workout(
              favorite.id,
              document.getString("id"),
              favorite.name,
              "",
              strengthDetails = workout,
              workoutDocument = favorite.workoutJson)
          startStrengthSession("favorite-workout", entry)
        }
        else -> throw IllegalArgumentException("Unsupported favorite workout type")
      }
    } catch (error: Exception) {
      Log.w("RelvenHome", "Could not start favorite workout", error)
      planLibraryMessage = getString(R.string.plan_favorite_error)
    }
  }

  private fun readPlanImport(uri: Uri) {
    planLibraryBusy = true
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val source = contentResolver.openInputStream(uri).use { RelvenPlanImport.read(it) }
          val plan = RelvenPlanStore.parse(applicationContext, source)
          PendingPlanImport(plan, RelvenPlanImport.preview(plan))
        }
      }
      planLibraryBusy = false
      result.fold(
          onSuccess = { pendingPlanImport = it },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not read plan import", error)
            planLibraryMessage = getString(R.string.plan_library_import_error)
          })
    }
  }

  private fun savePlanImport(pending: PendingPlanImport) {
    planLibraryBusy = true
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching { RelvenPlanStore.save(applicationContext, pending.plan) }
      }
      planLibraryBusy = false
      pendingPlanImport = null
      result.fold(
          onSuccess = {
            loadPlanLibrary()
            planLibraryMessage = getString(R.string.plan_library_import_success)
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not save imported plan", error)
            planLibraryMessage = getString(R.string.plan_library_import_error)
          })
    }
  }

  private fun activateSavedPlan(plan: RelvenPlanImporter.Plan) {
    planLibraryBusy = true
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching { RelvenPlanStore.activate(applicationContext, plan) }
      }
      planLibraryBusy = false
      result.fold(
          onSuccess = {
            loadPlanLibrary()
            refreshActivePlan()
            planLibraryMessage = getString(R.string.plan_library_active_success)
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not activate plan", error)
            planLibraryMessage = getString(R.string.plan_library_error)
          })
    }
  }

  private fun clearActivePlan() {
    planLibraryBusy = true
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching { RelvenPlanStore.clearActivePlan(applicationContext) }
      }
      planLibraryBusy = false
      result.fold(
          onSuccess = {
            loadPlanLibrary()
            refreshActivePlan()
            planLibraryMessage = getString(R.string.plan_library_active_success)
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not clear active plan", error)
            planLibraryMessage = getString(R.string.plan_library_error)
          })
    }
  }

  private fun archiveSavedPlan(rootPlanId: String) {
    setSavedPlanArchived(rootPlanId, archived = true)
  }

  private fun restoreArchivedPlan(rootPlanId: String) {
    setSavedPlanArchived(rootPlanId, archived = false)
  }

  private fun setSavedPlanArchived(rootPlanId: String, archived: Boolean) {
    if (planLibraryBusy) return
    planLibraryBusy = true
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          if (archived) RelvenPlanStore.archivePlan(applicationContext, rootPlanId)
          else RelvenPlanStore.restoreArchivedPlan(applicationContext, rootPlanId)
        }
      }
      planLibraryBusy = false
      result.fold(
          onSuccess = {
            loadPlanLibrary()
            planLibraryMessage = getString(
                if (archived) R.string.plan_library_archive_success
                else R.string.plan_library_restore_success)
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not update saved plan archive state", error)
            planLibraryMessage = getString(R.string.plan_library_archive_error)
          })
    }
  }

  private fun createPlan(
      name: String,
      startDate: String,
      weeks: Int,
      sessions: List<PlanDraftSession>,
      strengthExercises: List<PlanDraftStrengthExercise>) {
    planLibraryBusy = true
    planCreationError = null
    val workoutCopy = localizedPlanWorkoutCopy()
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val usesStrength = sessions.any {
            it.workoutType == PlanWorkoutType.Strength || it.workoutType == PlanWorkoutType.Prehab
          }
          val references =
              if (usesStrength) ExerciseCatalogRepository(applicationContext).exerciseReferences()
              else emptyMap()
          PlanDraftBuilder.create(
              name = name,
              startDate = startDate,
              weeks = weeks,
              sessions = sessions,
              timezone = java.util.TimeZone.getDefault().id,
              workoutCopy = workoutCopy,
              strengthExercises = strengthExercises,
              exerciseReferences = references)
        }
      }
      val source = result.getOrElse { error ->
        Log.w("RelvenHome", "Could not create plan", error)
        planLibraryBusy = false
        planCreationError = getString(R.string.plan_create_save_error)
        return@launch
      }
      if (!storePendingPlanActivation(source)) {
        planLibraryBusy = false
        planCreationError = getString(R.string.plan_create_save_error)
        return@launch
      }
      finishPendingPlanActivation(source)
    }
  }

  private fun retryPendingPlanActivation() {
    val sourceJson = pendingPlanActivationJson ?: return
    planLibraryBusy = true
    planCreationError = null
    lifecycleScope.launch { finishPendingPlanActivation(sourceJson) }
  }

  // Persist before saving so a process death during the save can recover the exact plan.
  @Suppress("ApplySharedPref", "UseKtx")
  private fun storePendingPlanActivation(sourceJson: String): Boolean {
    val stored = getSharedPreferences(PENDING_PLAN_ACTIVATION_PREFERENCES, MODE_PRIVATE)
        .edit()
        .putString(PENDING_PLAN_ACTIVATION_KEY, sourceJson)
        .commit()
    if (stored) pendingPlanActivationJson = sourceJson
    return stored
  }

  private suspend fun finishPendingPlanActivation(sourceJson: String) {
    val result = withContext(Dispatchers.IO) {
      runCatching {
        val parsed = RelvenPlanStore.parse(applicationContext, sourceJson)
        val stored = RelvenPlanStore.list(applicationContext).firstOrNull { it.id() == parsed.id() }
        val plan = when (pendingPlanSaveAction(
            parsed.id(), parsed.sourceJson(), stored?.id(), stored?.sourceJson())) {
          PendingPlanSaveAction.Save -> parsed.also { RelvenPlanStore.save(applicationContext, it) }
          PendingPlanSaveAction.Reuse -> requireNotNull(stored)
          PendingPlanSaveAction.Conflict -> throw IOException("A different plan already uses this identifier")
        }
        RelvenPlanStore.activate(applicationContext, plan)
      }
    }
    planLibraryBusy = false
    result.fold(
        onSuccess = {
          getSharedPreferences(PENDING_PLAN_ACTIVATION_PREFERENCES, MODE_PRIVATE).edit {
            remove(PENDING_PLAN_ACTIVATION_KEY)
          }
          pendingPlanActivationJson = null
          planCreationOpen = false
          planLibraryOpen = false
          loadPlanLibrary()
          refreshActivePlan()
        },
        onFailure = { error ->
          Log.w("RelvenHome", "Could not save and activate plan", error)
          planCreationOpen = true
          planCreationError = getString(R.string.plan_create_activation_error)
          loadPlanLibrary()
        })
  }

  private fun localizedPlanWorkoutCopy(): Map<PlanWorkoutType, PlanWorkoutCopy> {
    fun copy(name: String) = PlanWorkoutCopy(
        name = name,
        warmup = getString(R.string.plan_create_warmup_step),
        easy = getString(R.string.plan_create_easy_step),
        effort = getString(R.string.plan_create_effort_step),
        recovery = getString(R.string.plan_create_recovery_step),
        cooldown = getString(R.string.plan_create_cooldown_step),
        longRun = getString(R.string.plan_create_long_run_step))
    return mapOf(
        PlanWorkoutType.Easy to copy(getString(R.string.plan_create_easy_name)),
        PlanWorkoutType.Intervals to copy(getString(R.string.plan_create_intervals_name)),
        PlanWorkoutType.LongRun to copy(getString(R.string.plan_create_long_run_name)),
        PlanWorkoutType.Strength to copy(getString(R.string.plan_create_strength_name)),
        PlanWorkoutType.Prehab to copy(getString(R.string.plan_create_prehab_name)))
  }

  private fun refreshActivePlan() {
    lifecycleScope.launch {
      val plan = withContext(Dispatchers.IO) { runCatching { loadActivePlan() }.getOrNull() }
          ?: ActivePlanLoadState.Unavailable
      activePlanState = plan
      todayPlan = when (plan) {
        ActivePlanLoadState.Loading -> TodayPlanState.Loading
        ActivePlanLoadState.NoActivePlan -> TodayPlanState.NoActivePlan
        ActivePlanLoadState.Unavailable -> TodayPlanState.Unavailable
        is ActivePlanLoadState.Loaded -> todayPlanState(plan.plan, System.currentTimeMillis())
      }
    }
  }

  private fun copyPlanKitInstructions() {
    planLibraryBusy = true
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching { RelvenPlanKit.readInstructions(applicationContext) }
      }
      planLibraryBusy = false
      result.fold(
          onSuccess = { instructions ->
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Relven Plan Kit", instructions))
            planLibraryMessage = getString(R.string.plan_library_ai_copied)
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not read Plan Kit instructions", error)
            planLibraryMessage = getString(R.string.plan_library_ai_error)
          })
    }
  }

  private fun closeHistoryActivityDetail() {
    activityDetailRequest++
    activityDetailId = null
    activityDetailState = null
  }

  private fun saveInterruptedHistoryActivity(id: Long, note: String?) {
    if (interruptedRecoveryInProgress) return
    interruptedRecoveryInProgress = true
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              saveInterruptedActivity(DBHelper.getWritableDatabase(applicationContext), id, note)
            }
          }
      val saved = result.getOrElse { error ->
        Log.e("RelvenHome", "Could not save interrupted activity", error)
        false
      }
      if (saved) {
        loadHistoryActivityDetail(id)
        loadActivityHistory()
        recentActivity = withContext(Dispatchers.IO) { loadRecentActivity() }
      } else {
        android.widget.Toast.makeText(
                this@RelvenHomeActivity,
                org.runnerup.common.R.string.activity_recovery_save_failed,
                android.widget.Toast.LENGTH_LONG)
            .show()
      }
      interruptedRecoveryInProgress = false
    }
  }

  private fun discardInterruptedHistoryActivity(id: Long) {
    if (interruptedRecoveryInProgress) return
    interruptedRecoveryInProgress = true
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              discardInterruptedActivity(DBHelper.getWritableDatabase(applicationContext), id)
            }
          }
      val discarded = result.getOrElse { error ->
        Log.e("RelvenHome", "Could not discard interrupted activity", error)
        false
      }
      if (discarded) {
        closeHistoryActivityDetail()
        loadActivityHistory()
        recentActivity = withContext(Dispatchers.IO) { loadRecentActivity() }
      } else {
        android.widget.Toast.makeText(
                this@RelvenHomeActivity,
                org.runnerup.common.R.string.activity_recovery_discard_failed,
                android.widget.Toast.LENGTH_LONG)
            .show()
      }
      interruptedRecoveryInProgress = false
    }
  }

  private fun openHistoryActivityMap(id: Long) {
    val activity = (activityDetailState as? ActivityDetailLoadState.Loaded)?.activity
    val mapAvailable = BuildConfig.OSMDROID_ENABLED || BuildConfig.MAPBOX_ENABLED
    if (activity != null && activityHasMapRoute(activity, mapEnabled = mapAvailable)) {
      startActivity(ActivityMapActivity.intent(this, id))
    }
  }

  private fun openHistoryActivityUpload(id: Long) {
    startActivity(ActivityUploadActivity.intent(this, id))
  }

  private fun shareHistoryActivity(activity: HistoryActivityDetails, format: ActivityShareFormat) {
    val activityType = Sport.textOf(resources, activity.sport)
    val fileName =
        FileNameHelper.getExportFileName(activity.startedAtSeconds, activityType) + format.extension
    val target = activityShareTarget(ActivityProvider.AUTHORITY, activity.id, fileName, format)
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = target.mimeType
          putExtra(Intent.EXTRA_STREAM, target.uri)
          clipData = ClipData.newUri(contentResolver, fileName, target.uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(
        Intent.createChooser(shareIntent, getString(org.runnerup.common.R.string.Share_activity)))
  }

  private fun saveHistoryActivityEdit(command: ActivityEditCommand) {
    if (activityEditSaveState is ActivityEditSaveState.Saving) return
    activityEditSaveState = ActivityEditSaveState.Saving
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              val database = DBHelper.getWritableDatabase(applicationContext)
              if (!saveHistoryActivityEdit(database, command)) return@runCatching false
              try {
                val simplifier = PathSimplifier.getPathSimplifierForSave(applicationContext)
                if (simplifier != null) {
                  val ids = simplifier.getNoisyLocationIDsAsStrings(database, command.activityId)
                  ActivityCleaner.deleteLocations(database, ids)
                  ActivityCleaner().recompute(database, command.activityId)
                }
              } catch (error: Exception) {
                Log.e("RelvenHome", "Failed to simplify edited activity path", error)
              }
              true
            }
          }
      if (activityDetailId != command.activityId) return@launch
      val saved = result.getOrElse { error ->
        Log.e("RelvenHome", "Could not save activity edit", error)
        false
      }
      if (saved) {
        activityEditSaveState = ActivityEditSaveState.Saved
        loadHistoryActivityDetail(command.activityId)
      } else {
        activityEditSaveState = ActivityEditSaveState.Failed
      }
    }
  }

  private fun loadHistoryActivityDetail(id: Long) {
    val request = ++activityDetailRequest
    activityDetailState = ActivityDetailLoadState.Loading
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              val database: SQLiteDatabase = DBHelper.getReadableDatabase(applicationContext)
              readHistoryActivityDetails(database, id)
            }
          }
      if (request == activityDetailRequest && activityDetailId == id) {
        activityDetailState =
            result.fold(
                onSuccess = { activity ->
                  activity?.let(ActivityDetailLoadState::Loaded)
                      ?: ActivityDetailLoadState.NotFound
                },
                onFailure = { error ->
                  Log.w("RelvenHome", "Could not load activity details", error)
                  ActivityDetailLoadState.Unavailable
                })
      }
    }
  }

  private fun readActivityImport(uri: Uri) {
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              val bytes =
                  contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                      val count = input.read(buffer)
                      if (count < 0) break
                      total += count
                      require(total <= 10 * 1024 * 1024) {
                        "Activity file is larger than 10 MB"
                      }
                      output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                  } ?: error("Unable to open selected file")
              val name =
                  contentResolver
                      .query(
                          uri,
                          arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                          null,
                          null,
                          null)
                      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                      ?: "activity"
              PendingActivityImport(
                  ActivityFileImporter.read(name, bytes.inputStream()),
                  ActivityImportStore.sha256(bytes),
                  name)
            }
          }
      result.fold(
          onSuccess = { pendingActivityImport = it },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not read activity import", error)
            activityImportMessage =
                if (error is ActivityImportException &&
                    error.reason == ActivityImportException.Reason.MULTIPLE_ROUTE_SEGMENTS) {
                  getString(org.runnerup.common.R.string.Home_import_multiple_segments)
                } else {
                  getString(org.runnerup.common.R.string.Home_import_failed)
                }
          })
    }
  }

  private fun saveActivityImport(pending: PendingActivityImport) {
    pendingActivityImport = null
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              ActivityImportStore.importActivity(
                  applicationContext, pending.activity, pending.sha256)
            }
          }
      result.fold(
          onSuccess = { imported ->
            activityImportMessage =
                getString(
                    if (imported == ActivityImportStore.Result.ALREADY_IMPORTED)
                      org.runnerup.common.R.string.Home_import_duplicate
                    else org.runnerup.common.R.string.Home_import_success)
            if (imported == ActivityImportStore.Result.IMPORTED) loadActivityHistory()
          },
          onFailure = { error ->
            Log.w("RelvenHome", "Could not save imported activity", error)
            activityImportMessage = getString(org.runnerup.common.R.string.Home_import_failed)
          })
    }
  }

  private fun loadActivityHistory() {
    val request = ++activityHistoryRequest
    activityHistoryState = ActivityHistoryLoadState.Loading
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              val database: SQLiteDatabase = DBHelper.getReadableDatabase(applicationContext)
              readHistoryActivities(database)
            }
          }
      if (request == activityHistoryRequest) {
        activityHistoryState =
            result.fold(
                onSuccess = { ActivityHistoryLoadState.Loaded(it) },
                onFailure = { error ->
                  Log.w("RelvenHome", "Could not load activity history", error)
                  ActivityHistoryLoadState.Unavailable
                })
      }
    }
  }

  private fun startStrengthSession(planId: String, entry: PlanCalendarEntry.Workout) {
    val workout = entry.strengthDetails ?: return
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

  private fun startPlannedRunningWorkout(workout: org.runnerup.workout.Workout) {
    pendingPlannedRunningWorkout = workout
    selectHomeDestination(HomeDestination.Start)
  }

  override fun startQuickStrengthWorkout(name: String, workoutJson: String) {
    val workout =
        try {
          RelvenStrengthWorkoutParser.parse(workoutJson)
        } catch (error: Exception) {
          Log.w("RelvenHome", "Could not validate custom gym workout", error)
          strengthSaveError = true
          return
        }
    lifecycleScope.launch {
      try {
        val existing = withContext(Dispatchers.IO) {
          strengthStoreMutex.withLock {
            val saved = StrengthSessionStore.loadInProgress(applicationContext)
            if (saved != null) saved
            else StrengthSession.start("quick-workout", UUID.randomUUID().toString(), workout).also {
              StrengthSessionStore.saveInProgress(applicationContext, it)
            }
          }
        }
        if (existing.workout.id() == workout.id()) {
          strengthSaveError = false
          strengthSession = existing
          viewedStrengthCompletion = null
        } else {
          pendingStrengthSessionChoice = PendingStrengthSessionChoice(existing, name)
        }
      } catch (error: Exception) {
        Log.w("RelvenHome", "Could not start custom gym workout", error)
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
        reloadExerciseProgression()
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
          exerciseCatalogMutex.withLock {
            withContext(Dispatchers.IO) {
              loadExerciseCatalogState(
                  { ExerciseCatalogRepository(applicationContext).load(languageTag) }) { error ->
                    Log.w("RelvenHome", "Could not load the bundled exercise catalog", error)
                  }
            }
          }
    }
  }

  private suspend fun saveCustomExercise(exerciseId: String?, input: CustomExerciseInput): String {
    val savedId = exerciseCatalogMutex.withLock {
      withContext(Dispatchers.IO) {
        val repository = ExerciseCatalogRepository(applicationContext)
        if (exerciseId == null) repository.createCustomExercise(input)
        else {
          repository.updateCustomExercise(exerciseId, input)
          exerciseId
        }
      }
    }
    loadExerciseCatalog()
    return savedId
  }

  private suspend fun setCustomExerciseArchived(exerciseId: String, archived: Boolean) {
    exerciseCatalogMutex.withLock {
      withContext(Dispatchers.IO) {
        ExerciseCatalogRepository(applicationContext).setCustomExerciseArchived(exerciseId, archived)
      }
    }
    loadExerciseCatalog()
  }

  private fun chooseExerciseCollectionImport() {
    if (exerciseCollectionBusy) return
    exerciseCollectionBusy = true
    try {
      exerciseCollectionImportPicker.launch(arrayOf("application/json", "text/json", "text/plain"))
    } catch (error: Exception) {
      exerciseCollectionBusy = false
      exerciseCollectionMessage = getString(org.runnerup.common.R.string.Exercise_collection_import_error)
    }
  }

  private fun chooseExerciseCollectionExport() {
    if (exerciseCollectionBusy) return
    exerciseCollectionBusy = true
    try {
      exerciseCollectionExportPicker.launch("relven-custom-exercises.json")
    } catch (error: Exception) {
      exerciseCollectionBusy = false
      exerciseCollectionMessage = getString(org.runnerup.common.R.string.Exercise_collection_export_error)
    }
  }

  private fun importExerciseCollection(uri: Uri) {
    lifecycleScope.launch {
      try {
        val result =
            exerciseCatalogMutex.withLock {
              withContext(Dispatchers.IO) {
                val bytes =
                    contentResolver.openInputStream(uri)?.use(::readExerciseCollection)
                        ?: throw IOException("Could not open exercise collection")
                ExerciseCatalogRepository(applicationContext).importCustomExercises(bytes)
              }
            }
        if (result.addedCount > 0) loadExerciseCatalog()
        exerciseCollectionMessage = exerciseCollectionImportMessage(result)
      } catch (error: Exception) {
        Log.w("RelvenHome", "Could not import custom exercise collection", error)
        exerciseCollectionMessage = getString(org.runnerup.common.R.string.Exercise_collection_import_error)
      } finally {
        exerciseCollectionBusy = false
      }
    }
  }

  private fun exportExerciseCollection(uri: Uri) {
    lifecycleScope.launch {
      try {
        exerciseCatalogMutex.withLock {
          withContext(Dispatchers.IO) {
            val bytes = ExerciseCatalogRepository(applicationContext).exportCustomExercises()
            val output = contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open export destination")
            output.use {
              it.write(bytes)
              it.flush()
            }
          }
        }
        exerciseCollectionMessage = getString(org.runnerup.common.R.string.Exercise_collection_export_success)
      } catch (error: Exception) {
        Log.w("RelvenHome", "Could not export custom exercise collection", error)
        exerciseCollectionMessage = getString(org.runnerup.common.R.string.Exercise_collection_export_error)
      } finally {
        exerciseCollectionBusy = false
      }
    }
  }

  private fun exerciseCollectionImportMessage(result: CollectionImportResult): String =
      if (result.addedCount == 0) {
        getString(org.runnerup.common.R.string.Exercise_collection_import_no_changes)
      } else {
        getString(org.runnerup.common.R.string.Exercise_collection_import_success, result.addedCount)
      }

  private fun toggleExerciseFavorite(exerciseId: String, favorite: Boolean) {
    ExerciseFavoritesStore(applicationContext).setFavorite(exerciseId, favorite)
    favoriteExerciseIds = favoriteExerciseIds.toMutableSet().apply {
      if (favorite) add(exerciseId) else remove(exerciseId)
    }
  }

  private fun loadProgress() {
    val progressRequest = progressRequests.begin()
    progressState = ProgressLoadState.Loading
    val nowMillis = System.currentTimeMillis()
    currentTimeMillis = nowMillis
    lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) { runCatching { loadProgressSummary(nowMillis) } }
      result.exceptionOrNull()?.let { error ->
        Log.w("RelvenHome", "Could not load activity progress", error)
      }
      if (progressRequests.isCurrent(progressRequest)) {
        progressState = progressLoadState(result)
      }
    }
  }

  private fun loadProgressSummary(nowMillis: Long): ProgressSummary {
    val db = DBHelper.getReadableDatabase(applicationContext)
    val cutoffSeconds = (nowMillis - 28L * 24 * 60 * 60 * 1000) / 1000
    val rows = mutableListOf<RecordedActivity>()
    db.query(
            DB.ACTIVITY.TABLE,
            arrayOf("_id", DB.ACTIVITY.START_TIME, DB.ACTIVITY.TIME, DB.ACTIVITY.DISTANCE),
            "deleted = 0 AND ${DB.ACTIVITY.START_TIME} >= ? AND ${DB.ACTIVITY.START_TIME} <= ?",
            arrayOf(cutoffSeconds.toString(), (nowMillis / 1000).toString()),
            null,
            null,
            "${DB.ACTIVITY.START_TIME} DESC")
        .use { cursor ->
          while (cursor.moveToNext()) {
            rows +=
                RecordedActivity(
                    id = cursor.getLong(0).toString(),
                    startedAtMillis = cursor.getLong(1) * 1000,
                    durationSeconds = if (cursor.isNull(2)) null else cursor.getLong(2),
                    distanceMeters = if (cursor.isNull(3)) null else cursor.getDouble(3))
          }
        }
    return summarizeActivities(rows, nowMillis)
  }

  private fun reloadExerciseProgression() {
    exerciseProgressionState = ExerciseProgressionLoadState.Loading
    lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              strengthStoreMutex.withLock {
                StrengthSessionStore.completedSessions(applicationContext)
              }
            }
          }
      result.exceptionOrNull()?.let { error ->
        Log.w("RelvenHome", "Could not reload strength progression history", error)
      }
      exerciseProgressionState = exerciseProgressionLoadState(result)
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
      val columns =
          arrayOf("_id", DB.ACTIVITY.START_TIME, DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME)
      db.query(DB.ACTIVITY.TABLE, columns, "deleted = 0", null, null, null,
          "${DB.ACTIVITY.START_TIME} DESC", "1").use { cursor: Cursor ->
        if (!cursor.moveToFirst()) return null
        val recovery =
            if (cursor.isNull(3)) readActivityRecoverySummary(db, cursor.getLong(0)) else null
        return RecentActivity(
            startedAtSeconds = cursor.getLong(1),
            distanceMeters =
                if (cursor.isNull(2)) recovery?.distanceMeters
                else cursor.getDouble(2).takeIf { it.isFinite() && it > 0.0 },
            durationSeconds =
                if (cursor.isNull(3)) recovery?.durationSeconds
                else cursor.getLong(3).takeIf { it >= 0L })
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
    val completedStrengthSessions: Map<String, CompletedStrengthSession>,
    val exerciseProgressionState: ExerciseProgressionLoadState,
    val progressSummary: Result<ProgressSummary>)

private data class PendingStrengthSessionChoice(
    val session: StrengthSession,
    val requestedWorkoutName: String)

internal enum class HomeDestination {
  Today,
  Start,
  Plan,
  Exercises,
  Progress,
  History
}

internal fun homeDestinationFromSaved(saved: String?): HomeDestination =
    saved?.let { value -> HomeDestination.entries.firstOrNull { it.name == value } }
        ?: HomeDestination.Today

internal fun homeDestinationFromRequest(saved: String?, openPlanLibrary: Boolean): HomeDestination =
    if (openPlanLibrary) HomeDestination.Plan else homeDestinationFromSaved(saved)

@Composable
private fun RelvenHome(
    recentActivity: RecentActivity?,
    sessionStatus: HomeSessionStatus?,
    todayPlan: TodayPlanState,
    activePlanState: ActivePlanLoadState,
    exerciseCatalogLoadState: ExerciseCatalogLoadState,
    exerciseProgressionState: ExerciseProgressionLoadState,
    progressState: ProgressLoadState,
    activityHistoryState: ActivityHistoryLoadState,
    planRevisionStatus: PlanRevisionStatus,
    currentTimeMillis: Long,
    pendingPlannedRunningWorkout: org.runnerup.workout.Workout?,
    destination: HomeDestination,
    planLibraryState: PlanLibraryState,
    favoriteWorkouts: List<FavoriteWorkout>,
    planLibraryOpen: Boolean,
    planLibraryBusy: Boolean,
    planCreationOpen: Boolean,
    planCreationError: String?,
    onRetryPlanLibrary: () -> Unit,
    onImportPlan: () -> Unit,
    onOpenPlanCreator: () -> Unit,
    onClosePlanCreator: () -> Unit,
    onPlanDraftChanged: () -> Unit,
    onCreatePlan: (String, String, Int, List<PlanDraftSession>, List<PlanDraftStrengthExercise>) -> Unit,
    planActivationPending: Boolean,
    onRetryPlanActivation: () -> Unit,
    onCreatePlanWithAi: () -> Unit,
    onActivatePlan: (RelvenPlanImporter.Plan) -> Unit,
    onClearActivePlan: () -> Unit,
    onArchivePlan: (String) -> Unit,
    onRestoreArchivedPlan: (String) -> Unit,
    onOpenPlanLibrary: () -> Unit,
    onOpenPlanCalendar: () -> Unit,
    startFragmentManager: FragmentManager,
    onSelectDestination: (HomeDestination) -> Unit,
    onRetryProgress: () -> Unit,
    onRetryHistory: () -> Unit,
    onAddActivity: () -> Unit,
    onImportActivity: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onOpenActiveSession: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onReschedulePlan: (String, String) -> Unit,
    onAddWorkoutToPlan: (String, String) -> Unit,
    onAddRestToPlan: (String) -> Unit,
    onRemovePlanEntry: (String) -> Unit,
    onRenamePlan: (String) -> Unit,
    onRenameWorkout: (String, String) -> Unit,
    onSaveFavoriteWorkout: (String, String) -> Unit,
    onStartFavoriteWorkout: (FavoriteWorkout) -> Unit,
    onRemoveFavoriteWorkout: (String) -> Unit,
    onEditRunningEnd: (String, String, org.json.JSONObject) -> Unit,
    onEditRunningTarget: (String, String, org.json.JSONObject?) -> Unit,
    onRetryExerciseCatalog: () -> Unit,
    onSaveCustomExercise: suspend (String?, CustomExerciseInput) -> String,
    onSetCustomExerciseArchived: suspend (String, Boolean) -> Unit,
    onImportCustomExercises: () -> Unit,
    onExportCustomExercises: () -> Unit,
    exerciseCollectionBusy: Boolean,
    favoriteExerciseIds: Set<String>,
    onToggleExerciseFavorite: (String, Boolean) -> Unit,
    onRetryExerciseProgression: () -> Unit,
    completedStrengthSessions: Map<String, CompletedStrengthSession>,
    onStartStrength: (String, PlanCalendarEntry.Workout) -> Unit,
    onViewStrengthCompletion: (CompletedStrengthSession) -> Unit,
    onStartPlannedRunningWorkout: (org.runnerup.workout.Workout) -> Unit,
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
      TodayPlanCard(
          state = todayPlan,
          onOpenPlanLibrary = onOpenPlanLibrary,
          onOpenPlanCalendar = onOpenPlanCalendar)
      Surface(
          shape = RoundedCornerShape(16.dp),
          color = RelvenColors.Surface,
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
          modifier =
              Modifier.fillMaxWidth().clickable(
                  role = Role.Button, onClick = { onSelectDestination(HomeDestination.Progress) })) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(org.runnerup.common.R.string.Progress_title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(org.runnerup.common.R.string.Progress_entry_summary),
                color = RelvenColors.MutedText,
                fontSize = 13.sp)
          }
          Text(stringResource(org.runnerup.common.R.string.Progress_open), color = RelvenColors.Orange)
        }
      }
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
    } else if (destination == HomeDestination.Start) {
      StartDestinationScreen(startFragmentManager, pendingPlannedRunningWorkout)
    } else if (destination == HomeDestination.History) {
      ActivityHistoryScreen(
          loadState = activityHistoryState,
          onRetry = onRetryHistory,
          onAddActivity = onAddActivity,
          onImportActivity = onImportActivity,
          onOpenActivity = onOpenActivity)
    } else if (destination == HomeDestination.Progress) {
      ProgressScreen(
          progressState = progressState,
          onRetry = onRetryProgress,
          onBack = { onSelectDestination(HomeDestination.Today) })
    } else if (destination == HomeDestination.Plan) {
      if (planCreationOpen) {
        PlanCreationScreen(
            busy = planLibraryBusy,
            errorMessage = planCreationError,
            activationPending = planActivationPending,
            exerciseCatalogLoadState = exerciseCatalogLoadState,
            onBack = onClosePlanCreator,
            onRetryActivation = onRetryPlanActivation,
            onRetryExerciseCatalog = onRetryExerciseCatalog,
            onDraftChanged = onPlanDraftChanged,
            onCreate = onCreatePlan)
      } else if (planLibraryOpen || activePlanState !is ActivePlanLoadState.Loaded) {
        PlanLibraryScreen(
            state = planLibraryState,
            favoriteWorkouts = favoriteWorkouts,
            currentTimeMillis = currentTimeMillis,
            busy = planLibraryBusy,
            onRetry = onRetryPlanLibrary,
            onImport = onImportPlan,
            onCreatePlan = onOpenPlanCreator,
            onCreateWithAi = onCreatePlanWithAi,
            pendingActivation = planActivationPending,
            onRetryPendingActivation = onRetryPlanActivation,
            onActivate = onActivatePlan,
            onClearActive = onClearActivePlan,
            onArchive = onArchivePlan,
            onRestore = onRestoreArchivedPlan,
            onStartFavoriteWorkout = onStartFavoriteWorkout,
            onRemoveFavoriteWorkout = onRemoveFavoriteWorkout,
            onOpenCalendar = onOpenPlanCalendar)
      } else {
        PlanCalendarScreen(
            loadState = activePlanState,
            revisionStatus = planRevisionStatus,
            nowMillis = currentTimeMillis,
            completedStrengthSessions = completedStrengthSessions,
            onSaveFavoriteWorkout = onSaveFavoriteWorkout,
            onStartStrength = onStartStrength,
            onStartRunning = onStartPlannedRunningWorkout,
            onViewStrengthCompletion = onViewStrengthCompletion,
            onOpenPlanLibrary = onOpenPlanLibrary,
            onReschedule = onReschedulePlan,
            onAddWorkout = onAddWorkoutToPlan,
            onAddRest = onAddRestToPlan,
            onRemoveEntry = onRemovePlanEntry,
            onRenamePlan = onRenamePlan,
            onRenameWorkout = onRenameWorkout,
            onEditRunningEnd = onEditRunningEnd,
            onEditRunningTarget = onEditRunningTarget)
      }
    } else {
      ExerciseCatalogScreen(
          exerciseCatalogLoadState,
          progressionState = exerciseProgressionState,
          onRetry = onRetryExerciseCatalog,
          onRetryProgression = onRetryExerciseProgression,
          onSaveCustomExercise = onSaveCustomExercise,
          onSetCustomExerciseArchived = onSetCustomExerciseArchived,
          onImportCustomExercises = onImportCustomExercises,
          onExportCustomExercises = onExportCustomExercises,
          exerciseCollectionBusy = exerciseCollectionBusy,
          favoriteExerciseIds = favoriteExerciseIds,
          onToggleFavorite = onToggleExerciseFavorite)
    }
    if (destination != HomeDestination.Plan || !planCreationOpen) {
      HomeNavigationBar(
          destination = destination,
          onSelectDestination = onSelectDestination,
          onOpenHistory = onOpenHistory,
          modifier = Modifier.align(Alignment.BottomCenter))
    }
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
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
  Surface(
      color = Color(0xFF0E141E),
      shape = RoundedCornerShape(28.dp),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
      modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
      HomeNavigationItem(
          selected = destination == HomeDestination.Today || destination == HomeDestination.Progress,
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
          selected = destination == HomeDestination.Start,
          icon = { tint ->
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = tint)
          },
          label = stringResource(org.runnerup.common.R.string.Home_nav_start),
          onClick = { onSelectDestination(HomeDestination.Start) },
          modifier = Modifier.weight(1f))
      HomeNavigationItem(
          selected = destination == HomeDestination.History,
          icon = { tint -> Icon(Icons.Default.History, contentDescription = null, tint = tint) },
          label = stringResource(org.runnerup.common.R.string.Home_nav_history),
          onClick = onOpenHistory,
          modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun ProgressScreen(
    progressState: ProgressLoadState,
    onRetry: () -> Unit,
    onBack: () -> Unit) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 24.dp)
              .padding(top = 20.dp, bottom = 120.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)) {
    TextButton(onClick = onBack) {
      Text(stringResource(org.runnerup.common.R.string.Progress_back))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
          stringResource(org.runnerup.common.R.string.Progress_title),
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.semantics { heading() })
      Text(
          stringResource(org.runnerup.common.R.string.Progress_subtitle),
          color = RelvenColors.MutedText)
    }
    when (progressState) {
      ProgressLoadState.Loading ->
          Text(
              stringResource(org.runnerup.common.R.string.Progress_loading),
              color = RelvenColors.MutedText)
      ProgressLoadState.Unavailable -> {
        Text(
            stringResource(org.runnerup.common.R.string.Progress_unavailable),
            color = RelvenColors.MutedText)
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
          Text(stringResource(org.runnerup.common.R.string.Progress_retry))
        }
      }
      is ProgressLoadState.Loaded -> {
        val summary = progressState.summary
        if (summary.twentyEightDays.sessionCount == 0) {
          Text(
              stringResource(org.runnerup.common.R.string.Progress_empty),
              color = RelvenColors.MutedText)
        }
        ProgressWindowCard(
            title = stringResource(org.runnerup.common.R.string.Progress_seven_days),
            summary = summary.sevenDays)
        ProgressWindowCard(
            title = stringResource(org.runnerup.common.R.string.Progress_twenty_eight_days),
            summary = summary.twentyEightDays)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = RelvenColors.Surface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(org.runnerup.common.R.string.Progress_load_title),
                fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(org.runnerup.common.R.string.Progress_load_unavailable),
                color = RelvenColors.MutedText)
          }
        }
      }
    }
  }
}

@Composable
private fun ProgressWindowCard(title: String, summary: ActivityWindowSummary) {
  val locale = ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.forLanguageTag("sv-SE")
  Surface(
      shape = RoundedCornerShape(20.dp),
      color = RelvenColors.Surface,
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(
          stringResource(
              org.runnerup.common.R.string.Progress_sessions,
              summary.sessionCount),
          color = RelvenColors.MutedText)
      val durationLabel =
          when {
            summary.durationSeconds < 60 ->
                stringResource(
                    org.runnerup.common.R.string.Progress_duration_seconds,
                    summary.durationSeconds)
            summary.durationSeconds < 3600 ->
                stringResource(
                    org.runnerup.common.R.string.Progress_duration_minutes,
                    summary.durationSeconds / 60,
                    summary.durationSeconds % 60)
            else ->
                stringResource(
                    org.runnerup.common.R.string.Progress_duration,
                    summary.durationSeconds / 3600,
                    (summary.durationSeconds % 3600) / 60)
          }
      Text(durationLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
      val distance = summary.distanceMeters
      if (distance != null) {
        val distanceText =
            remember(locale, distance) {
              NumberFormat.getNumberInstance(locale).format(distance / 1000.0)
            }
        Text(
            stringResource(
                org.runnerup.common.R.string.Progress_distance,
                distanceText,
                summary.distanceSessionCount),
            color = RelvenColors.MutedText)
      } else if (summary.distanceSessionCount > 0) {
        Text(
            stringResource(
                org.runnerup.common.R.string.Progress_distance_total_unavailable,
                summary.distanceSessionCount),
            color = RelvenColors.MutedText)
      }
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
private fun TodayPlanCard(
    state: TodayPlanState,
    onOpenPlanLibrary: () -> Unit,
    onOpenPlanCalendar: () -> Unit) {
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
              stringResource(org.runnerup.common.R.string.Today_plan_create_hint),
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
      when (todayPlanAction(state)) {
        TodayPlanAction.CreateOrManagePlans ->
            Button(
                onClick = onOpenPlanLibrary,
                colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
              Text(stringResource(org.runnerup.common.R.string.Today_plan_open_library))
            }
        TodayPlanAction.ManageActivePlan ->
            Button(
                onClick = onOpenPlanCalendar,
                colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
              Text(stringResource(org.runnerup.common.R.string.Today_plan_open_calendar))
            }
        null -> Unit
      }
    }
  }
}

private data class PendingActivityImport(
    val activity: ImportedActivity,
    val sha256: String,
    val displayName: String)

@Composable
private fun ActivityImportReviewDialog(
    pending: PendingActivityImport,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit) {
  val activity = pending.activity
  val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
      .format(Date(activity.startTimeSeconds * 1000L))
  val distanceKm = NumberFormat.getNumberInstance().format(activity.distanceMeters / 1000.0)
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(org.runnerup.common.R.string.Home_import_review_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(pending.displayName, fontWeight = FontWeight.SemiBold)
          Text(date)
          Text(stringResource(org.runnerup.common.R.string.Home_import_summary, distanceKm, activity.durationSeconds / 60, activity.trackPoints.size))
        }
      },
      confirmButton = {
        TextButton(onClick = onConfirm) {
          Text(stringResource(org.runnerup.common.R.string.Home_import_save))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text(stringResource(org.runnerup.common.R.string.Home_import_cancel))
        }
      })
}
