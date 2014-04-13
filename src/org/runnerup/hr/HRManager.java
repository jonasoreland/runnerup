package org.runnerup.hr;

import java.util.ArrayList;
import java.util.List;

import org.runnerup.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRManager {

	public static HRProvider getHRProvider(Context ctx, String src) {
		if (src.contentEquals(SamsungBLEHRProvider.NAME)) {
			if (!SamsungBLEHRProvider.checkLibrary())
				return null;
			return new SamsungBLEHRProvider(ctx);
		}
		if (src.contentEquals(AndroidBLEHRProvider.NAME)) {
			if (!AndroidBLEHRProvider.checkLibrary(ctx))
				return null;
			return new AndroidBLEHRProvider(ctx);
		}
		if (src.contentEquals(Bt20Base.ZephyrHRM.NAME)) {
			if (!Bt20Base.checkLibrary(ctx))
				return null;
			return new Bt20Base.ZephyrHRM(ctx);
		}
		if (src.contentEquals(Bt20Base.PolarHRM.NAME)) {
			if (!Bt20Base.checkLibrary(ctx))
				return null;
			return new Bt20Base.PolarHRM(ctx);
		}
		if (src.contentEquals(MockHRProvider.NAME)) {
			return new MockHRProvider(ctx);
		}

		return null;
	}

	public static List<HRProvider> getHRProviderList(Context ctx) {
		Resources res = ctx.getResources();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		boolean experimental = prefs.getBoolean(res.getString(R.string.pref_bt_experimental), false);
		boolean mock = prefs.getBoolean(res.getString(R.string.pref_bt_mock), false);
		
		if (experimental) {
			/* dummy if to remove warning on experimental */
		}
		
		List<HRProvider> providers = new ArrayList<HRProvider>();
		if (SamsungBLEHRProvider.checkLibrary()) {
			providers.add(new SamsungBLEHRProvider(ctx));
		}
		
		if (AndroidBLEHRProvider.checkLibrary(ctx)) {
			providers.add(new AndroidBLEHRProvider(ctx));
		}
		
		if (Bt20Base.checkLibrary(ctx)) {
			providers.add(new Bt20Base.ZephyrHRM(ctx));
		}

		if (Bt20Base.checkLibrary(ctx)) {
			providers.add(new Bt20Base.PolarHRM(ctx));
		}
		
		if (mock) {
			providers.add(new MockHRProvider(ctx));
		}
		
		return providers;
	}
}
