package org.runnerup.relven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WorkoutPreparationScreen(
    state: WorkoutPreparationUiState,
    trackerReady: Boolean,
    selectedWorkoutAvailable: Boolean = true,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(RelvenColors.Background)
              .padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)) {
    Text(
        text = "Redo att starta",
        color = RelvenColors.Text,
        fontSize = 29.sp,
        fontWeight = FontWeight.Bold)
    Text(text = state.workoutName, color = RelvenColors.MutedText, fontSize = 16.sp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RelvenColors.Surface,
        shape = RoundedCornerShape(20.dp)) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("NÄSTA", color = RelvenColors.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = state.upcomingStep,
            color = RelvenColors.Text,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold)
      }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RelvenColors.Surface,
        shape = RoundedCornerShape(16.dp)) {
      Row(
          modifier = Modifier.padding(18.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically) {
        if (state.phase == WorkoutPreparationPhase.PREPARING) {
          CircularProgressIndicator(
              modifier = Modifier.size(20.dp), color = RelvenColors.Orange, strokeWidth = 2.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(state.status, color = RelvenColors.Text, fontWeight = FontWeight.SemiBold)
          if (!selectedWorkoutAvailable) {
            Text(
                "Passet kunde inte återställas. Avbryt och välj passet igen.",
                color = RelvenColors.MutedText,
                fontSize = 13.sp)
          }
          Text(
              text =
                  if (state.watchReady) "Passet är överfört till klockan"
                  else "Inspelningen startar först när du trycker på start",
              color = RelvenColors.MutedText,
              fontSize = 13.sp)
        }
      }
    }

    Spacer(Modifier.weight(1f))
    Button(
        onClick = onStart,
        enabled =
            state.phase == WorkoutPreparationPhase.READY &&
                trackerReady &&
                selectedWorkoutAvailable,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RelvenColors.Orange)) {
      Text(
          text =
              when {
                !selectedWorkoutAvailable -> "Passet kunde inte återställas"
                trackerReady -> "Starta pass"
                else -> "Förbereder träningsmotorn"
              },
          color = RelvenColors.Background,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp)
    }
    OutlinedButton(
        onClick = onCancel,
        enabled = state.phase != WorkoutPreparationPhase.STARTING,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp)) {
      Text("Avbryt", color = RelvenColors.MutedText)
    }
  }
}
