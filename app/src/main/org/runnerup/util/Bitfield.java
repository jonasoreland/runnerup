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

package org.runnerup.util;


public class Bitfield {

    public static boolean test(long flags, int bit) {
        long val = (1 << bit);
        return (flags & val) == val;
    }

    public static long set(long flags, int bit, boolean value) {
        if (value)
            return set(flags, bit);
        else
            return clear(flags, bit);
    }

    private static long set(long flags, int bit) {
        long val = (1 << bit);
        return flags | val;
    }

    private static long clear(long flags, int bit) {
        long val = (1 << bit);
        return flags & (~val);
    }
}
