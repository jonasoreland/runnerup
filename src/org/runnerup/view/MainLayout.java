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
package org.runnerup.view;

import org.runnerup.R;

import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;

public class MainLayout extends TabActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Resources res = getResources(); // Resource object to get Drawables
		TabHost tabHost = getTabHost(); // The activity TabHost

		tabHost.addTab(tabHost.newTabSpec("Start")
				.setIndicator("Start", res.getDrawable(R.drawable.ic_tab_main))
				.setContent(new Intent(this, StartActivity.class)));

		tabHost.addTab(tabHost.newTabSpec("Feed")
				.setIndicator("Feed", res.getDrawable(R.drawable.ic_tab_feed))
				.setContent(new Intent(this, FeedActivity.class)));

		tabHost.addTab(tabHost
				.newTabSpec("History")
				.setIndicator("History",
						res.getDrawable(R.drawable.ic_tab_history))
				.setContent(new Intent(this, HistoryActivity.class)));

		tabHost.addTab(tabHost
				.newTabSpec("Settings")
				.setIndicator("Settings",
						res.getDrawable(R.drawable.ic_tab_setup))
				.setContent(new Intent(this, SettingsActivity.class)));

		// Set tabs Colors
		tabHost.setBackgroundColor(Color.BLACK);
		tabHost.getTabWidget().setBackgroundColor(Color.BLACK);
		tabHost.setCurrentTab(0);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		Intent i = null;
		switch (item.getItemId()) {
		case R.id.menu_accounts:
			i = new Intent(this, AccountsActivity.class);
			break;
		case R.id.menu_workouts:
			i = new Intent(this, ManageWorkoutsActivity.class);
			break;
		case R.id.menu_audio_cues:
			i = new Intent(this, AudioCueSettingsActivity.class);
			break;
		case R.id.menu_settings:
			getTabHost().setCurrentTab(3);
			return true;
		}
		if (i != null) {
			startActivity(i);
		}
		return true;
	}
}