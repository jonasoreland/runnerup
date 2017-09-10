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


public class Formatter implements OnSharedPreferenceChangeListener {

    private Context context = null;
    private Resources resources = null;
    private LocaleResources cueResources = null;
    private SharedPreferences sharedPreferences = null;
    private java.text.DateFormat dateFormat = null;
    private java.text.DateFormat timeFormat = null;
    //private HRZones hrZones = null;
    private final Locale defaultLocale;

    private boolean km = true;
    private String base_unit = "km";
    private double base_meters = km_meters;

    public final static double km_meters = 1000.0;
    public final static double mi_meters = 1609.34;
    //public final static double FEET_PER_METER = 3.2808;

    public enum Format {
        CUE,       // for text to speech
        CUE_SHORT, // brief for tts
        CUE_LONG,  // long for tts
        TXT,       // same as TXT_SHORT but without unit
        TXT_SHORT, // brief for printing
        TXT_LONG,  // long for printing
        TXT_TIMESTAMP, // For current time e.g 13:41:24
    }

    public Formatter(Context ctx) {
        context = ctx;
        resources = ctx.getResources();
        cueResources = getCueLangResources(ctx);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
        timeFormat = android.text.format.DateFormat.getTimeFormat(ctx);
        //hrZones = new HRZones(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !ctx.getResources().getConfiguration().getLocales().isEmpty()) {
            defaultLocale = ctx.getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            defaultLocale = ctx.getResources().getConfiguration().locale;
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    !ctx.getResources().getConfiguration().getLocales().isEmpty()) {
                defaultLocale = configuration.getLocales().get(0);
            } else {
                //noinspection deprecation
                defaultLocale = configuration.locale;
            }
            audioLocale = locale;
        }

        void setLang(Locale locale) {
            if (Build.VERSION.SDK_INT >= 17) {
                configuration.setLocale(locale);
            } else {
                //noinspection deprecation
                configuration.locale = locale;
            }
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        }

        public String getString(int id) throws Resources.NotFoundException {
            setLang(audioLocale);
            String result = resources.getString(id);
            setLang(defaultLocale);
            return result;
        }

        //General getQuantityString accepts "Object ...", limit to exactly one argument (current use) to avoid runtime crashes
        public String getQuantityString(int id, int quantity, Object formatArgs) throws Resources.NotFoundException {
            setLang(audioLocale);
            String result = resources.getQuantityString(id, quantity, formatArgs);
            setLang(defaultLocale);
            return result;
        }
    }

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

    private LocaleResources getCueLangResources(Context ctx) {
        Locale loc = getAudioLocale(ctx);
        if (loc == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    !ctx.getResources().getConfiguration().getLocales().isEmpty()) {
                loc = ctx.getResources().getConfiguration().getLocales().get(0);
            } else {
                //noinspection deprecation
                loc = ctx.getResources().getConfiguration().locale;
            }
        }
        return new LocaleResources(ctx, loc);
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

    public String getDistanceUnit(Format target) {
        switch (target) {
            case CUE:
            case CUE_LONG:
            case CUE_SHORT:
            case TXT_LONG:
                //No string for long - not used
                // return resources.getString(km ? R.plurals.cue_kilometer : R.plurals.cue_mile);
            case TXT:
            case TXT_SHORT:
                return resources.getString(km ? R.string.metrics_distance_km : R.string.metrics_distance_mi);
        }
        return null;
    }

    public static boolean getUseKilometers(Resources res, SharedPreferences prefs, Editor editor) {
        boolean _km;
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
        } else {
            if (editor != null)
                editor.putString(key, "km");
        }
        return true;
    }

    public double getUnitMeters() {
        return this.base_meters;
    }

    public int getUnitDecimals() {
        return 3;
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

    public String format(Format target, Dimension dimension, double value) {
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
            case CAD:
                return formatCadence(target, value);
            case TEMPERATURE:
                return formatCadence(target, value);//TODO
            case PRESSURE:
                return formatCadence(target, value);//TODO
        }
        return "";
    }

    public String formatElapsedTime(Format target, long seconds) {
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
            case TXT_TIMESTAMP:
                return formatTime(seconds);
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
            s.append(cueResources.getQuantityString(R.plurals.cue_hour, (int) hours, (int) hours));
        }
        if (minutes > 0) {
            if (hours > 0)
                s.append(" ");
            includeDimension = true;
            s.append(cueResources.getQuantityString(R.plurals.cue_minute, (int) minutes, (int) minutes));
        }
        if (seconds > 0) {
            if (hours > 0 || minutes > 0)
                s.append(" ");

            if (includeDimension) {
                s.append(cueResources.getQuantityString(R.plurals.cue_second, (int) seconds, (int) seconds));
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
    public String formatHeartRate(Format target, double heart_rate) {
        int bpm = (int) Math.round(heart_rate);
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cueResources.getQuantityString(R.plurals.cue_bpm, bpm, bpm);
            case TXT:
            case TXT_SHORT:
            case TXT_LONG:
                return Integer.toString(bpm);
        }
        return "";
    }

    /**
     * Format cadence
     *
     * @param target
     * @param val
     * @return
     */
    private String formatCadence(Format target, double val) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return Integer.toString((int) Math.round(val)) + " "
                        + cueResources.getQuantityString(R.plurals.cue_bpm, (int) val, (int) val);
            case TXT:
            case TXT_SHORT:
            case TXT_LONG:
                return Integer.toString((int) Math.round(val));
        }
        return "";
    }

    private String formatHeartRateZone(Format target, double hrZone) {
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
    public String formatPace(Format target, double seconds_per_meter) {
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
     * @return pace unit string
     */
    public String getPaceUnit() {//Resources resources, SharedPreferences sharedPreferences) {
        int du = km ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
        return resources.getString(R.string.metrics_elapsed_min) + "/" + resources.getString(du);
    }

    /**
     * @param seconds_per_meter
     * @return string suitable for printing according to settings
     */
    private String txtPace(double seconds_per_meter, boolean includeUnit) {
        long val = Math.round(base_meters * seconds_per_meter);
        String str = DateUtils.formatElapsedTime(val);
        if (!includeUnit)
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
            s.append(cueResources.getQuantityString(R.plurals.cue_hour, (int) hours_per_unit, (int) hours_per_unit));
        }
        if (minutes_per_unit > 0) {
            if (hours_per_unit > 0)
                s.append(" ");
            s.append(cueResources.getQuantityString(R.plurals.cue_minute, (int) minutes_per_unit, (int) minutes_per_unit));
        }
        if (seconds_per_unit > 0) {
            if (hours_per_unit > 0 || minutes_per_unit > 0)
                s.append(" ");
            s.append(cueResources.getQuantityString(R.plurals.cue_second, (int) seconds_per_unit, (int) seconds_per_unit));
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
    private String formatSpeed(Format target, double seconds_per_meter) {
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
     * @param meter_per_seconds
     * @return string suitable for printing according to settings
     */
    private String txtSpeed(double meter_per_seconds, boolean includeUnit) {
        double distance_per_seconds = meter_per_seconds / base_meters;
        double distance_per_hour = distance_per_seconds * 3600;
        String str = String.format(defaultLocale, "%.1f", distance_per_hour);
        if (!includeUnit)
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
        String fmtDistPerHour = txtSpeed(meter_per_seconds, false);
        return cueResources.getQuantityString(km ? R.plurals.cue_kilometers_per_hour : R.plurals.cue_miles_per_hour,
                (int) distance_per_hour, fmtDistPerHour);
    }

    /**
     * @param seconds_since_epoch
     * @return
     */
    public String formatDateTime(long seconds_since_epoch) {
        // ignore target
        // milliseconds
        // as argument
        return dateFormat.format(seconds_since_epoch * 1000) +
                " " +
                timeFormat.format(seconds_since_epoch * 1000);
    }

    /**
     * @param target
     * @param value
     * @return
     */
    public String formatDistance(Format target, double value) {
        return formatDistance(target, value, 0);
    }

    /**
     * @param target
     * @param value
     * @param decimals
     * @return
     */
    public String formatDistance(Format target, double value, int decimals) {
        Long meters = Math.round(value);
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
                int i = getUnitDecimals(); //Decimals to display
                String unit;
                if (meters == 0) {
                    //No need for decimals
                    decimals = 0;
                    unit = getUnitString();
                } else if (meters < getUnitMeters()) {
                    //Short unit, should be feet or yard for non metric for <1.0 mi
                    unit = resources.getString(R.string.metrics_distance_m);
                } else {
                    decimals += getUnitDecimals();
                    unit = getUnitString();
                    value = value / getUnitMeters();
                }
                return String.format(Locale.getDefault(), "%." + decimals + "f %s", value, unit);

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
                        .append(cueResources.getQuantityString(km ? R.plurals.cue_kilometer : R.plurals.cue_mile, (int) val, (int) val));
            }
        } else {
            if (txt)
                s.append(meters).append(" ").append(resources.getString(R.string.metrics_distance_m));
            else
                s.append(cueResources.getQuantityString(R.plurals.cue_meter, (int) meters, (int) meters));
        }
        return s.toString();
    }

    public String formatRemaining(Format target, Dimension dimension, double value) {
        switch (dimension) {
            case DISTANCE:
                return formatRemainingDistance(target, value);
            case TIME:
                return formatRemainingTime(target, value);
            case PACE:
            case SPEED:
            case HR:
            case CAD:
            case TEMPERATURE:
            case PRESSURE:
            default:
                break;
        }
        return "";
    }

    private String formatRemainingTime(Format target, double value) {
        return formatElapsedTime(target, Math.round(value));
    }

    private String formatRemainingDistance(Format target, double value) {
        return formatDistance(target, Math.round(value));
    }

    public String formatName(String first, String last) {
        if (first != null && last != null)
            return first + " " + last;
        else if (first == null && last != null)
            return last;
        else if (first != null /*&& last == null*/)
            return first;
        return "";
    }

    private String formatTime(long seconds_since_epoch) {
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
