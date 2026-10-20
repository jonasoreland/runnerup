package org.runnerup.hr;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Wearable;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.runnerup.common.util.Constants;

public class WearHRProviderTimeoutTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void firstSampleTimeoutStopsWatchServiceAndRemovesListener() {
    Context context = mock(Context.class);
    Handler handler = mock(Handler.class);
    MessageClient messageClient = mock(MessageClient.class);
    Task<Integer> sendTask = mock(Task.class);
    HRProvider.HRClient hrClient = mock(HRProvider.HRClient.class);
    AtomicReference<Runnable> timeout = new AtomicReference<>();
    AtomicReference<OnSuccessListener<Integer>> sendSuccess = new AtomicReference<>();

    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return true;
            })
        .when(handler)
        .post(any(Runnable.class));
    doAnswer(
            invocation -> {
              timeout.set(invocation.getArgument(0));
              return true;
            })
        .when(handler)
        .postDelayed(any(Runnable.class), eq(30_000L));
    when(messageClient.sendMessage(
            eq("watch-node"), eq(Constants.Wear.Path.MSG_CMD_HR_START), any(byte[].class)))
        .thenReturn(sendTask);
    when(sendTask.addOnSuccessListener(any(OnSuccessListener.class)))
        .thenAnswer(
            invocation -> {
              sendSuccess.set(invocation.getArgument(0));
              return sendTask;
            });

    try (MockedStatic<Wearable> wearable = mockStatic(Wearable.class)) {
      wearable.when(() -> Wearable.getMessageClient(context)).thenReturn(messageClient);
      WearHRProvider provider = new WearHRProvider(context);
      provider.open(handler, hrClient);
      provider.connect(HRDeviceRef.create(WearHRProvider.NAME, "Watch", "watch-node"));
      sendSuccess.get().onSuccess(1);

      assertNotNull("connection timeout must be scheduled", timeout.get());
      timeout.get().run();

      verify(messageClient)
          .sendMessage(
              eq("watch-node"), eq(Constants.Wear.Path.MSG_CMD_HR_STOP), any(byte[].class));
      verify(messageClient).removeListener(any(MessageClient.OnMessageReceivedListener.class));
      verify(hrClient).onConnectResult(false);
    }
  }
}
