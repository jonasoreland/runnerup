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
import org.runnerup.common.util.Constants;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.SpeedUnit;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class Formatter implements OnSharedPreferenceChangeListener {

    private final Context context;
    private final Resources resources;
    private final LocaleResources cueResources;
    private final SharedPreferences sharedPreferences;
    private final java.text.DateFormat dateFormat;
    private final java.text.DateFormat timeFormat;
    private final java.text.DateFormat monthFormat;
    private final java.text.DateFormat dayOfMonthFormat;
    //private HRZones hrZones = null;

    private boolean metric = true;
    private String base_unit = "km";
    private double base_meters = km_meters;
    private final boolean unitCue;

    public final static double km_meters = 1000.0;
    public final static double mi_meters = 1609.34;
    private final static double meters_per_foot = 0.3048;

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
        monthFormat = new SimpleDateFormat("MMM yyyy", cueResources.defaultLocale);
        dayOfMonthFormat = new SimpleDateFormat("E d", cueResources.defaultLocale);
        unitCue = sharedPreferences.getBoolean(cueResources.getString(R.string.cueinfo_units), true);
        //hrZones = new HRZones(context);

        setUnit();
    }

    private class LocaleResources {
        final Resources resources;
        final Configuration configuration;
        final Locale defaultLocale;
        final Locale audioLocale;

        LocaleResources(Context ctx, Locale configAudioLocale) {
            resources = ctx.getResources();
            configuration = resources.getConfiguration();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    !ctx.getResources().getConfiguration().getLocales().isEmpty()) {
                defaultLocale = configuration.getLocales().get(0);
            } else {
                defaultLocale = configuration.locale;
            }

            if (configAudioLocale == null) {
                audioLocale = defaultLocale;
            } else {
                audioLocale = configAudioLocale;
            }
        }

        void setLang(Locale locale) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                configuration.setLocale(locale);
            } else {
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
            return new Locale(prefs.getString(res.getString(R.string.pref_audio_lang), "en"));
        }
        return null;
    }

    private LocaleResources getCueLangResources(Context ctx) {
        Locale loc = getAudioLocale(ctx);
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
        metric = getUseMetric(context.getResources(), sharedPreferences, null);

        if (metric) {
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
                return resources.getString(metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi);
        }
        return null;
    }

    public static boolean getUseMetric(Resources res, SharedPreferences prefs, Editor editor) {
        boolean _km;
        String unit = prefs.getString(res.getString(R.string.pref_unit), null);
        if (unit == null)
            _km = guessDefaultUnit(res, editor);
        else if (unit.contentEquals("km"))
            _km = true;
        else if (unit.contentEquals("mi"))
            _km = false;
        else
            _km = guessDefaultUnit(res, editor);

        return _km;
    }

    private static boolean guessDefaultUnit(Resources res, Editor editor) {
        String countryCode = Locale.getDefault().getCountry();
        Log.e("Formatter", "guessDefaultUnit: countryCode: " + countryCode);
        if (countryCode.equals(""))
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

    /**
     * Gets the user's preferred Speedunit
     *
     * @param context Context element to access configuration
     * @return Configured Speed Unit (falls back to pace, if the configured value is invalid)
     */
    public static SpeedUnit getPreferredSpeedUnit(Context context){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // use either pace or speed according to the user's preference
        String speedUnit = prefs.getString(context.getResources().getString(R.string.pref_speedunit), SpeedUnit.PACE.getValue());
        assert speedUnit != null;   // may not happen
        switch(speedUnit){
            case Constants.SPEED_UNIT.SPEED:
                return SpeedUnit.SPEED;
            case Constants.SPEED_UNIT.PACE:
            default:
                return SpeedUnit.PACE;
        }
    }

    public double getUnitMeters() {
        return this.base_meters;
    }

    public static double getUnitMeters(Resources res, SharedPreferences prefs) {
        if (getUseMetric(res, prefs, null))
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
        int hours = 0;
        int minutes = 0;
        if (seconds >= 3600) {
            hours = (int) (seconds / 3600);
            seconds -= hours * 3600;
        }
        if (seconds >= 60) {
            minutes = (int) (seconds / 60);
            seconds -= minutes * 60L;
        }

        StringBuilder s = new StringBuilder();
        if (unitCue) {
            if (hours > 0) {
                includeDimension = true;
                s.append(cueResources.getQuantityString(R.plurals.cue_hour, hours, hours));
            }
            if (minutes > 0) {
                if (hours > 0)
                    s.append(" ");

                includeDimension = true;
                s.append(cueResources.getQuantityString(R.plurals.cue_minute, minutes, minutes));
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
        } else {
            // HH:MM:SS would almost work except that some tts reports "time is HH MM SS"
            // Always two digits if there a higher number
            if (hours > 0) {
                String str = String.format(cueResources.audioLocale, "%02d", minutes);
                String sec = String.format(cueResources.audioLocale, "%02d", seconds);
                s.append(hours)
                        // Add extra delay as well as avoid interpreting "01 05 58" as "January 5th 58"
                        .append("\n")
                        .append(str)
                        .append(" ")
                        .append(sec);
            } else if (minutes > 0) {
                String sec = String.format(cueResources.audioLocale, "%02d", seconds);
                s.append(minutes)
                        .append(" ")
                        .append(sec);
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
        int val2 = (int) Math.round(heart_rate);
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                if (unitCue) {
                    return cueResources.getQuantityString(R.plurals.cue_bpm, val2, val2);
                } else {
                    return Integer.toString(val2);
                }
            case TXT:
            case TXT_SHORT:
            case TXT_LONG:
                return Integer.toString(val2);
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
    public String formatCadence(Format target, double val) {
        int val2 = (int) Math.round(val);
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                if (unitCue) {
                    return cueResources.getQuantityString(R.plurals.cue_rpm, val2, val2);
                } else {
                    return Integer.toString(val2);
                }
            case TXT:
            case TXT_SHORT:
            case TXT_LONG:
                return Integer.toString(val2);
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
                String s = Integer.toString((int) Math.floor(hrZone));
                if (unitCue) {
                    s = cueResources.getString(R.string.heart_rate_zone) + " " + s;
                }
                return s;
            case CUE:
            case CUE_LONG:
                s = Double.toString(Math.floor(10.0 * hrZone) / 10.0);
                if (unitCue) {
                    s = cueResources.getString(R.string.heart_rate_zone) + " " + s;
                }
                return s;
        }
        return "";
    }

    /**
     * Format pace from raw pace
     * Most of RU handles pace separately instead of just storing speed and formatting
     *
     * @param target
     * @param seconds_per_meter
     * @return
     */
    public String formatPace(Format target, double seconds_per_meter) {
        double meters_per_second = (seconds_per_meter == 0 || Double.isNaN(seconds_per_meter)) ?
                Double.NaN : 1/seconds_per_meter;
        return formatPaceSpeed(target, meters_per_second);
    }

    /**
     * Returns either a formatted value in minutes per kilometer or kilometer per hour
     * depending on the user's preference
     *
     * @param target the target format [cue or txt] and length
     * @param meters_per_second speed in m/s
     * @return display value
     */
    public String formatVelocityByPreferredUnit(Format target, double meters_per_second) {
        String paceTextUnit = this.sharedPreferences
                .getString(context.getResources().getString(R.string.pref_speedunit), SpeedUnit.PACE.getValue());
        assert paceTextUnit != null;
        if(paceTextUnit.contentEquals(SpeedUnit.PACE.getValue())) {
            return this.formatPaceSpeed(target, meters_per_second);
        } else {
            return this.formatSpeed(target, meters_per_second);
        }
    }

    /**
     * Returns a label for Pace or Speed depending on the user's preference
     *
     * @return value
     */
    public String formatVelocityLabel() {
        String paceTextUnit = this.sharedPreferences
                .getString(context.getResources().getString(R.string.pref_speedunit), SpeedUnit.PACE.getValue());
        assert paceTextUnit != null;
        if (paceTextUnit.contentEquals(SpeedUnit.PACE.getValue())) {
            return this.context.getString(R.string.Pace);
        } else {
            return this.context.getString(R.string.Speed);
        }
    }

    /**
     * Format pace from speed
     *
     * @param target
     * @param meters_per_second speed in m/s
     * @return
     */
    public String formatPaceSpeed(Format target, double meters_per_second) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cuePace(meters_per_second);
            case TXT:
            case TXT_SHORT:
                return txtPace(meters_per_second, false);
            case TXT_LONG:
                return txtPace(meters_per_second, true);
        }
        return "";
    }

    /**
     * @return pace unit string
     */
    String getVelocityUnit(Context context) {//Resources resources, SharedPreferences sharedPreferences) {
        int du = metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
        switch(getPreferredSpeedUnit(context)){
            case SPEED:
                return resources.getString(du) + "/" + resources.getString(R.string.metrics_elapsed_h);
            case PACE:
            default:
                return resources.getString(R.string.metrics_elapsed_min) + "/" + resources.getString(du);
        }
    }

    /**
     * @param meters_per_second
     * @return string suitable for printing according to settings
     */
    private String txtPace(double meters_per_second, boolean includeUnit) {
        String str;
        final double txtStoppedPace = km_meters / 60 / 60;
        if (Double.isNaN(meters_per_second) || meters_per_second <= txtStoppedPace) {
            str = "--:--";
        } else {
            long val = Math.round(base_meters / meters_per_second);
            str = DateUtils.formatElapsedTime(val);
        }
        if (includeUnit) {
            str = str + " /" + resources.getString((metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi));
        }
        return str;
    }

    private String cuePace(double meters_per_second) {
        // Cue cut-off for stopped is set to some minimal meaningful reportable pace
        final double cueStoppedPace = km_meters / 20 / 60;
        if (Double.isNaN(meters_per_second) || meters_per_second <= cueStoppedPace) {
            return cueResources.getString(R.string.cue_stopped);
        }
        int seconds_per_unit = (int) Math.round(base_meters / meters_per_second);
        int minutes_per_unit = 0;
        if (seconds_per_unit >= 60) {
            minutes_per_unit = seconds_per_unit / 60;
            seconds_per_unit -= minutes_per_unit * 60;
        }

        StringBuilder s = new StringBuilder();
        if (unitCue) {
            if (minutes_per_unit > 0) {
                s.append(cueResources.getQuantityString(R.plurals.cue_minute, minutes_per_unit, minutes_per_unit));
            }
            if (seconds_per_unit > 0) {
                if (minutes_per_unit > 0)
                    s.append(" ");
                s.append(cueResources.getQuantityString(R.plurals.cue_second, seconds_per_unit, seconds_per_unit));
            }
            s.append(" ").append(cueResources.getString(metric ? R.string.cue_perkilometer : R.string.cue_permile));
        } else {
            String secs = String.format(cueResources.audioLocale, "%02d", seconds_per_unit);
            s.append(minutes_per_unit)
                    .append(" ")
                    .append(secs);
        }
        return s.toString();
    }

    /**
     * Format Speed
     *
     * @param target
     * @param meters_per_second
     * @return
     */
    private String formatSpeed(Format target, double meters_per_second) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cueSpeed(meters_per_second);

            case TXT:
            case TXT_SHORT:
                return txtSpeed(meters_per_second, false);
            case TXT_LONG:
                return txtSpeed(meters_per_second, true);
        }
        return "";
    }

    /**
     * @param meters_per_second
     * @return string suitable for printing according to settings
     */
    private String txtSpeed(double meters_per_second, boolean includeUnit) {
        if (Double.isNaN(meters_per_second)) {
            return "-";
        }

        double distance_per_hour = meters_per_second * 3600 / base_meters;
        String str = String.format(cueResources.defaultLocale, "%.1f", distance_per_hour);
        if (!includeUnit)
            return str;
        else {
            int res = metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
            return str +
                    " " +
                    resources.getString(res) +
                    "/" +
                    resources.getString(R.string.metrics_elapsed_h);
        }
    }

    private String cueSpeed(double meters_per_second) {
        if (Double.isNaN(meters_per_second)) {
            return cueResources.getString(R.string.cue_stopped);
        }

        double distance_per_hour = meters_per_second  * 3600 / base_meters;
        String str = String.format(cueResources.audioLocale, "%.1f", distance_per_hour);
        if (unitCue) {
            return cueResources.getQuantityString(metric ? R.plurals.cue_kilometers_per_hour : R.plurals.cue_miles_per_hour,
                    (int)distance_per_hour, str);
        } else {
            return str;
        }
    }

    /**
     * @param date date to format
     * @return month and year as a string (e.g. "Feb 2000")
     */
    public String formatMonth(Date date) {
        return monthFormat.format(date);
    }

    /**
     * @param date date to format
     * @return day of the week and day of the month as a string (e.g. "Fri 13")
     */
    public String formatDayOfMonth(Date date) {
        return dayOfMonthFormat.format(date);
    }

    /**
     * @param seconds_since_epoch
     * @return date and time as a string
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
     * Format "elevation" like distances
     * @param target
     * @param meters
     * @return Formatted string with unit
     */
    public String formatElevation(Format target, double meters) {
        // TODO add (plural) strings and handle Format, cues
        DecimalFormat df = new DecimalFormat("#.0");
        if (metric) {
            return df.format(meters) + " m";
        } else {
            return df.format(meters / meters_per_foot) + " ft";
        }
    }

    /**
     * @param target
     * @param meters
     * @return
     */
    public String formatDistance(Format target, long meters) {
        switch (target) {
            case CUE:
            case CUE_LONG:
            case CUE_SHORT:
                return formatDistance(meters, false);
            case TXT:
                return formatDistanceInKmOrMiles(meters);
            case TXT_SHORT:
                return formatDistance(meters, true);
            case TXT_LONG:
                return meters + " m";
        }
        return null;
    }

    private double getRoundedDistanceInKmOrMiles(long meters) {
        double decimals = 2;
        return round(meters/base_meters, decimals);
    }

    private String formatDistanceInKmOrMiles(long meters) {
        return String.format(cueResources.defaultLocale, "%.2f", getRoundedDistanceInKmOrMiles(meters));
    }

    private String formatDistance(long meters, boolean txt) {
        String res;
        if (meters >= base_meters * 0.99) {
            double val = getRoundedDistanceInKmOrMiles(meters);
            if (txt) {
                res = String.format(cueResources.defaultLocale, "%.2f %s", val,
                        resources.getString(metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi));
            } else {
                // Get a localized presentation string, used with the localized plurals string
                String val2;
                if (val < 10) {
                    val2 = String.format(cueResources.audioLocale, "%.2f", val);
                } else {
                    val2 = String.format(cueResources.audioLocale, "%.1f", val);
                }
                if (unitCue) {
                    res = cueResources.getQuantityString(metric ? R.plurals.cue_kilometer : R.plurals.cue_mile, (int)val, val2);
                } else {
                    res = val2;
                }
            }
        } else {
            // Present distance in meters if less than 0.99 km or mi (no strings for feet)
            if (txt) {
                res = String.format(cueResources.defaultLocale, "%d %s", meters, resources.getString(R.string.metrics_distance_m));
            }
            else {
                if (unitCue) {
                    res = cueResources.getQuantityString(R.plurals.cue_meter, (int) meters, meters);
                } else {
                    res = Long.toString(meters);
                }
            }
        }
        return res;
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
