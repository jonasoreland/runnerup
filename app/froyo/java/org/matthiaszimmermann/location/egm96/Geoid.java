//Dummy wrapper for froyo
//
package org.matthiaszimmermann.location.egm96;

import java.io.InputStream;

public class Geoid {

    public static boolean init(InputStream is) {
        return false;
    }

    public static double getOffset(double lat, double lon) {
        return 0;
    }
}