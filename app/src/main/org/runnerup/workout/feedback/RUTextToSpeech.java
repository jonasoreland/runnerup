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

package org.runnerup.workout.feedback;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import org.runnerup.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;


public class RUTextToSpeech {

    private static final String UTTERANCE_ID = "RUTextTospeech";
    private final boolean mute;
    private final TextToSpeech textToSpeech;
    private final AudioManager audioManager;
    private long id = (long) (System.nanoTime() + (1000 * Math.random()));

    class Entry {
        final String text;
        final HashMap<String, String> params;

        public Entry(String text, HashMap<String, String> params) {
            this.text = text;
            this.params = params;
        }
    }

    private final HashSet<String> cueSet = new HashSet<>();
    private final ArrayList<Entry> cueList = new ArrayList<>();

    public RUTextToSpeech(TextToSpeech tts, boolean mute_, Context context) {
        this.textToSpeech = tts;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.mute = mute_;
        Locale locale = Formatter.getAudioLocale(context);
        if (locale != null) {
            int res;
            switch((res = tts.isLanguageAvailable(locale))) {
                case TextToSpeech.LANG_AVAILABLE:
                case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                    res = tts.setLanguage(locale);
                    Log.e(getClass().getName(), "setLanguage(" + locale.getDisplayLanguage() + ") => " + res);
                    break;
                case TextToSpeech.LANG_MISSING_DATA:
                case TextToSpeech.LANG_NOT_SUPPORTED:
                    Log.e(getClass().getName(), "setLanguage("+locale.getDisplayLanguage()+") => MISSING: " + res);
                    break;
            }
        }

        if (this.mute) {
            UtteranceCompletion.setUtteranceCompletedListener(tts, this);
        }
    }

    private String getId(String text) {
        long val;
        synchronized (this) {
            val = this.id;
            this.id++;
        }
        return UTTERANCE_ID + val;
    }

    public Boolean isAvailable() {
        return textToSpeech != null;
    }

    @SuppressWarnings("UnusedReturnValue")
    int speak(String text, int queueMode, HashMap<String, String> params) {
        if (!isAvailable()) {
            return 0;
        }

        final boolean trace = true;
        if (queueMode == TextToSpeech.QUEUE_FLUSH) {
            //Unused in RU
            //noinspection ConstantConditions
            if (trace) {
                Log.e(getClass().getName(), "speak (mute: " + mute + "): " + text);
            }
            // speak directly
            if (mute) {
                return speakWithMute(text, queueMode, params);
            } else {
                return textToSpeech.speak(text, queueMode, params);
            }
        } else {
            if (!cueSet.contains(text)) {
                //noinspection ConstantConditions
                if (trace) {
                    Log.e(getClass().getName(), "buffer speak: " + text);
                }
                cueSet.add(text);
                cueList.add(new Entry(text, params));
            } else {
                //noinspection ConstantConditions
                if (trace) {
                    Log.e(getClass().getName(), "skip buffer (duplicate) speak: " + text);
                }
            }
            return 0;
        }
    }

    /**
     * Requests audio focus before speaking, if no focus is given nothing is
     * said.
     *
     * @param text
     * @param queueMode
     * @param params
     * @return
     */
    private int speakWithMute(String text, int queueMode,
            HashMap<String, String> params) {
        if (!isAvailable()) {
            return TextToSpeech.ERROR;
        }

        if (requestFocus()) {
            final String utId = getId(text);
            outstanding.add(utId);

            if (params == null) {
                params = new HashMap<>();
            }
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utId);
            int res = textToSpeech.speak(text, queueMode, params);
            if (res == TextToSpeech.ERROR) {
                outstanding.remove(utId);
            }
            if (outstanding.isEmpty()) {
                audioManager.abandonAudioFocus(null);
            }
            return res;
        }
        Log.e(getClass().getName(), "Could not get audio focus.");
        return TextToSpeech.ERROR;
    }

    private final HashSet<String> outstanding = new HashSet<>();

    void utteranceCompleted(String id) {
        outstanding.remove(id);
        if (outstanding.isEmpty()) {
            audioManager.abandonAudioFocus(null);
        }
    }

    private boolean requestFocus() {
        int result = audioManager.requestAudioFocus(
                null,// afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    public void emit() {
        if (!isAvailable()) {
            return;
        }
        if (cueSet.isEmpty()) {
            return;
        }
        if (mute && requestFocus()) {
            for (Entry e : cueList) {
                final String utId = getId(e.text);
                outstanding.add(utId);

                HashMap<String, String> params = e.params;
                if (params == null) {
                    params = new HashMap<>();
                }
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utId);
                int res = textToSpeech.speak(e.text, TextToSpeech.QUEUE_ADD, params);
                if (res == TextToSpeech.ERROR) {
                    Log.e(getClass().getName(), "res == ERROR emit() text: " + e.text + ", utId: " + utId
                            + ") outstanding.size(): " + outstanding.size());
                    outstanding.remove(utId);
                }
            }
            if (outstanding.isEmpty()) {
                audioManager.abandonAudioFocus(null);
            }
        } else {
            for (Entry e : cueList) {
                textToSpeech.speak(e.text, TextToSpeech.QUEUE_ADD, e.params);
            }
        }
        cueSet.clear();
        cueList.clear();
    }
}

//Explicit check for Android 4.3
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class UtteranceCompletion {

    @SuppressLint("ObsoleteSdkInt")
    public static void setUtteranceCompletedListener(
            TextToSpeech tts, final RUTextToSpeech ruTextToSpeech) {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onDone(String utteranceId) {
                ruTextToSpeech.utteranceCompleted(utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                ruTextToSpeech.utteranceCompleted(utteranceId);
            }

            @Override
            public void onStart(String utteranceId) {
            }
        });
    }
}
