/*
 * Copyright (C) 2025 - 2025 jonas.oreland@gmail.com
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

package org.runnerup.export.format;
import org.runnerup.workout.Sport;

public class ExportOptions {

  public final boolean isStrava;
  public final boolean garminExtensions; // Also Cluetrust
  public final boolean accuracyExtensions;

  public static class Builder {
    public boolean isStrava = false;
    public boolean garminExtensions = true; // Also Cluetrust
    public boolean accuracyExtensions = true;

    public ExportOptions build() {
      return new ExportOptions(this);
    }
    private Builder() {}
  }

  boolean shouldExportLap(Integer sportDbValue, Float distance, Long time) {
    if (distance > 0 && time > 0) {
      return true;
    }
    if (sportDbValue != null && Sport.isWithoutGps(sportDbValue)) {
      return time > 0;
    }
    // TODO should we export laps with distance = 0 and time > 0
    // or with distance > 0 and time = 0 for other sports??
    return false;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ExportOptions getDefault() {
    return builder().build();
  }

  private ExportOptions(Builder builder) {
    this.isStrava = builder.isStrava;
    this.garminExtensions = builder.garminExtensions;
    this.accuracyExtensions = builder.accuracyExtensions;
  }
}
