/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.runnerup.R;
import org.runnerup.widget.WidgetUtil;

public class AudioCueSettingsActivity extends AppCompatActivity {
  public static final String SUFFIX = "_audio_cues";

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WidgetUtil.addLegacyOverflowButton(getWindow());
    setContentView(R.layout.settings_activity);

    Intent intent = getIntent();
    String settingsName = intent.getStringExtra("name");

    // Ensure that the fragment is added only once
    if (savedInstanceState == null) {
      Bundle bundle = new Bundle();
      if (settingsName != null) {
        // If the Intent that started this Activity has an audio cue name, pass it to the Fragment.
        bundle.putString("name", settingsName);
      }

      getSupportFragmentManager()
          .beginTransaction()
          .setReorderingAllowed(true)
          .replace(R.id.settings_fragment_container, AudioCueSettingsFragment.class, bundle)
          .commit();
    }

    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.settings_fragment_container),
        new OnApplyWindowInsetsListener() {
          @NonNull
          @Override
          public WindowInsetsCompat onApplyWindowInsets(
              @NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return WindowInsetsCompat.CONSUMED;
          }
        });
  }
}
