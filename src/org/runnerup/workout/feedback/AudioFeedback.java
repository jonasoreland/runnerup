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

import java.util.HashMap;

import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Event;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

import android.content.Context;
import android.content.res.Resources;
import android.speech.tts.TextToSpeech;

import android.os.Build;
import android.annotation.TargetApi;

@TargetApi(Build.VERSION_CODES.FROYO)
public class AudioFeedback extends Feedback {

	Event event = Event.STARTED;
	Scope scope = Scope.WORKOUT;
	Dimension dimension = Dimension.DISTANCE;
	Intensity intensity = null;
	RUTextToSpeech textToSpeech;
	Formatter formatter;
	
	public AudioFeedback(Scope scope, Event event) {
		super();
		this.scope = scope;
		this.event = event;
		this.dimension = null;
	}

	public AudioFeedback(Scope scope, Dimension dimension) {
		super();
		this.scope = scope;
		this.event = null;
		this.dimension = dimension;
	}

	public AudioFeedback(Intensity intensity, Event event) {
		super();
		this.scope = null;
		this.dimension = null;
		this.intensity = intensity;
		this.event = event;
	}

	@Override
	public void onBind(Workout s, HashMap<String, Object> bindValues) {
		super.onBind(s, bindValues);
		textToSpeech = (RUTextToSpeech) bindValues.get(Workout.KEY_TTS);
		formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
	}
	
	@Override
	public boolean equals(Feedback _other) {
		if (!(_other instanceof AudioFeedback))
			return false;

		AudioFeedback other = (AudioFeedback) _other;
		if (this.scope != other.scope)
			return false;

		if (this.event != other.event)
			return false;

		if (this.dimension != other.dimension)
			return false;

		return true;
	}

	protected String getCue(Workout w, Context ctx) {
		String msg = null;
		if (event != null && scope != null) {
			msg = scope.getCue(ctx) + " " + event.getCue(ctx);
		} else if (event != null && intensity != null) {
			Resources res = ctx.getResources();
			msg = res.getString(intensity.getCueId(), "") + " " + event.getCue(ctx);
		} else if (dimension != null && scope != null && w.isEnabled(dimension)) {
			double val = w.get(scope, dimension); // SI
			msg = scope.getCue(ctx) + " " + formatter.format(Formatter.CUE_LONG, dimension, val);
		}
		return msg;
	}
	
	@Override
	public void emit(Workout w, Context ctx) {
		String msg = getCue(w, ctx);
		if (msg != null) {
			textToSpeech.speak(msg, TextToSpeech.QUEUE_ADD, null);
		}
	}
}
