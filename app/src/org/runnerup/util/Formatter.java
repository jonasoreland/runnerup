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

package org.runnerup.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;

import org.runnerup.R;
import org.runnerup.workout.Dimension;

import java.text.DecimalFormat;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.FROYO)
public class Formatter implements OnSharedPreferenceChangeListener {

    Context context = null;
    Resources resources = null;
    LocaleResources cueResources = null;
    SharedPreferences sharedPreferences = null;
    java.text.DateFormat dateFormat = null;
    java.text.DateFormat timeFormat = null;
    HRZones hrZones = null;

    boolean km = true;
    String base_unit = "km";
    double base_meters = km_meters;

    public final static double km_meters = 1000.0;
    public final static double mi_meters = 1609.34;
    public final static double FEETS_PER_METER = 3.2808;

    public static final int CUE = 1; // for text to speech
    public static final int CUE_SHORT = 2; // brief for tts
    public static final int CUE_LONG = 3; // long for tts
    public static final int TXT = 4; // same as TXT_SHORT but without unit
    public static final int TXT_SHORT = 5; // brief for printing
    public static final int TXT_LONG = 6; // long for printing

    public Formatter(Context ctx) {
        context = ctx;
        resources = ctx.getResources();
        cueResources = getLangResources(ctx);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
        timeFormat = android.text.format.DateFormat.getTimeFormat(ctx);
        hrZones = new HRZones(context);

        setUnit();
    }

    private class LocaleResources {
        final Resources resources;
        final Configuration configuration;
        final Locale defaultLocale;
        final Locale audioLocale;

        LocaleResources(Context ctx, Locale locale) {
            resources = ctx.getResources();
            configuration = resources.getConfiguration();
            defaultLocale = configuration.locale;
            audioLocale = locale;
        }

        void setLang(Locale locale) {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        }

        public String getString(int id) throws Resources.NotFoundException {
            setLang(audioLocale);
            String result = resources.getString(id);
            setLang(defaultLocale);
            return result;
        }

        public String getQuantityString(int id, int quantity) throws Resources.NotFoundException {
            setLang(audioLocale);
            String result = resources.getQuantityString(id, quantity);
            setLang(defaultLocale);
            return result;
        }
    };

    public static Locale getAudioLocale(Context ctx) {
        Resources res = ctx.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (prefs.contains(res.getString(R.string.pref_audio_lang))) {
            Log.e("Formatter", "Audio language: " +
                    prefs.getString(res.getString(R.string.pref_audio_lang), null));
            return new Locale(prefs.getString(res.getString(R.string.pref_audio_lang), "en"));
        }
        return null;
    }

    private LocaleResources getLangResources(Context ctx) {
        Locale loc = getAudioLocale(ctx);
        if (loc != null) {
            return new LocaleResources(ctx,loc);
        } else {
            return new LocaleResources(ctx, ctx.getResources().getConfiguration().locale);
        }
    }

    public String getCueString(int msgId) {
        return cueResources.getString(msgId);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key != null && context.getString(R.string.pref_unit).contentEquals(key))
            setUnit();
    }

    private void setUnit() {
        km = getUseKilometers(context.getResources(), sharedPreferences, null);

        if (km) {
            base_unit = "km";
            base_meters = km_meters;
        } else {
            base_unit = "mi";
            base_meters = mi_meters;
        }
    }

    public static boolean getUseKilometers(Resources res, SharedPreferences prefs, Editor editor) {
        boolean _km = true;
        String unit = prefs.getString(res.getString(R.string.pref_unit), null);
        if (unit == null)
            _km = guessDefaultUnit(res, prefs, editor);
        else if (unit.contentEquals("km"))
            _km = true;
        else if (unit.contentEquals("mi"))
            _km = false;
        else
            _km = guessDefaultUnit(res, prefs, editor);

        return _km;
    }

    private static boolean guessDefaultUnit(Resources res, SharedPreferences prefs, Editor editor) {
        String countryCode = Locale.getDefault().getCountry();
        Log.e("Formatter", "guessDefaultUnit: countryCode: " + countryCode);
        if (countryCode == null)
            return true; // km;
        String key = res.getString(R.string.pref_unit);
        if ("US".contentEquals(countryCode) ||
                "GB".contentEquals(countryCode)) {
            if (editor != null)
                editor.putString(key, "mi");
            return false;
        }
        else {
            if (editor != null)
                editor.putString(key, "km");
        }
        return true;
    }

    public double getUnitMeters() {
        return this.base_meters;
    }

    public static double getUnitMeters(Resources res, SharedPreferences prefs) {
        if (getUseKilometers(res, prefs, null))
            return km_meters;
        else
            return mi_meters;
    }

    public String getUnitString() {
        return this.base_unit;
    }

    public String format(int target, Dimension dimension, double value) {
        switch (dimension) {
            case DISTANCE:
                return formatDistance(target, Math.round(value));
            case TIME:
                return formatElapsedTime(target, Math.round(value));
            case PACE:
                return formatPace(target, value);
            case HR:
                return formatHeartRate(target, value);
            case HRZ:
                return formatHeartRateZone(target, value);
            case SPEED:
                return formatSpeed(target, value);
        }
        return "";
    }

    public String formatElapsedTime(int target, long seconds) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
                return cueElapsedTime(seconds, false);
            case CUE_LONG:
                return cueElapsedTime(seconds, true);
            case TXT:
            case TXT_SHORT:
                return DateUtils.formatElapsedTime(seconds);
            case TXT_LONG:
                return txtElapsedTime(seconds);
        }
        return "";
    }

    private String cueElapsedTime(long seconds, boolean includeDimension) {
        long hours = 0;
        long minutes = 0;
        if (seconds >= 3600) {
            hours = seconds / 3600;
            seconds -= hours * 3600;
        }
        if (seconds >= 60) {
            minutes = seconds / 60;
            seconds -= minutes * 60;
        }
        StringBuilder s = new StringBuilder();
        if (hours > 0) {
            includeDimension = true;
            s.append(hours)
                    .append(" ")
                    .append(cueResources.getQuantityString(R.plurals.cue_hour, (int)hours));
        }
        if (minutes > 0) {
            if (hours > 0)
                s.append(" ");
            includeDimension = true;
            s.append(minutes)
                    .append(" ")
                    .append(cueResources.getQuantityString(R.plurals.cue_minute, (int)minutes));
        }
        if (seconds > 0) {
            if (hours > 0 || minutes > 0)
                s.append(" ");

            if (includeDimension) {
                s.append(seconds)
                        .append(" ")
                        .append(cueResources.getQuantityString(R.plurals.cue_second, (int)seconds));
            } else {
                s.append(seconds);
            }
        }
        return s.toString();
    }

    private String txtElapsedTime(long seconds) {
        long hours = 0;
        long minutes = 0;
        if (seconds >= 3600) {
            hours = seconds / 3600;
            seconds -= hours * 3600;
        }
        if (seconds >= 60) {
            minutes = seconds / 60;
            seconds -= minutes * 60;
        }
        StringBuilder s = new StringBuilder();
        if (hours > 0) {
            s.append(hours).append(" ").append(resources.getString(R.string.metrics_elapsed_h));
        }
        if (minutes > 0) {
            if (hours > 0)
                s.append(" ");
            if (hours > 0 || seconds > 0)
                s.append(minutes).append(" ").append(resources.getString(R.string.metrics_elapsed_m));
            else
                s.append(minutes).append(" ").append(resources.getString(R.string.metrics_elapsed_min));
        }
        if (seconds > 0) {
            if (hours > 0 || minutes > 0)
                s.append(" ");
            s.append(seconds).append(" ").append(resources.getString(R.string.metrics_elapsed_s));
        }
        return s.toString();
    }

    /**
     * Format heart rate
     *
     * @param target
     * @param heart_rate
     * @return
     */
    public String formatHeartRate(int target, double heart_rate) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return Integer.toString((int) Math.round(heart_rate)) + " "
                        + cueResources.getQuantityString(R.plurals.cue_bpm, (int)heart_rate);
            case TXT:
            case TXT_SHORT:
            case TXT_LONG:
                return Integer.toString((int) Math.round(heart_rate));
        }
        return "";
    }

    private String formatHeartRateZone(int target, double hrZone) {
        switch (target) {
            case TXT:
            case TXT_SHORT:
                return Integer.toString((int) Math.round(hrZone));
            case TXT_LONG:
                return Double.toString(Math.round(10.0 * hrZone) / 10.0);
            case CUE_SHORT:
                return cueResources.getString(R.string.heart_rate_zone) + " "
                        + Integer.toString((int) Math.floor(hrZone));
            case CUE:
            case CUE_LONG:
                return cueResources.getString(R.string.heart_rate_zone) + " "
                        + Double.toString(Math.floor(10.0 * hrZone) / 10.0);
        }
        return "";
    }

    /**
     * Format pace
     * 
     * @param target
     * @param seconds_per_meter
     * @return
     */
    public String formatPace(int target, double seconds_per_meter) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cuePace(seconds_per_meter);
            case TXT:
            case TXT_SHORT:
                return txtPace(seconds_per_meter, false);
            case TXT_LONG:
                return txtPace(seconds_per_meter, true);
        }
        return "";
    }

    /**
     * @param seconds_per_meter
     * @return string suitable for printing according to settings
     */
    private String txtPace(double seconds_per_meter, boolean includeUnit) {
        long val = Math.round(base_meters * seconds_per_meter);
        String str = DateUtils.formatElapsedTime(val);
        if (includeUnit == false)
            return str;
        else {
            int res = km ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
            return str + "/" + resources.getString(res);
        }
    }

    private String cuePace(double seconds_per_meter) {
        long seconds_per_unit = Math.round(base_meters * seconds_per_meter);
        long hours_per_unit = 0;
        long minutes_per_unit = 0;
        if (seconds_per_unit >= 3600) {
            hours_per_unit = seconds_per_unit / 3600;
            seconds_per_unit -= hours_per_unit * 3600;
        }
        if (seconds_per_unit >= 60) {
            minutes_per_unit = seconds_per_unit / 60;
            seconds_per_unit -= minutes_per_unit * 60;
        }
        StringBuilder s = new StringBuilder();
        if (hours_per_unit > 0) {
            s.append(hours_per_unit)
                    .append(" ")
                    .append(cueResources.getQuantityString(R.plurals.cue_hour, (int)hours_per_unit));
        }
        if (minutes_per_unit > 0) {
            if (hours_per_unit > 0)
                s.append(" ");
            s.append(minutes_per_unit)
                    .append(" ")
                    .append(cueResources.getQuantityString(R.plurals.cue_minute, (int)minutes_per_unit));
        }
        if (seconds_per_unit > 0) {
            if (hours_per_unit > 0 || minutes_per_unit > 0)
                s.append(" ");
            s.append(seconds_per_unit)
                    .append(" ")
                    .append(cueResources.getQuantityString(R.plurals.cue_second, (int)seconds_per_unit));
        }
        s.append(" ").append(cueResources.getString(km ? R.string.cue_perkilometer : R.string.cue_permile));
        return s.toString();
    }

    /**
     * Format Speed
     *
     * @param target
     * @param seconds_per_meter
     * @return
     */
    public String formatSpeed(int target, double seconds_per_meter) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cueSpeed(seconds_per_meter);
            case TXT:
            case TXT_SHORT:
                return txtSpeed(seconds_per_meter, false);
            case TXT_LONG:
                return txtSpeed(seconds_per_meter, true);
        }
        return "";
    }

    /**
     * @param seconds_per_meter
     * @return string suitable for printing according to settings
     */
    private String txtSpeed(double meter_per_seconds, boolean includeUnit) {
        double distance_per_seconds = meter_per_seconds / base_meters;
        double distance_per_hour = distance_per_seconds * 3600;
        String str = String.format("%.1f", distance_per_hour);
        if (includeUnit == false)
            return str;
        else {
            int res = km ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
            return str +
                    resources.getString(res) +
                    "/" +
                    resources.getString(R.string.metrics_elapsed_h);
        }
    }

    private String cueSpeed(double meter_per_seconds) {
        double distance_per_seconds = meter_per_seconds / base_meters;
        double distance_per_hour = distance_per_seconds * 3600;
        String str = String.format("%.1f", distance_per_hour);
        StringBuilder s = new StringBuilder();
        s.append(str);
        s.append(" ").append(km ? cueResources.getQuantityString(R.plurals.cue_kilometer, 2) : cueResources.getQuantityString(R.plurals.cue_mile, 2));
        s.append(" ").append(cueResources.getString(R.string.cue_perhour));
        return s.toString();
    }

    /**
     * @param target
     * @param seconds_since_epoch
     * @return
     */
    public String formatDateTime(int target, long seconds_since_epoch) {
        // ignore target
        StringBuilder s = new StringBuilder();
        s.append(dateFormat.format(seconds_since_epoch * 1000)); // takes
                                                                 // milliseconds
                                                                 // as argument
        s.append(" ");
        s.append(timeFormat.format(seconds_since_epoch * 1000));
        return s.toString();
    }

    /**
     * @param target
     * @param meters
     * @return
     */
    public String formatDistance(int target, long meters) {
        switch (target) {
            case CUE:
            case CUE_LONG:
            case CUE_SHORT:
                return cueDistance(meters, false);
            case TXT:
                return cueDistanceInKmOrMiles(meters);
            case TXT_SHORT:
                return cueDistance(meters, true);
            case TXT_LONG:
                return Long.toString(meters) + " m";
        }
        return null;
    }

    private String cueDistanceInKmOrMiles(long meters) {
        DecimalFormat df = new DecimalFormat("#.00");
        return df.format(meters / base_meters);
    }

    private String cueDistance(long meters, boolean txt) {
        double base_val = km_meters; // 1km
        double decimals = 2;
        if (!km) {
            base_val = mi_meters;
        }

        StringBuilder s = new StringBuilder();
        if (meters >= base_val) {
            double base = ((double) meters) / base_val;
            double val = round(base, decimals);
            if (txt) {
                s.append(val).append(" ")
                        .append(resources.getString(km ? R.string.metrics_distance_km : R.string.metrics_distance_mi));
            } else {
                s.append(val).append(" ")
                        .append(cueResources.getQuantityString(km ? R.plurals.cue_kilometer : R.plurals.cue_mile, (int)val));
            }
        } else {
            s.append(meters);
            if (txt)
                s.append(" ").append("m");
            else
                s.append(" ").append(cueResources.getQuantityString(R.plurals.cue_meter, (int)meters));
        }
        return s.toString();
    }

    public String formatRemaining(int target, Dimension dimension, double value) {
        switch (dimension) {
            case DISTANCE:
                return formatRemainingDistance(target, value);
            case TIME:
                return formatRemainingTime(target, value);
            case PACE:
            case SPEED:
                break;
            case HR:
                break;
            default:
                break;
        }
        return "";
    }

    public String formatRemainingTime(int target, double value) {
        return formatElapsedTime(target, Math.round(value));
    }

    public String formatRemainingDistance(int target, double value) {
        return formatDistance(target, Math.round(value));
    }

    public String formatName(String first, String last) {
        if (first != null && last != null)
            return first + " " + last;
        else if (first == null && last != null)
            return last;
        else if (first != null && last == null)
            return first;
        return "";
    }

    public String formatTime(int target, long seconds_since_epoch) {
        return timeFormat.format(seconds_since_epoch * 1000);
    }

    public static double round(double base, double decimals) {
        double exp = Math.pow(10, decimals);
        return Math.round(base * exp) / exp;
    }

    public static double getUnitMeters(Context mContext) {
        return getUnitMeters(mContext.getResources(),
                PreferenceManager.getDefaultSharedPreferences(mContext));
    }
}
