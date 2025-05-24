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

package org.runnerup.workout;

import org.runnerup.common.util.Constants.DB.DIMENSION;

/** This is just constant */
public enum Dimension {
  TIME(DIMENSION.TIME, org.runnerup.common.R.string.time),
  DISTANCE(DIMENSION.DISTANCE, org.runnerup.common.R.string.distance),
  SPEED(DIMENSION.SPEED, org.runnerup.common.R.string.speed),
  PACE(DIMENSION.PACE, org.runnerup.common.R.string.pace),
  HR(DIMENSION.HR, org.runnerup.common.R.string.Heart_rate),
  HRZ(DIMENSION.HRZ, org.runnerup.common.R.string.heart_rate_zone),
  CAD(DIMENSION.CAD, org.runnerup.common.R.string.cadence),
  TEMPERATURE(DIMENSION.CAD, org.runnerup.common.R.string.temperature),
  PRESSURE(DIMENSION.CAD, org.runnerup.common.R.string.pressure);

  // TODO
  public static final boolean SPEED_CUE_ENABLED = true;

  final int value;
  final int textId;

  Dimension(int val, int textId) {
    this.value = val;
    this.textId = textId;
  }

  /**
   * @return the value
   */
  public int getValue() {
    return value;
  }

  public int getTextId() {
    return textId;
  }

  public boolean equal(Dimension what) {
    return !(what == null || what.value != this.value);
  }

  public static Dimension valueOf(int val) {
    switch (val) {
      case -1:
        return null;
      case DIMENSION.TIME:
        return TIME;
      case DIMENSION.DISTANCE:
        return DISTANCE;
      case DIMENSION.SPEED:
        return SPEED;
      case DIMENSION.PACE:
        return PACE;
      case DIMENSION.HR:
        return HR;
      case DIMENSION.HRZ:
        return HRZ;
      case DIMENSION.CAD:
        return CAD;
      case DIMENSION.TEMPERATURE:
        return TEMPERATURE;
      case DIMENSION.PRESSURE:
        return PRESSURE;
      default:
        return null;
    }
  }
}
