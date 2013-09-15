package org.runnerup.gpstracker.hr;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRManager {

	public static HRProvider getHRProvider(String src, Context ctx) {
		if (!SamsungBLEHRProvider.checkLibrary())
			return null;
		return new SamsungBLEHRProvider(ctx);
	}

	public static List<HRProvider> getHRProviderList(Context ctx) {
		List<HRProvider> providers = new ArrayList<HRProvider>();
		if (SamsungBLEHRProvider.checkLibrary()) {
			providers.add(new SamsungBLEHRProvider(ctx));
		}
		
		if (providers.isEmpty()) {
			providers.add(new MockHRProvider());
		}
		
		return providers;
	}
}
