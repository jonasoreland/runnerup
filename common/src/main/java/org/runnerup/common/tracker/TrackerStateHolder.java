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
package org.runnerup.common.tracker;

import java.util.ArrayList;

/**
 * Created by jonas on 12/28/14.
 */
public class TrackerStateHolder {

    private TrackerState state;
    final private ArrayList<TrackerStateListener> listeners = new ArrayList<TrackerStateListener>();

    public TrackerStateHolder() {
        this.state = null;
    }

    public TrackerStateHolder(TrackerState state) {
        this.state = state;
    }

    public void set(TrackerState newState) {
        if (TrackerState.equals(state, newState))
            return;

        TrackerState oldState = state;
        state = newState;
        for (TrackerStateListener l : listeners) {
            l.onTrackerStateChange(oldState, newState);
        }
    }

    public TrackerState get() {
        return state;
    }

    public void registerTrackerStateListener(TrackerStateListener listener) {
        listeners.add(listener);
    }

    public void unregisterTrackerStateListener(TrackerStateListener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }
}
