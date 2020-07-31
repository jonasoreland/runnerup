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
import android.view.View;
import android.widget.TextView;

import org.runnerup.util.Formatter;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Feedback;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Workout;

import java.util.HashMap;


public class CountdownFeedback extends Feedback {

    private final Scope scope;
    private final Dimension dimension;
    private TextView textView = null;
    private Formatter formatter;

    public CountdownFeedback(Scope s, Dimension d) {
        this.scope = s;
        this.dimension = d;
    }

    @Override
    public void onBind(Workout s, HashMap<String, Object> bindValues) {
        super.onBind(s, bindValues);
        if (bindValues.containsKey(Workout.KEY_FORMATTER))
            formatter = (Formatter) bindValues.get(Workout.KEY_FORMATTER);
        if (bindValues.containsKey(Workout.KEY_COUNTER_VIEW))
            textView = (TextView) bindValues.get(Workout.KEY_COUNTER_VIEW);
    }

    @Override
    public void onStart(Workout s) {
        if (textView != null) {
            textView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onEnd(Workout s) {
        if (textView != null) {
            textView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean equals(Feedback _other) {
        return _other instanceof CountdownFeedback;
    }

    @Override
    public void emit(Workout w, Context ctx) {
        if (textView == null)
            return;

        double remaining = w.getRemaining(scope, dimension);
        if (remaining > 0) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(formatter.formatRemaining(Formatter.Format.TXT_SHORT, dimension, remaining));
        }
        else {
            textView.setVisibility(View.INVISIBLE);
        }
    }
}
