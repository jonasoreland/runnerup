/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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
import android.widget.Toast;

import org.runnerup.BuildConfig;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Event;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

import java.util.HashMap;


public class AudioFeedback extends Feedback {

    private final int msgId;
    private String msgTxt = null;
    final private Event event = null;
    final Scope scope;
    final Dimension dimension;
    private Intensity intensity;
    RUTextToSpeech textToSpeech;
    Formatter formatter;

    public AudioFeedback(int msgId) {
        super();
        this.msgId = msgId;
        scope = null;
        this.dimension = null;
        this.intensity = null;
        // The trigger should have Event.STARTED
    }

    public AudioFeedback(Scope scope) {
        this(scope, null);
    }

    public AudioFeedback(Scope scope, Dimension dimension) {
        super();
        this.msgId = -1;
        this.scope = scope;
        this.dimension = dimension;
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
        if (!(_other instanceof AudioFeedback))
            return false;

        AudioFeedback other = (AudioFeedback) _other;

        return this.msgId == other.msgId &&
                //this.event == other.event &&
                this.scope == other.scope &&
                this.dimension == other.dimension;
    }

    public Scope getScope() { return scope; }

    // Allow override
    String getCue(Workout w, Context ctx) {
        String msg = null;
        if (msgId > 0) {
            if (msgTxt == null) {
                msgTxt = formatter.getCueString(msgId);
            }
            msg = msgTxt;
        } else {
            // Note: currently, event is always null
            if (event != null && scope != null) {
                msg = formatter.getCueString(event.getCueId());
            } else if (event != null && intensity != null) {
                msg = formatter.getCueString(intensity.getCueId()) + " " + formatter.getCueString(event.getCueId());
            } else if (dimension != null && scope != null) {
                if (w.isEnabled(dimension, scope)) {
                    double val = w.get(scope, dimension); // SI
                    msg = formatter.format(Formatter.Format.CUE_LONG, dimension, val);
                }
            } else if (scope != null) {
                msg = formatter.getCueString(scope.getCueId());
            }
        }
        return msg;
    }

    @Override
    public void emit(Workout w, Context ctx) {
        String msg = getCue(w, ctx);
        if (msg != null && textToSpeech != null) {
            if (BuildConfig.DEBUG) {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
            }
            textToSpeech.speak(msg, TextToSpeech.QUEUE_ADD, null);
        }
    }
}
