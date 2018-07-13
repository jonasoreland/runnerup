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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;


public class FormValues extends HashMap<String, String> implements Writable {
    /**
     *
     */
    private static final long serialVersionUID = -5681066662144155533L;

    public FormValues() {
        super();
    }

    @Override
    public void write(OutputStream o) throws IOException {
        boolean first = true;
        DataOutputStream out = new DataOutputStream(o);
        for (String k : keySet()) {
            if (!first)
                out.writeByte('&');
            out.writeBytes(SyncHelper.URLEncode(k));
            out.writeByte('=');
            out.writeBytes(SyncHelper.URLEncode(get(k)));
            first = false;
        }
    }

    public String queryString() {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (String k : keySet()) {
            if (!first)
                buf.append('&');
            buf.append(SyncHelper.URLEncode(k));
            buf.append('=');
            buf.append(SyncHelper.URLEncode(get(k)));
            first = false;
        }
        return buf.toString();
    }
}
