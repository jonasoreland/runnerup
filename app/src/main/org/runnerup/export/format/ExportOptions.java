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

public class ExportOptions {

  public final boolean mStrava;
  public final boolean mGarminExt; // Also Cluetrust
  public final boolean mAccuracyExtensions;

  public static class Builder {
    public boolean mStrava = false;
    public boolean mGarminExt = true; // Also Cluetrust
    public boolean mAccuracyExtensions = true;

    public ExportOptions build() {
      return new ExportOptions(this);
    }
    private Builder() {}
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ExportOptions getDefault() {
    return builder().build();
  }

  private ExportOptions(Builder builder) {
    this.mStrava = builder.mStrava;
    this.mGarminExt = builder.mGarminExt;
    this.mAccuracyExtensions = builder.mAccuracyExtensions;
  }
}
