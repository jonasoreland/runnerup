package org.runnerup.util;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;


public class MapViewWrapper extends com.mapbox.mapboxsdk.maps.MapView {

    public MapViewWrapper(Context context) {
        super(context);
    }
    public MapViewWrapper(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

}
