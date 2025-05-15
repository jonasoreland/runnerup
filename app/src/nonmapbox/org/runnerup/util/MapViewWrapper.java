package org.runnerup.util;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;

import org.osmdroid.tileprovider.MapTileProviderBase;


public class MapViewWrapper extends org.osmdroid.views.MapView {
    public MapViewWrapper(Context context, MapTileProviderBase tileProvider, Handler tileRequestCompleteHandler, AttributeSet attrs) {
        super(context, tileProvider, tileRequestCompleteHandler, attrs);
    }

    public MapViewWrapper(final Context context,
                   MapTileProviderBase tileProvider,
                   final Handler tileRequestCompleteHandler, final AttributeSet attrs, boolean hardwareAccelerated) {
        super(context, tileProvider, tileRequestCompleteHandler, attrs, hardwareAccelerated);
    }

    public MapViewWrapper(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public MapViewWrapper(final Context context) {
        super(context);
    }

    public MapViewWrapper(final Context context,
                   final MapTileProviderBase aTileProvider) {
        super(context, aTileProvider);
    }

    public MapViewWrapper(final Context context,
                   final MapTileProviderBase aTileProvider,
                   final Handler tileRequestCompleteHandler) {
        super(context, aTileProvider, tileRequestCompleteHandler);
    }
}
