package org.runnerup.relven.ui

import android.content.Intent
import android.os.Bundle
import android.database.Cursor
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.runnerup.common.util.Constants.DB
import org.runnerup.db.DBHelper
import org.runnerup.view.MainLayout

/** Production home screen; RunnerUp remains authoritative for workout controls. */
class RelvenHomeActivity : ComponentActivity() {
  private var recentActivity by mutableStateOf<RecentActivity?>(null)
  private var sessionStatus by mutableStateOf<HomeSessionStatus?>(null)
  private var trackerSessionBinding: HomeTrackerSessionBinding? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RelvenTheme {
        RelvenHome(
            recentActivity = recentActivity,
            sessionStatus = sessionStatus,
            onOpenWorkout = { startActivity(Intent(this, MainLayout::class.java)) },
            onOpenHistory = {
              startActivity(
                  Intent(this, MainLayout::class.java)
                      .putExtra(MainLayout.EXTRA_INITIAL_TAB, 1))
            })
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

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      recentActivity = withContext(Dispatchers.IO) { loadRecentActivity() }
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

@Composable
private fun RelvenHome(
    recentActivity: RecentActivity?,
    sessionStatus: HomeSessionStatus?,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit) {
  Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
  Box(Modifier.fillMaxSize()) {
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
          Text(
              status.label,
              color = RelvenColors.Orange,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Idag", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Redo för nästa pass?",
            fontSize = 15.sp,
            color = RelvenColors.MutedText)
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
    Surface(
        color = Color(0xDD0E141E),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp).size(190.dp, 64.dp)) {
      Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = {}, modifier = Modifier.background(RelvenColors.Orange, CircleShape)) {
          Icon(Icons.Default.Home, contentDescription = "Idag", tint = Color.White)
        }
        IconButton(onClick = onOpenWorkout) {
          Icon(
              Icons.AutoMirrored.Filled.DirectionsRun,
              contentDescription = "Starta pass",
              tint = Color.White)
        }
        IconButton(onClick = onOpenHistory) {
          Icon(Icons.Default.History, contentDescription = "Historik i träningsvyn", tint = Color.White)
        }
      }
    }
  }
  }
}
