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

import android.content.Context;
import android.speech.tts.TextToSpeech;

import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

import java.util.HashMap;


public class AudioCountdownFeedback extends Feedback {

    private final Scope scope;
    private final Dimension dimension;
    private RUTextToSpeech textToSpeech;
    private Formatter formatter;

    public AudioCountdownFeedback(Scope s, Dimension d) {
        this.scope = s;
        this.dimension = d;
    }

    @Override
    public void onBind(Workout s, HashMap<String, Object> bindValues) {
        super.onBind(s, bindValues);
        if (bindValues.containsKey(Workout.KEY_TTS))
            textToSpeech = (RUTextToSpeech) bindValues.get(Workout.KEY_TTS);
        if (bindValues.containsKey(Workout.KEY_FORMATTER))
            formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
    }

    @Override
    public boolean equals(Feedback _other) {
        if (!(_other instanceof AudioCountdownFeedback))
            return false;

        AudioCountdownFeedback other = (AudioCountdownFeedback) _other;

        return scope == other.scope && dimension == other.dimension;
    }

    @Override
    public void emit(Workout w, Context ctx) {
        double remaining = w.getRemaining(scope, dimension); // SI

        if (remaining > 0) {
            String msg = formatter.formatRemaining(Formatter.Format.CUE_SHORT, dimension, remaining);
            textToSpeech.speak(msg, TextToSpeech.QUEUE_ADD, null);
        }
    }
}
