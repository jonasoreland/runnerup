package org.runnerup.relven.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import org.runnerup.R

data class ActivityUploadServiceState(
    val name: String,
    val uploaded: Boolean = false,
    val selected: Boolean = false,
    val externalId: String? = null,
    val externalUrl: String? = null)

internal fun canSelectActivityUpload(service: ActivityUploadServiceState): Boolean =
    !service.uploaded

internal fun updateActivityUploadSelection(
    service: ActivityUploadServiceState,
    selected: Boolean): ActivityUploadServiceState =
    if (canSelectActivityUpload(service)) service.copy(selected = selected) else service

internal fun updateActivityUploadSelection(
    services: List<ActivityUploadServiceState>,
    name: String,
    selected: Boolean): List<ActivityUploadServiceState> =
    services.map { service ->
      if (service.name == name) updateActivityUploadSelection(service, selected) else service
    }

internal fun selectedActivityUploadNames(services: List<ActivityUploadServiceState>): Set<String> =
    services.filter { canSelectActivityUpload(it) && it.selected }.mapTo(linkedSetOf()) { it.name }

interface ActivityUploadActions {
  fun onBack()

  fun onSelectionChanged(name: String, selected: Boolean)

  fun onUpload()

  fun onOpenUploadedActivity(name: String)

  fun onClearUpload(name: String)

  fun onManageAccounts()
}

class ActivityUploadStateHolder(
    private val actions: ActivityUploadActions,
    private val showBackButton: Boolean) {
  var services by mutableStateOf(emptyList<ActivityUploadServiceState>())
    private set
  var uploading by mutableStateOf(false)
    private set
  var allowImmediateUpload by mutableStateOf(true)
    private set

  fun setImmediateUploadAllowed(allowed: Boolean) {
    allowImmediateUpload = allowed
  }

  fun update(services: List<ActivityUploadServiceState>, uploading: Boolean) {
    this.services = services.toList()
    this.uploading = uploading
  }

  fun selectionChanged(name: String, selected: Boolean) = actions.onSelectionChanged(name, selected)

  fun back() = actions.onBack()

  fun upload() = actions.onUpload()

  fun openUploadedActivity(name: String) = actions.onOpenUploadedActivity(name)

  fun clearUpload(name: String) = actions.onClearUpload(name)

  fun manageAccounts() = actions.onManageAccounts()

  @Composable
  fun Content() {
    ActivityUploadServicesScreen(services, uploading, showBackButton, allowImmediateUpload, this)
  }
}

object ActivityUploadServicesCompose {
  @JvmStatic
  fun install(view: ComposeView, state: ActivityUploadStateHolder) {
    view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    view.setContent { RelvenTheme { state.Content() } }
  }
}

@Composable
private fun ActivityUploadServicesScreen(
    services: List<ActivityUploadServiceState>,
    uploading: Boolean,
    showBackButton: Boolean,
    allowImmediateUpload: Boolean,
    actions: ActivityUploadStateHolder) {
  val selectedNames = selectedActivityUploadNames(services)
  var serviceToClear by remember { mutableStateOf<String?>(null) }

  Surface(modifier = Modifier.fillMaxSize(), color = RelvenColors.Background) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (showBackButton) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
    if (showBackButton) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions::back, modifier = Modifier.size(48.dp)) {
          Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.activity_upload_back),
              tint = RelvenColors.Orange)
        }
        Text(
            stringResource(R.string.activity_upload_title),
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (!showBackButton) {
        Text(
            stringResource(R.string.activity_upload_title),
            fontSize = 25.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
      }
      Text(
          stringResource(R.string.activity_upload_subtitle),
          fontSize = 14.sp,
          color = RelvenColors.MutedText)
    }

    if (services.isEmpty()) {
      Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(22.dp),
          color = RelvenColors.Surface) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
              stringResource(R.string.activity_upload_empty_title),
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold)
          Text(
              stringResource(R.string.activity_upload_empty_body),
              color = RelvenColors.MutedText,
              fontSize = 14.sp)
        }
      }
      androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
    } else {
      LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(services, key = { it.name }) { service ->
          ActivityUploadServiceCard(
              service = service,
              enabled = !uploading,
              onSelectionChanged = { selected ->
                actions.selectionChanged(service.name, selected)
              },
              onOpen = { actions.openUploadedActivity(service.name) },
              onClear = { serviceToClear = service.name })
        }
      }
    }

    OutlinedButton(
        onClick = actions::manageAccounts,
        enabled = !uploading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
      Text(stringResource(R.string.activity_upload_manage_accounts))
    }
    if (allowImmediateUpload) {
      Button(
          onClick = actions::upload,
          enabled = selectedNames.isNotEmpty() && !uploading,
          modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
              containerColor = RelvenColors.Orange,
              contentColor = RelvenColors.Background)) {
        if (uploading) {
          CircularProgressIndicator(
              modifier = Modifier.padding(end = 10.dp),
              color = RelvenColors.Background,
              strokeWidth = 2.dp)
          Text(stringResource(R.string.activity_upload_in_progress))
        } else {
          Text(
              pluralStringResource(
                  R.plurals.activity_upload_submit, selectedNames.size, selectedNames.size),
              fontWeight = FontWeight.SemiBold)
        }
      }
    } else {
      Text(
          stringResource(R.string.activity_upload_save_note),
          color = RelvenColors.MutedText,
          fontSize = 13.sp)
    }
  }
  }

  serviceToClear?.let { name ->
    AlertDialog(
        onDismissRequest = { serviceToClear = null },
        title = { Text(stringResource(R.string.activity_upload_clear_title, name)) },
        text = { Text(stringResource(R.string.activity_upload_clear_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                serviceToClear = null
                actions.clearUpload(name)
              }) {
            Text(stringResource(R.string.activity_upload_confirm_clear))
          }
        },
        dismissButton = {
          TextButton(onClick = { serviceToClear = null }) {
            Text(stringResource(R.string.activity_upload_cancel))
          }
        })
  }
}

@Composable
private fun ActivityUploadServiceCard(
    service: ActivityUploadServiceState,
    enabled: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit) {
  val clearDescription =
      stringResource(R.string.activity_upload_clear_content_description, service.name)
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = RelvenColors.Surface,
      border = androidx.compose.foundation.BorderStroke(
          1.dp,
          if (service.uploaded) RelvenColors.Orange.copy(alpha = 0.28f)
          else RelvenColors.SurfaceRaised.copy(alpha = 0.7f))) {
    Row(
        modifier = Modifier
            .then(
                if (!service.uploaded) {
                  Modifier.selectable(
                      selected = service.selected,
                      enabled = enabled,
                      role = Role.Checkbox) { onSelectionChanged(!service.selected) }
                } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (service.uploaded) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = RelvenColors.Orange)
      } else {
        Checkbox(
            checked = service.selected,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier)
      }
      Column(
          modifier = Modifier.weight(1f).then(
              if (service.uploaded && service.externalUrl != null && enabled) {
                Modifier.clickable(role = Role.Button, onClick = onOpen)
              } else Modifier),
          verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(service.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(
                if (service.uploaded) R.string.activity_upload_status_uploaded
                else R.string.activity_upload_status_ready),
            color = if (service.uploaded) RelvenColors.Orange else RelvenColors.MutedText,
            fontSize = 13.sp)
        if (service.uploaded && service.externalUrl != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = RelvenColors.MutedText)
            Text(
                stringResource(R.string.activity_upload_open_link),
                color = RelvenColors.MutedText,
                fontSize = 12.sp)
          }
        }
      }
      if (service.uploaded) {
        IconButton(
            onClick = onClear,
            enabled = enabled,
            modifier = Modifier.semantics {
              contentDescription = clearDescription
            }) {
          Icon(
              Icons.Default.DeleteOutline,
              contentDescription = null,
              tint = RelvenColors.MutedText)
        }
      }
    }
  }
}
