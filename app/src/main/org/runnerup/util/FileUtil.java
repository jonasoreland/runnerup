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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtil {

  private static int copy(InputStream src, OutputStream dst) throws IOException {
    int cnt = 0;
    byte[] buf = new byte[1024];
    int bytesRead;
    while ((bytesRead = src.read(buf)) > 0) {
      cnt += bytesRead;
      dst.write(buf, 0, bytesRead);
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

  /**
   * Copies content from a source Uri to a destination Uri using the provided Context.
   *
   * @param context The Context used to obtain a ContentResolver for opening the Uris.
   * @param to The destination Uri.
   * @param from The source Uri.
   * @return The number of bytes copied.
   * @throws IOException If an I/O error occurs, including if Uris cannot be opened (e.g.,
   *     FileNotFoundException).
   * @throws NullPointerException if context, to, or from Uri is null.
   */
  public static int copyFile(Context context, Uri to, Uri from) throws IOException {
    if (context == null) {
      throw new NullPointerException("Context cannot be null");
    }
    if (to == null) {
      throw new NullPointerException("Destination Uri cannot be null");
    }
    if (from == null) {
      throw new NullPointerException("Source Uri cannot be null");
    }

    // openFileDescriptor will throw FileNotFoundException if the URI cannot be opened,
    // which will be propagated up.
    ContentResolver resolver = context.getContentResolver();
    try (ParcelFileDescriptor fromFileDescriptor = resolver.openFileDescriptor(from, "r");
        ParcelFileDescriptor toFileDescriptor = resolver.openFileDescriptor(to, "w")) {

      if (fromFileDescriptor == null) {
        throw new IOException("Could not open source Uri: " + from);
      }
      if (toFileDescriptor == null) {
        throw new IOException("Could not open destination Uri: " + to);
      }

      try (FileInputStream input = new FileInputStream(fromFileDescriptor.getFileDescriptor());
          FileOutputStream output = new FileOutputStream(toFileDescriptor.getFileDescriptor())) {
        return copy(input, output);
      }
    }
  }
}
