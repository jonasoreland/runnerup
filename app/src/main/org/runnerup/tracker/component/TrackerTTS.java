/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
package org.runnerup.tracker.component;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import org.runnerup.workout.Workout;
import org.runnerup.workout.feedback.RUTextToSpeech;

import java.util.HashMap;


public class TrackerTTS extends DefaultTrackerComponent {

    private TextToSpeech tts;

    private static final String NAME = "TTS";

    @Override
    public String getName() {
        return NAME;
    }

    private RUTextToSpeech rutts;

    @Override
    public ResultCode onInit(final Callback callback, final Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                callback.run(TrackerTTS.this, ResultCode.RESULT_OK);
            }
            else {
                callback.run(TrackerTTS.this, ResultCode.RESULT_ERROR);
            }
        });
        return ResultCode.RESULT_PENDING;
    }

    @Override
    public void onBind(HashMap<String, Object> bindValues) {
        Context ctx = (Context) bindValues.get(TrackerComponent.KEY_CONTEXT);
        Boolean mute = (Boolean) bindValues.get(Workout.KEY_MUTE);

        rutts = new RUTextToSpeech(tts, mute, ctx);

        bindValues.put(Workout.KEY_TTS, rutts);
    }

    @Override
    public boolean isConnected() {
        return rutts != null && rutts.isAvailable();
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        return ResultCode.RESULT_OK;
    }
}
