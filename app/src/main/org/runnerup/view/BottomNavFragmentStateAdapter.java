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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import org.runnerup.R;

/**
 * Provides the {@link Fragment} instances displayed in the app's main bottom navigation. This
 * adapter is used with a ViewPager2 to manage fragment states.
 */
public class BottomNavFragmentStateAdapter extends FragmentStateAdapter {
  /** The number of fragments that this adapter supplies. */
  private static final int NUM_PAGES = 3;

  /**
   * Drawable resource IDs for the icons displayed in the bottom navigation tabs. The order of icons
   * in this array must match the order of fragments returned by the {@link #createFragment(int)}
   * method at the corresponding positions.
   */
  @DrawableRes
  private static final int[] ICONS = {
    R.drawable.ic_tab_main_24dp, R.drawable.ic_tab_history_24dp, R.drawable.ic_tab_settings_24dp
  };

  public BottomNavFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
    super(fragmentActivity);
  }

  @NonNull
  @Override
  public Fragment createFragment(int position) {
    // Important! New fragments must always be created due to the way ViewPager2 and
    // FragmentStateAdapter manage the lifecycle of fragments.
    return switch (position) {
      case 1 -> new HistoryFragment();
      case 2 -> new SettingsFragment();
      default -> new StartFragment();
    };
  }

  @Override
  public int getItemCount() {
    return NUM_PAGES;
  }

  /**
   * Returns the drawable resource ID for the icon at the specified position.
   *
   * <p>This position corresponds to the index of the tab in the bottom navigation.
   *
   * @param position The position of the tab/fragment.
   * @return The drawable resource ID for the icon.
   * @throws ArrayIndexOutOfBoundsException if the position is out of the bounds of the icon array.
   */
  public @DrawableRes int getIcon(int position) {
    if (position < 0 || position >= ICONS.length) {
      throw new ArrayIndexOutOfBoundsException(
          "Position " + position + " is out of bounds of the icons array of size " + ICONS.length);
    }

    return ICONS[position];
  }
}
