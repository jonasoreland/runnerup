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
    progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
    progressBar.setIndeterminate(true);
    dialog = new AlertDialog.Builder(context).setView(progressBar).create();
  }

  public void setTitle(int titleId) {
    dialog.setTitle(titleId);
  }

  public void setTitle(CharSequence title) {
    dialog.setTitle(title);
  }

  public void setMessage(CharSequence message) {
    dialog.setMessage(message);
  }

  public void setCancelable(boolean cancelable) {
    dialog.setCancelable(cancelable);
  }

  public void setMax(int max) {
    progressBar.setIndeterminate(false);
    progressBar.setMax(max);
  }

  public void setProgress(int progress) {
    progressBar.setProgress(progress);
  }

  public void setButton(int which, CharSequence text, DialogInterface.OnClickListener listener) {
    dialog.setButton(which, text, listener);
  }

  public Button getButton(int which) {
    return dialog.getButton(which);
  }

  public void setCanceledOnTouchOutside(boolean cancel) {
    dialog.setCanceledOnTouchOutside(cancel);
  }

  public void cancel() {
    dialog.cancel();
  }

  public void show() {
    dialog.show();
  }

  public void dismiss() {
    dialog.dismiss();
  }

  public boolean isShowing() {
    return dialog.isShowing();
  }
}
