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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides instance of {@link HRProvider}
 *
 * @author jonas
 */

public class HRManager {

    static class AntPlusProxy {
        // AntPlus module may be disabled when building, only available in a few phones
        private static final String Lib = "org.runnerup.hr.AntPlus";
        static final String Name = "AntPlus";

        static boolean checkAntPlusLibrary(Context ctx) {
            if (BuildConfig.ANTPLUS_ENABLED) {
                try {
                    Class<?> clazz = Class.forName(Lib);
                    Method method = clazz.getDeclaredMethod("checkLibrary", Context.class);
                    return (boolean) method.invoke(null, ctx);
                } catch (Exception e) {
                    Log.d(Lib, Name + "Library is not loaded "+e);
                }
            }
            return false;
        }

        static HRProvider createProviderByReflection(Context ctx, boolean experimental) {
            try {
                Class<?> classDefinition = Class.forName(Lib);
                Constructor<?> cons = classDefinition.getConstructor(Context.class);
                HRProvider ap = (HRProvider) cons.newInstance(ctx);
                if (!ap.isEnabled()) {
                    return null;
                }
                return ap;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Creates an {@link HRProvider}. This will be wrapped in a {@link RetryingHRProviderProxy}.
     * *
     * @param src The type of {@link HRProvider} to create.
     * @return A new instance of an {@link HRProvider} or null if
     *   A) 'src' is not a valid {@link HRProvider} type
     *   B) the device does not support an {@link HRProvider} of type 'src'
     */
    public static HRProvider getHRProvider(Context ctx, String src) {
        HRProvider provider = getHRProviderImpl(ctx, src);
        if (provider != null) {
            return new RetryingHRProviderProxy(provider);
        }
        return null;
    }

    
    private static HRProvider getHRProviderImpl(Context ctx, String src) {
        System.err.println("getHRProvider(" + src + ")");
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

        if (src.contentEquals(AntPlusProxy.Name)) {
            if (!AntPlusProxy.checkAntPlusLibrary(ctx))
                return null;
            HRProvider hrprov = AntPlusProxy.createProviderByReflection(ctx, true);
            if (hrprov != null && src.contentEquals(hrprov.getProviderName())) {
                return hrprov;
            }
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

    /**
     * Returns a list of {@link HRProvider}'s that are available on this device.
     * 
     * It is recommended to use this list only for selecting a valid {@link HRProvider}.
     * For connecting to the device, use the instance returned by {@link #getHRProvider(android.content.Context, String)}
     * 
     * @return A list of all {@link HRProvider}'s that are available on this device.
     */
    public static List<HRProvider> getHRProviderList(Context ctx) {
        Resources res = ctx.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean experimental = prefs
                .getBoolean(res.getString(R.string.pref_bt_experimental), false);
        boolean mock = prefs.getBoolean(res.getString(R.string.pref_bt_mock), false);

        List<HRProvider> providers = new ArrayList<>();
        if (AndroidBLEHRProvider.checkLibrary(ctx)) {
            providers.add(new AndroidBLEHRProvider(ctx));
        }

        if (experimental && Bt20Base.checkLibrary(ctx)) {
            providers.add(new Bt20Base.ZephyrHRM(ctx));
        }

        if (experimental && Bt20Base.checkLibrary(ctx)) {
            providers.add(new Bt20Base.PolarHRM(ctx));
        }

        if (experimental && Bt20Base.checkLibrary(ctx)) {
            providers.add(new Bt20Base.StHRMv1(ctx));
        }

        if (AntPlusProxy.checkAntPlusLibrary(ctx)) {
            HRProvider hrprov = AntPlusProxy.createProviderByReflection(ctx, experimental);
            if (hrprov != null) {
                providers.add(hrprov);
            }
        }

        if (mock) {
            providers.add(new MockHRProvider(ctx));
        }

        return providers;
    }
}
