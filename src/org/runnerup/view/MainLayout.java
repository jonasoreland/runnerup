package org.runnerup.view;

import org.runnerup.R;

import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TabHost;

public class MainLayout extends TabActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Resources res = getResources(); // Resource object to get Drawables
		TabHost tabHost = getTabHost(); // The activity TabHost

		Intent intent = new Intent(this, StartActivity.class);
		tabHost.addTab(tabHost.newTabSpec("Start")
				.setIndicator("Start", res.getDrawable(R.drawable.ic_tab_main))
				.setContent(intent));

		Intent intent2 = new Intent(this, HistoryActivity.class);
		tabHost.addTab(tabHost
				.newTabSpec("History")
				.setIndicator("History",
						res.getDrawable(R.drawable.ic_tab_setup))
				.setContent(intent2));

		Intent intent3 = new Intent(this, SettingsActivity.class);
		tabHost.addTab(tabHost
				.newTabSpec("Settings")
				.setIndicator("Settings",
						res.getDrawable(R.drawable.ic_tab_setup))
				.setContent(intent3));

		// Set tabs Colors
		tabHost.setBackgroundColor(Color.BLACK);
		tabHost.getTabWidget().setBackgroundColor(Color.BLACK);
		tabHost.setCurrentTab(0);
	}
}