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
import android.speech.tts.UtteranceProgressListener;

public class RUTextToSpeech {

	boolean mute = true;
	boolean trace = true;
	TextToSpeech textToSpeech;
	
	public RUTextToSpeech(TextToSpeech tts, String mute) {
		this.textToSpeech = tts;
		this.mute = ! ("yes".equalsIgnoreCase(mute));
	}

	int speak(String text, int queueMode, HashMap<String, String> params, Context ctx) {
		if (trace) {
			System.err.println("should mute: "+ mute);
			System.err.println("speak: " + text);
		}
		
		if (mute && ctx != null) {
			final AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
			int result = am.requestAudioFocus(null,// afChangeListener,
					AudioManager.STREAM_MUSIC,
					AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

			if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				textToSpeech.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
					@Override
					public void onUtteranceCompleted(String utteranceId) {
						if("RUTextTospeech".equalsIgnoreCase(utteranceId)){
							am.abandonAudioFocus(null);
						}
					}
				});
				if(params == null){
					params = new HashMap<String,String>();
				}
				params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "RUTextTospeech");
				int res = textToSpeech.speak(text, queueMode, params);
// This is how it should be done in newer versions of android.				
//				textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
//					@Override
//					public void onStart(String utteranceId) {						
//					}
//					@Override
//					public void onError(String utteranceId) {
//						am.abandonAudioFocus(null);// afChangeListener						
//					}
//					@Override
//					public void onDone(String utteranceId) {
//						am.abandonAudioFocus(null);// afChangeListener
//					}
//				});
				return res;
			}
			return TextToSpeech.ERROR;			
		} else {
		  return textToSpeech.speak(text,  queueMode, params);
		}
	}
}
