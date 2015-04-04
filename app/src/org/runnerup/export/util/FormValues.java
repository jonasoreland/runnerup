package org.runnerup.export.util;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/**
* Created by LFAJER on 2015-04-04.
*/
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
