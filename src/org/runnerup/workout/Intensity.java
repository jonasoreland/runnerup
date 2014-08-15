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

package org.runnerup.workout;

import org.runnerup.R;
import org.runnerup.util.Constants.DB.INTENSITY;

public enum Intensity {

    /**
     * Running
     */
    ACTIVE(INTENSITY.ACTIVE, R.string.txt_intensity_active),

    /**
	 *
	 */
    RESTING(INTENSITY.RESTING, R.string.txt_intensity_resting),

    /**
     * Warm up
     */
    WARMUP(INTENSITY.WARMUP, R.string.txt_intensity_warmup, R.string.cue_warmup),

    /**
     * Cool down
     */
    COOLDOWN(INTENSITY.COOLDOWN, R.string.txt_intensity_cooldown, R.string.cue_cooldown),

    /**
     * Loop (for workout construction/plans)
     */
    REPEAT(INTENSITY.REPEAT, R.string.txt_intensity_repeat),

    /**
     *
     */
   RECOVERY(INTENSITY.RECOVERY, R.string.txt_intensity_recovery) ;

    int value;
    int textId;
    int cueId;

    Intensity(int val, int textId) {
        this.value = val;
        this.textId = textId;
        this.cueId = textId;
    }

    Intensity(int val, int textId, int cueId) {
        this.value = val;
        this.textId = textId;
        this.cueId = cueId;
    }

    public int getValue() {
        return value;
    }

    public int getTextId() {
        return textId;
    }

    public int getCueId() {
        return cueId;
    }
}
