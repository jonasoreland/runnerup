package org.runnerup.export.util;

import java.io.IOException;
import java.io.OutputStream;

/**
* Created by LFAJER on 2015-04-04.
*/
public interface Writable {
    void write(OutputStream out) throws IOException;
}
