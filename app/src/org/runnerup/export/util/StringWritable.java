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

package org.runnerup.export.util;

import java.io.IOException;
import java.io.OutputStream;


public class StringWritable implements Writable {
    private final byte[]  s;

    public StringWritable(String s) {
        this.s = s.getBytes();
    }

    public StringWritable(byte[] s) {
        this.s = s;
    }

    public void write(OutputStream out) throws IOException {
        out.write(s);
    }
}
