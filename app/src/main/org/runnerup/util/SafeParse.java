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

package org.runnerup.util;

public class SafeParse {

  public static String normalizeDigits(String string) {
    if (string == null) return null;
    StringBuilder sb = new StringBuilder(string.length());
    for (int i = 0; i < string.length(); i++) {
      char c = string.charAt(i);
      // Check for Arabic-Indic digits (0x0660 to 0x0669)
      if (c >= '\u0660' && c <= '\u0669') {
        sb.append((char) ('0' + (c - '\u0660')));
      }
      // Check for Extended Arabic-Indic / Persian digits (0x06F0 to 0x06F9)
      else if (c >= '\u06f0' && c <= '\u06f9') {
        sb.append((char) ('0' + (c - '\u06f0')));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public static int parseInt(String string, int defaultValue) {
    try {
      return Integer.parseInt(normalizeDigits(string));
    } catch (Exception ex) {
    }
    return defaultValue;
  }

  public static long parseLong(String string, long defaultValue) {
    try {
      return Long.parseLong(normalizeDigits(string));
    } catch (Exception ex) {
    }
    return defaultValue;
  }

  public static double parseDouble(String string, double defaultValue) {
    if (string == null) return defaultValue;
    try {
      // Normalize digits and replace comma with dot to support international format
      String normalized = normalizeDigits(string).replace(',', '.');
      return Double.parseDouble(normalized);
    } catch (Exception ex) {
      return defaultValue;
    }
  }

  /**
   * @param string in form "HH:MM:SS"
   * @param defaultValue
   * @return
   */
  public static long parseSeconds(String string, long defaultValue) {
    try {
      String normalized = normalizeDigits(string);
      String[] split = normalized.split(":");
      long mul = 1;
      long sum = 0;
      for (int i = split.length - 1; i >= 0; i--) {
        sum += Long.parseLong(split[i]) * mul;
        mul *= 60;
      }
      return sum;
    } catch (Exception ex) {
    }
    return defaultValue;
  }

  public static int[] parseIntList(final String str) {
    try {
      String[] split = str.split(",");
      int[] res = new int[split.length];
      for (int i = 0; i < split.length; i++) {
        res[i] = Integer.parseInt(split[i]);
      }
      return res;
    } catch (Exception ex) {

    }
    return null;
  }

  public static String storeIntList(int[] list) {
    StringBuilder buf = new StringBuilder().append(list[0]);
    for (int i = 1; i < list.length; i++) {
      buf.append(',').append(list[i]);
    }
    return buf.toString();
  }
}
