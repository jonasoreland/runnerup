/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import org.runnerup.R;
import org.runnerup.util.GoogleApiHelper;

public class AboutPreference extends DialogPreference {

  public AboutPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public AboutPreference(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  private void init(Context context) {
    try {
      PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      this.setDialogTitle(
          context.getString(org.runnerup.common.R.string.About_RunnerUp)
              + " v"
              + pInfo.versionName);
    } catch (NameNotFoundException e) {
      Log.e(getClass().getName(), "Failed to get package info", e);
    }
    setNegativeButtonText(context.getString(org.runnerup.common.R.string.OK));
    if (GoogleApiHelper.isGooglePlayServicesAvailable(context)) {
      setPositiveButtonText(context.getString(org.runnerup.common.R.string.Rate_RunnerUp));
    } else {
      setPositiveButtonText(null);
    }
    setDialogLayoutResource(R.layout.whatsnew);
  }

  // The dialog showing the actual preference controls (a WebView).
  public static class AboutDialogFragment extends DialogFragment {
    public static String TAG = "AboutDialog";

    public static AboutDialogFragment newInstance(String preferenceKey) {
      AboutDialogFragment fragment = new AboutDialogFragment();
      Bundle bundle = new Bundle(1);
      bundle.putString("key", preferenceKey);
      fragment.setArguments(bundle);
      return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      View view = getLayoutInflater().inflate(R.layout.whatsnew, null);
      WebView wv = view.findViewById(R.id.web_view1);
      wv.loadUrl("file:///android_asset/about.html");

      AlertDialog.Builder builder =
          new AlertDialog.Builder(requireContext())
              .setView(view)
              .setNegativeButton(
                  org.runnerup.common.R.string.OK,
                  (dialog, which) -> {
                    // Dismissed
                  });

      if (GoogleApiHelper.isGooglePlayServicesAvailable(requireContext())) {
        builder.setPositiveButton(
            org.runnerup.common.R.string.Rate_RunnerUp,
            (dialog, which) -> {
              try {
                String applicationId = "org.runnerup";
                Uri uri = Uri.parse("market://details?id=" + applicationId);
                requireContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
              } catch (Exception ex) {
                Log.e(AboutDialogFragment.class.getName(), "Failed to open Play Store", ex);
              }
            });
      }

      try {
        PackageInfo pInfo =
            requireContext()
                .getPackageManager()
                .getPackageInfo(requireContext().getPackageName(), 0);
        builder.setTitle(
            requireContext().getString(org.runnerup.common.R.string.About_RunnerUp)
                + " v"
                + pInfo.versionName);
      } catch (Exception e) {
        Log.e(getClass().getName(), "Failed to get package info", e);
      }

      return builder.create();
    }
  }
}
