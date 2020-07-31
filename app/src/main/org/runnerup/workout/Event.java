/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

package org.runnerup.workout;

import org.runnerup.R;

/**
 * This is just constant
 */
public enum Event {

    STARTED(1, R.string.cue_started),
    PAUSED(2, R.string.cue_paused),
    STOPPED(3, R.string.cue_stopped),
    RESUMED(4, R.string.cue_resumed),
    COMPLETED(5, R.string.cue_completed);

    final int value;
    final int cueId;

    Event(int val, int cueId) {
        this.value = val;
        this.cueId = cueId;
    }

    /**
     * @return the eventValue
     */
    public int getValue() {
        return value;
    }

    public boolean equal(Event what) {
        return !(what == null || what.value != this.value);
    }

    public int getCueId() {
        return cueId;
    }
}
