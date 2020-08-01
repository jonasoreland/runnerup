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

package org.runnerup.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class FileUtil {

    private static int copy(InputStream src, OutputStream dst) throws IOException {
        int cnt = 0;
        byte[] buf = new byte[1024];
        while (src.read(buf) > 0) {
            cnt += buf.length;
            dst.write(buf);
        }
        return cnt;
    }

    public static int copyFile(String to, String from) throws IOException {
        FileInputStream input = null;
        FileOutputStream output = null;

        try {
            input = new FileInputStream(from);
            output = new FileOutputStream(to);

            return copy(input, output);
        } finally {
            close(input);
            close(output);
        }
    }

    public static void close(InputStream input) {
        if (input != null)
            try {
                input.close();
            } catch (IOException ex) {
            }
    }

    private static void close(OutputStream input) {
        if (input != null)
            try {
                input.close();
            } catch (IOException ex) {
            }
    }

    @SuppressWarnings("UnusedReturnValue")
    public static int copy(InputStream input, String dst) throws IOException {
        FileOutputStream output = null;

        try {
            output = new FileOutputStream(dst);

            return copy(input, output);
        } finally {
            close(output);
        }
    }
}
