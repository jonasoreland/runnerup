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

import java.util.HashMap;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;

public class RUTextToSpeech {

    private static final String UTTERANCE_ID = "RUTextTospeech";
    boolean mute = false;
    boolean trace = true;
    TextToSpeech textToSpeech;
    Context ctx;

    public RUTextToSpeech(TextToSpeech tts, String mute, Context context) {
        this.textToSpeech = tts;
        this.mute = "yes".equalsIgnoreCase(mute);
        this.ctx = context;
    }

    int speak(String text, int queueMode, HashMap<String, String> params) {
        if (trace) {
            System.err.println("should mute: " + mute);
            System.err.println("speak: " + text);
        }
        if (mute && ctx != null) {
            return speakWithMute(text, queueMode, params);
        } else {
            return textToSpeech.speak(text, queueMode, params);
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
    private int speakWithMute(String text, int queueMode, HashMap<String, String> params) {
        final AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int result = am.requestAudioFocus(null,// afChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            final String utId = UTTERANCE_ID + text.hashCode();
            //Should use setOnUtteranceProgressListener, if target is API version newer than 15.
            textToSpeech.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    if (utId.equalsIgnoreCase(utteranceId)) {
                        am.abandonAudioFocus(null);
                    }
                }
            });
            if (params == null) {
                params = new HashMap<String, String>();
            }
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utId);
            int res = textToSpeech.speak(text, queueMode, params);
            if (res == TextToSpeech.ERROR) {
                am.abandonAudioFocus(null);
            }
            return res;
        }
        System.err.println("Could not get audio focus.");
        return TextToSpeech.ERROR;
    }
}
