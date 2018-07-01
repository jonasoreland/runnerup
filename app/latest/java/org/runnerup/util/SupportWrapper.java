package org.runnerup.util;

import android.content.Context;
import android.support.v4.app.NotificationCompat;

//Wrapper class for calls not available in Froyo support library
//Last supported version in Froyo was 24.1, all options are not available in newer versions
public class SupportWrapper {
    public static NotificationCompat.Builder Builder(Context context, String chanId) {
        return new NotificationCompat.Builder(context, chanId);
    }
}
