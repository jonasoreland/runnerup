package org.runnerup.util;

import android.content.Context;
import android.util.AttributeSet;

public class MapViewWrapper extends com.mapbox.maps.MapView {

  public MapViewWrapper(Context context) {
    super(context);
  }

  public MapViewWrapper(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }
}
