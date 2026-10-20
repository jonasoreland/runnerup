package org.runnerup.relven.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.runnerup.view.MainLayout

/** Development entry point while the legacy workout controls remain authoritative. */
class RelvenPreviewActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RelvenTheme {
        RelvenPreviewHome(
            onOpenWorkout = { startActivity(Intent(this, MainLayout::class.java)) },
            onOpenHistory = {
              startActivity(
                  Intent(this, MainLayout::class.java)
                      .putExtra(MainLayout.EXTRA_INITIAL_TAB, 1))
            })
      }
    }
  }
}

@Composable
private fun RelvenPreviewHome(onOpenWorkout: () -> Unit, onOpenHistory: () -> Unit) {
  Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
  Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
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
