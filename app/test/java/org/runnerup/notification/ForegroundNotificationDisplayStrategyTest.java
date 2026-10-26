package org.runnerup.notification;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowService;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ForegroundNotificationDisplayStrategyTest {
  @Test
  public void approximateLocationPermissionUsesLocationForegroundServiceType() throws Exception {
    ServiceController<RecordingService> controller =
        Robolectric.buildService(RecordingService.class).create();
    try {
      RecordingService service = controller.get();
      Shadows.shadowOf(service).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);

      new ForegroundNotificationDisplayStrategy(service).notify(1, new Notification());

      Method typeAccessor = ShadowService.class.getDeclaredMethod("getForegroundServiceType");
      typeAccessor.setAccessible(true);
      assertEquals(
          ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
          typeAccessor.invoke(Shadows.shadowOf(service)));
    } finally {
      controller.destroy();
    }
  }

  public static class RecordingService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }
  }
}
