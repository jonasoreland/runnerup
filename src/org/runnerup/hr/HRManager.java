package org.runnerup.hr;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRManager {

	public static HRProvider getHRProvider(Context ctx, String src) {
		if (src.contentEquals(SamsungBLEHRProvider.NAME)) {
			if (!SamsungBLEHRProvider.checkLibrary())
				return null;
			return new SamsungBLEHRProvider(ctx);
		}
		return null;
	}

	public static List<HRProvider> getHRProviderList(Context ctx) {
		List<HRProvider> providers = new ArrayList<HRProvider>();
		if (SamsungBLEHRProvider.checkLibrary()) {
			System.err.println("Samsung OK");
			providers.add(new SamsungBLEHRProvider(ctx));
		}
		
		if (providers.isEmpty()) {
			providers.add(new MockHRProvider());
		}
		
		return providers;
	}
}
