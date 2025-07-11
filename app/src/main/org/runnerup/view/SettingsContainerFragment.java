/*
 * Copyright (C) 2025 robert.jonsson75@gmail.com
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

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.runnerup.R;

/**
 * Hosts the application's settings hierarchy, starting with {@link SettingsFragment} and allowing
 * navigation to sub-preference screens like units or sensor settings. This container manages the
 * transition between these nested settings screens using its child FragmentManager and handles back
 * navigation within this hierarchy via a custom {@link OnBackPressedCallback}, ensuring that back
 * presses navigate correctly within the settings sub-screens before propagating to the main bottom
 * nav navigation in {@link MainLayout}.
 */
public class SettingsContainerFragment extends Fragment
    implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

  public SettingsContainerFragment() {
    super(R.layout.settings_activity);
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    requireActivity().getOnBackPressedDispatcher().addCallback(requireActivity(), onBackPressed);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // Ensure that this fragment is added only once
    if (savedInstanceState == null) {
      // Add SettingsFragment as the initial fragment for the app's settings hierarchy
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.settings_fragment_container, new SettingsFragment())
          .commit();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    // Update callback state when resumed
    onBackPressed.setEnabled(hasBackStackEntries());
  }

  @Override
  public void onPause() {
    super.onPause();
    // Disable callback when paused
    onBackPressed.setEnabled(false);
  }

  /**
   * Called when a {@link Preference} in a {@link PreferenceFragmentCompat} requests to navigate to
   * another {@link Fragment}. This occurs when a preference in the XML has an {@code app:fragment}
   * attribute.
   *
   * <p>This method handles the creation and display of the new fragment within this {@code
   * SettingsContainerFragment}'s designated container view ({@code
   * R.id.settings_fragment_container}). It also manages the back stack for this nested navigation
   * and enables the custom {@link #onBackPressed} callback to handle back presses within these
   * settings sub-screens.
   *
   * @param caller The {@link PreferenceFragmentCompat} that contains the Preference initiating the
   *     navigation.
   * @param pref The {@link Preference} that was clicked, containing the fragment class name to
   *     navigate to.
   * @return {@code true} to indicate that the navigation event has been handled.
   */
  @Override
  public boolean onPreferenceStartFragment(
      @NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
    final Bundle args = pref.getExtras();
    final String fragmentClassName = pref.getFragment();

    // Should not happen, but just in case
    if (fragmentClassName == null) {
      return false;
    }

    // Instantiate the new settings fragment
    final Fragment fragment =
        getChildFragmentManager()
            .getFragmentFactory()
            .instantiate(requireContext().getClassLoader(), fragmentClassName);
    fragment.setArguments(args);

    // Replace the existing fragment
    getChildFragmentManager()
        .beginTransaction()
        .replace(R.id.settings_fragment_container, fragment)
        .setReorderingAllowed(true)
        .addToBackStack(null)
        .commit();

    // Since a new settings fragment has been pushed onto the back stack, enable our
    // onBackPressed callback to handle back presses within this fragment.
    onBackPressed.setEnabled(true);

    return true;
  }

  private boolean hasBackStackEntries() {
    return getChildFragmentManager().getBackStackEntryCount() > 0;
  }

  /**
   * An {@link OnBackPressedCallback} instance that provides custom handling for back navigation
   * within the hierarchy of settings fragments.
   *
   * <p>The callback will only be enabled when this fragment is resumed and its {@link
   * #getChildFragmentManager()} has a back stack. When the back stack is empty, this callback will
   * be disabled to allow parent/activity callbacks to handle the next back press.
   */
  private final OnBackPressedCallback onBackPressed =
      new OnBackPressedCallback(false /* initially disabled */) {
        @Override
        public void handleOnBackPressed() {
          if (hasBackStackEntries()) {
            // There are back stack entries, so pop one off
            getChildFragmentManager().popBackStackImmediate();

            // After popping, update the enabled state of this callback
            setEnabled(hasBackStackEntries());
          }
        }
      };
}
