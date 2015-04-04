package org.runnerup.export.util;

import java.io.IOException;
import java.io.OutputStream;

/**
* Created by LFAJER on 2015-04-04.
*/
public class StringWritable implements Writable {
    final String s;

    public StringWritable(String s) {
        this.s = s;
    }

    public void write(OutputStream out) throws IOException {
        out.write(s.getBytes());
    }
}
