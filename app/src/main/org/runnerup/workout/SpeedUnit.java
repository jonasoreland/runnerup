package org.runnerup.workout;

import org.runnerup.R;
import org.runnerup.common.util.Constants;

public enum SpeedUnit {

    SPEED(Constants.SPEED_UNIT.SPEED, R.string.speed),
    PACE(Constants.SPEED_UNIT.PACE, R.string.pace);

    final String value;
    final int textId;

    SpeedUnit(String val, int textId){
        this.value = val;
        this.textId = textId;
    }

    public String getValue() {
        return value;
    }

    public int getTextId() {
        return textId;
    }

    public boolean equal(SpeedUnit what) {
        return !(what == null || !what.value.equals(this.value));
    }
}
