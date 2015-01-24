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

package org.runnerup.hr;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import org.runnerup.R;

import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.FROYO)
public class HRManager {

    public static HRProvider getHRProvider(Context ctx, String src) {
        HRProvider provider = getHRProviderImpl(ctx, src);
        if (provider != null) {
            return new RetryingHRProviderProxy(provider);
        }
        return provider;
    }

    private static HRProvider getHRProviderImpl(Context ctx, String src) {
        System.err.println("getHRProvider(" + src + ")");
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

        if (src.contentEquals(AntPlus.NAME)) {
            if (!AntPlus.checkLibrary(ctx))
                return null;
            HRProvider p = new AntPlus(ctx);
            System.err.println("getHRProvider(" + src + ") => " + p);
            return p;
        }

        if (src.contentEquals(Bt20Base.StHRMv1.NAME)) {
            if (!Bt20Base.checkLibrary(ctx))
                return null;
            return new Bt20Base.StHRMv1(ctx);
        }

        if (src.contentEquals(MockHRProvider.NAME)) {
            return new MockHRProvider(ctx);
        }

        return null;
    }

    public static List<HRProvider> getHRProviderList(Context ctx) {
        Resources res = ctx.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean experimental = prefs
                .getBoolean(res.getString(R.string.pref_bt_experimental), false);
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

        if (Bt20Base.checkLibrary(ctx)) {
            providers.add(new Bt20Base.StHRMv1(ctx));
        }

        if (AntPlus.checkLibrary(ctx)) {
            providers.add(new AntPlus(ctx));
        }

        if (mock) {
            providers.add(new MockHRProvider(ctx));
        }

        return providers;
    }
}
