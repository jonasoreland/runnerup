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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import org.runnerup.R;
import org.runnerup.workout.Workout;
import org.runnerup.workout.feedback.RUTextToSpeech;

import java.util.HashMap;

/**
 * Created by jonas on 12/11/14.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class TrackerTTS extends DefaultTrackerComponent {

    private TextToSpeech tts;
    private Context context;
    private RUTextToSpeech ruTTS;

    public static final String NAME = "TTS";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ResultCode onInit(final Callback callback, final Context context) {
        this.context = context;
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    callback.run(TrackerTTS.this, ResultCode.RESULT_OK);
                }
                else {
                    callback.run(TrackerTTS.this, ResultCode.RESULT_ERROR);
                }
            }
        });
        return ResultCode.RESULT_PENDING;
    }

    @Override
    public void onBind(HashMap<String, Object> bindValues) {
        Context ctx = (Context) bindValues.get(TrackerComponent.KEY_CONTEXT);
        Boolean mute = (Boolean) bindValues.get(Workout.KEY_MUTE);
        bindValues.put(Workout.KEY_TTS, new RUTextToSpeech(tts, mute, ctx));
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        return ResultCode.RESULT_OK;
    }

    RUTextToSpeech getTTS(SharedPreferences prefs) {
        final boolean mute = prefs.getBoolean(context.getString(R.string.pref_mute_bool), false);
        ruTTS = new RUTextToSpeech(tts, mute, context);
        return ruTTS;
    }
}
