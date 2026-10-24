package org.runnerup.relven.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.StringRes
import org.runnerup.common.R
import org.runnerup.common.tracker.TrackerState
import org.runnerup.common.util.ValueModel
import org.runnerup.tracker.Tracker
import org.runnerup.view.RunActivity

internal fun runActivityIntent(context: Context): Intent =
    Intent(context, RunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

internal enum class HomeSessionStatus(@StringRes val labelResource: Int) {
  ACTIVE(R.string.Home_session_active),
  PAUSED(R.string.Home_session_paused)
}

internal fun homeSessionStatus(state: TrackerState?): HomeSessionStatus? =
    when (state) {
      TrackerState.STARTED -> HomeSessionStatus.ACTIVE
      TrackerState.PAUSED -> HomeSessionStatus.PAUSED
      else -> null
    }

internal class HomeTrackerSessionObserver(
    private val tracker: Tracker,
    private val onStatusChanged: (HomeSessionStatus?) -> Unit
) : AutoCloseable {
  @Volatile private var closed = false

  private val stateListener =
      ValueModel.ChangeListener<TrackerState> { _, _, newState ->
        if (!closed) onStatusChanged(homeSessionStatus(newState))
      }

  init {
    tracker.registerTrackerStateListener(stateListener)
    onStatusChanged(homeSessionStatus(tracker.state))
  }

  override fun close() {
    if (closed) return
    closed = true
    tracker.unregisterTrackerStateListener(stateListener)
  }
}

internal class HomeTrackerSessionBinding(
    private val context: Context,
    private val onTrackerBound: (Tracker) -> Unit,
    private val onTrackerUnbound: (Tracker) -> Unit,
    private val onStatusChanged: (HomeSessionStatus?) -> Unit
) {
  constructor(context: Context, onStatusChanged: (HomeSessionStatus?) -> Unit) :
      this(context, {}, {}, onStatusChanged)

  private var started = false
  private var bound = false
  private var tracker: Tracker? = null
  private var observer: HomeTrackerSessionObserver? = null

  private val connection =
      object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
          if (!started || service !is Tracker.LocalBinder) return
          observer?.close()
          tracker = service.service
          observer = HomeTrackerSessionObserver(service.service, onStatusChanged)
          onTrackerBound(service.service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          observer?.close()
          observer = null
          tracker = null
          if (started) onStatusChanged(null)
        }
      }

  fun start() {
    if (started) return
    started = true
    bound = context.bindService(Intent(context, Tracker::class.java), connection, Context.BIND_AUTO_CREATE)
  }

  fun stop() {
    if (!started) return
    started = false
    tracker?.let(onTrackerUnbound)
    tracker = null
    observer?.close()
    observer = null
    onStatusChanged(null)
    if (bound) {
      context.unbindService(connection)
      bound = false
    }
  }
}
