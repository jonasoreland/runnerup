package org.runnerup.relven.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import org.runnerup.common.R
import org.runnerup.util.Formatter
import org.runnerup.workout.Sport

@Composable
internal fun ActivityEditForm(
    activity: HistoryActivityDetails,
    saveState: ActivityEditSaveState,
    onSave: (ActivityEditCommand) -> Unit,
    onCancel: () -> Unit) {
  val context = LocalContext.current
  val formatter = remember(context, LocalConfiguration.current) { Formatter(context) }
  val resources = LocalResources.current
  var sport by remember(activity.id) { mutableIntStateOf(activity.sport) }
  val initialDistance =
      remember(activity.id, formatter.getUnitMeters()) {
        formatDistanceInput(
            activity.distanceMeters ?: 0.0,
            formatter.getUnitMeters(),
            resources.configuration.locales[0])
      }
  var distance by remember(activity.id, formatter.getUnitMeters()) {
    mutableStateOf(initialDistance)
  }
  var comment by remember(activity.id) { mutableStateOf(activity.comment.orEmpty()) }
  var sportMenuExpanded by remember(activity.id) { mutableStateOf(false) }
  var distanceInvalid by remember(activity.id) { mutableStateOf(false) }

  Surface(
      color = RelvenColors.Surface,
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(
          stringResource(R.string.Home_activity_detail_edit_title),
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold)

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.Home_activity_detail_edit_sport),
            color = RelvenColors.MutedText,
            fontSize = 13.sp)
        androidx.compose.foundation.layout.Box {
          OutlinedButton(
              onClick = { sportMenuExpanded = true },
              modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(Sport.textOf(resources, sport), modifier = Modifier.weight(1f))
          }
          DropdownMenu(
              expanded = sportMenuExpanded,
              onDismissRequest = { sportMenuExpanded = false }) {
            Sport.values().forEach { option ->
              DropdownMenuItem(
                  text = { Text(Sport.textOf(resources, option.getDbValue())) },
                  onClick = {
                    sport = option.getDbValue()
                    sportMenuExpanded = false
                  })
            }
          }
        }
      }

      if (Sport.hasManualDistance(sport)) {
        OutlinedTextField(
            value = distance,
            onValueChange = {
              distance = it
              distanceInvalid = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
              Text(
                  stringResource(
                      R.string.Home_activity_detail_edit_distance_unit,
                      formatter.getDistanceUnit()))
            },
            singleLine = true,
            isError = distanceInvalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (distanceInvalid) {
          Text(
              stringResource(R.string.Home_activity_detail_edit_distance_invalid),
              color = MaterialTheme.colorScheme.error,
              fontSize = 13.sp)
        }
      }

      OutlinedTextField(
          value = comment,
          onValueChange = { comment = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text(stringResource(R.string.Home_activity_detail_edit_notes)) },
          minLines = 3,
          maxLines = 6)

      if (saveState is ActivityEditSaveState.Failed) {
        Text(
            stringResource(R.string.Home_activity_detail_edit_failed),
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp)
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = onCancel,
            enabled = saveState !is ActivityEditSaveState.Saving,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
          Text(stringResource(R.string.Cancel))
        }
        Button(
            onClick = {
              val manualDistanceMeters =
                  if (Sport.hasManualDistance(sport)) {
                    activityEditDistanceMeters(
                        distance,
                        formatter.getUnitMeters(),
                        initialDistance,
                        activity.distanceMeters)
                  } else null
              if (Sport.hasManualDistance(sport) && manualDistanceMeters == null) {
                distanceInvalid = true
              } else {
                onSave(
                    ActivityEditCommand(
                        activityId = activity.id,
                        sport = sport,
                        manualDistanceMeters = manualDistanceMeters,
                        comment = comment))
              }
            },
            enabled = saveState !is ActivityEditSaveState.Saving,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RelvenColors.Orange,
                contentColor = RelvenColors.Background)) {
          if (saveState is ActivityEditSaveState.Saving) {
            CircularProgressIndicator(color = RelvenColors.Background, strokeWidth = 2.dp)
          } else {
            Text(
                stringResource(R.string.Home_activity_detail_edit_save),
                fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}

private fun formatDistanceInput(distanceMeters: Double, metersPerUnit: Double, locale: Locale): String {
  if (!distanceMeters.isFinite() || distanceMeters < 0.0 || !metersPerUnit.isFinite() || metersPerUnit <= 0.0) {
    return "0"
  }
  return DecimalFormat("0.###", DecimalFormatSymbols.getInstance(locale)).apply {
    isGroupingUsed = false
  }.format(distanceMeters / metersPerUnit)
}
