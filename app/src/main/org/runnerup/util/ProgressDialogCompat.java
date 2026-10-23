package org.runnerup.util;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.ProgressBar;
import androidx.appcompat.app.AlertDialog;

public final class ProgressDialogCompat {
  private final AlertDialog dialog;
  private final ProgressBar progressBar;

  public ProgressDialogCompat(Context context) {
    this(context, true);
  }

  /**
   * Creates a progress controller that can be driven by another UI when {@code showDialog} is
   * false.
   */
  public ProgressDialogCompat(Context context, boolean showDialog) {
    if (!showDialog) {
      dialog = null;
      progressBar = null;
      return;
    }
    progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
    progressBar.setIndeterminate(true);
    dialog = new AlertDialog.Builder(context).setView(progressBar).create();
  }

  public void setTitle(int titleId) {
    if (dialog != null) dialog.setTitle(titleId);
  }

  public void setTitle(CharSequence title) {
    if (dialog != null) dialog.setTitle(title);
  }

  public void setMessage(CharSequence message) {
    if (dialog != null) dialog.setMessage(message);
  }

  public void setCancelable(boolean cancelable) {
    if (dialog != null) dialog.setCancelable(cancelable);
  }

  public void setMax(int max) {
    if (progressBar != null) {
      progressBar.setIndeterminate(false);
      progressBar.setMax(max);
    }
  }

  public void setProgress(int progress) {
    if (progressBar != null) progressBar.setProgress(progress);
  }

  public void setButton(int which, CharSequence text, DialogInterface.OnClickListener listener) {
    if (dialog != null) dialog.setButton(which, text, listener);
  }

  public Button getButton(int which) {
    return dialog == null ? null : dialog.getButton(which);
  }

  public void setCanceledOnTouchOutside(boolean cancel) {
    if (dialog != null) dialog.setCanceledOnTouchOutside(cancel);
  }

  public void cancel() {
    if (dialog != null) dialog.cancel();
  }

  public void show() {
    if (dialog != null) dialog.show();
  }

  public void dismiss() {
    if (dialog != null) dialog.dismiss();
  }

  public boolean isShowing() {
    return dialog != null && dialog.isShowing();
  }
}
