package org.runnerup.gpstracker.hr;

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
}
