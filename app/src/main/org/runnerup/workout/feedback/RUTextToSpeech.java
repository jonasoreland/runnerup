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
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.runnerup.util.Formatter;

public class RUTextToSpeech {

  private static final String UTTERANCE_ID = "RUTextTospeech";
  private final boolean mute;
  private final TextToSpeech textToSpeech;
  private final AudioManager audioManager;
  private final AtomicBoolean hasAudioFocus = new AtomicBoolean(false);
  private long id = (long) (System.nanoTime() + (1000 * Math.random()));

  class Entry {
    final int prio;
    final boolean flush;
    final String text;
    final HashMap<String, String> params;
    final String id;

    public Entry(String text, UtterancePrio prio, boolean flush, HashMap<String, String> params, String id) {
      this.text = text;
      this.flush = flush;
      this.prio = prio.value;
      this.params = params;
      this.id = id;
    }
  }

  private final HashSet<String> cueSet = new HashSet<>();
  private final ArrayList<Entry> cueList = new ArrayList<>();

  public RUTextToSpeech(TextToSpeech tts, boolean mute_, Context context) {
    this.textToSpeech = tts;
    this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    this.mute = mute_;
    Locale locale = Formatter.getAudioLocale(context);
    if (tts != null && locale != null) {
      int res;
      switch ((res = tts.isLanguageAvailable(locale))) {
        case TextToSpeech.LANG_AVAILABLE:
        case TextToSpeech.LANG_COUNTRY_AVAILABLE:
        case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
          res = tts.setLanguage(locale);
          Log.d(getClass().getName(), "setLanguage(" + locale.getDisplayLanguage() + ") => " + res);
          break;
        case TextToSpeech.LANG_MISSING_DATA:
        case TextToSpeech.LANG_NOT_SUPPORTED:
          Log.v(
              getClass().getName(),
              "setLanguage(" + locale.getDisplayLanguage() + ") => MISSING: " + res);
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
  int speak(String text, UtterancePrio prio, boolean flush, HashMap<String, String> params) {
    if (!isAvailable()) {
      return 0;
    }

    final boolean trace = true;
    if (!cueSet.contains(text)) {
      //noinspection ConstantConditions
      if (trace) {
        Log.d(getClass().getName(), "buffer speak: " + text);
      }
      cueSet.add(text);
      cueList.add(new Entry(text, prio, flush, params, getId(text)));
    } else {
      //noinspection ConstantConditions
      if (trace) {
        Log.d(getClass().getName(), "skip buffer (duplicate) speak: " + text);
      }
    }
    return 0;
  }

  private final HashMap<String, Entry> outstanding = new HashMap<>();

  void utteranceCompleted(String id) {
    outstanding.remove(id);
    maybeAbandonAudioFocus();
  }

  private boolean requestFocus() {
    if (hasAudioFocus.get()) {
      return true;
    }
    int result =
        audioManager.requestAudioFocus(
            null, // afChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    var granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    hasAudioFocus.set(granted);
    return granted;
  }

  private void maybeAbandonAudioFocus() {
    if (!hasAudioFocus.get()) {
      return;
    }
    if (!outstanding.isEmpty()) {
      return;
    }
    hasAudioFocus.set(false);
    audioManager.abandonAudioFocus(null);
  }

  public void emit() {
    if (!isAvailable()) {
      return;
    }
    if (cueSet.isEmpty()) {
      return;
    }
    if (mute && !requestFocus()) {
      // Log error ?
      return;
    }

    // Sort pending utterances accoring to prio.
    Collections.sort(cueList, (x,y) -> -Integer.compare(x.prio, y.prio));

    int mode = TextToSpeech.QUEUE_ADD;
    int maxPrio = cueList.get(0).prio;

    // Check outstanding.
    if (!outstanding.isEmpty()) {
      int outstandingPrio = getMaxOutstandingPrio();
      if (maxPrio >= outstandingPrio) {
        mode = TextToSpeech.QUEUE_FLUSH;
        outstanding.clear();
      }
    }

    for (Entry e : cueList) {
      HashMap<String, String> params = e.params;
      if (params == null) {
        params = new HashMap<>();
      }

      params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, e.id);

      int res = textToSpeech.speak(e.text, mode, params);
      if (res == TextToSpeech.ERROR) {
        Log.i(
            getClass().getName(),
              "res == ERROR emit() text: "
              + e.text
              + ", utId: "
              + e.id
              + ") outstanding.size(): "
              + outstanding.size());
      } else {
        outstanding.put(e.id, e);
        // Subsequent utterances will be added.
        mode = TextToSpeech.QUEUE_ADD;
      }
    }

    cueSet.clear();
    cueList.clear();
    maybeAbandonAudioFocus();
  }

  int getMaxOutstandingPrio() {
    int prio = -1;
    for (Entry e : outstanding.values()) {
      prio = Math.max(prio, e.prio);
    }
    return prio;
  }
}

class UtteranceCompletion {

  public static void setUtteranceCompletedListener(
      TextToSpeech tts, final RUTextToSpeech ruTextToSpeech) {
    tts.setOnUtteranceProgressListener(
        new UtteranceProgressListener() {
          @Override
          public void onDone(String utteranceId) {
            ruTextToSpeech.utteranceCompleted(utteranceId);
          }

          @Override
          public void onError(String utteranceId) {
            ruTextToSpeech.utteranceCompleted(utteranceId);
          }

          @Override
          public void onStart(String utteranceId) {}
        });
  }
}
