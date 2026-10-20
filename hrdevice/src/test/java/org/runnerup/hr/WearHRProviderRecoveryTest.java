package org.runnerup.hr;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.runnerup.common.util.Constants;

public class WearHRProviderRecoveryTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void staleStreamRestartsOnceAndFreshSampleRestoresConnectedStatus() {
    Context context = mock(Context.class);
    Handler handler = mock(Handler.class);
    MessageClient messageClient = mock(MessageClient.class);
    Task<Integer> sendTask = mock(Task.class);
    HRProvider.HRClient hrClient = mock(HRProvider.HRClient.class);
    AtomicLong nowMillis = new AtomicLong(1_000);
    List<Runnable> delayedCallbacks = new ArrayList<>();
    List<OnSuccessListener<Integer>> sendSuccessCallbacks = new ArrayList<>();

    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return true;
            })
        .when(handler)
        .post(any(Runnable.class));
    doAnswer(
            invocation -> {
              delayedCallbacks.add(invocation.getArgument(0));
              return true;
            })
        .when(handler)
        .postDelayed(any(Runnable.class), anyLong());
    when(messageClient.sendMessage(
            eq("watch-node"), eq(Constants.Wear.Path.MSG_CMD_HR_START), any(byte[].class)))
        .thenReturn(sendTask);
    when(sendTask.addOnSuccessListener(any(OnSuccessListener.class)))
        .thenAnswer(
            invocation -> {
              sendSuccessCallbacks.add(invocation.getArgument(0));
              return sendTask;
            });
    when(sendTask.addOnFailureListener(any())).thenReturn(sendTask);

    try (MockedStatic<Wearable> wearable = mockStatic(Wearable.class);
        MockedStatic<SystemClock> systemClock = mockStatic(SystemClock.class)) {
      wearable.when(() -> Wearable.getMessageClient(context)).thenReturn(messageClient);
      systemClock.when(SystemClock::elapsedRealtime).thenAnswer(ignored -> nowMillis.get());
      systemClock
          .when(SystemClock::elapsedRealtimeNanos)
          .thenAnswer(ignored -> nowMillis.get() * 1_000_000);

      WearHRProvider provider = new WearHRProvider(context);
      provider.open(handler, hrClient);
      provider.connect(HRDeviceRef.create(WearHRProvider.NAME, "Watch", "watch-node"));
      sendSuccessCallbacks.get(0).onSuccess(1);

      ArgumentCaptor<MessageClient.OnMessageReceivedListener> listenerCaptor =
          ArgumentCaptor.forClass(MessageClient.OnMessageReceivedListener.class);
      verify(messageClient).addListener(listenerCaptor.capture());
      MessageClient.OnMessageReceivedListener listener = listenerCaptor.getValue();
      sendHeartRate(listener, "watch-node", "80");
      assertEquals(WearHRConnectionState.Status.CONNECTED, provider.getConnectionStatus());

      nowMillis.set(16_001);
      Runnable freshnessCheck = delayedCallbacks.get(delayedCallbacks.size() - 1);
      freshnessCheck.run();
      freshnessCheck.run();
      assertEquals(WearHRConnectionState.Status.RECONNECTING, provider.getConnectionStatus());
      verify(messageClient, times(2))
          .sendMessage(
              eq("watch-node"), eq(Constants.Wear.Path.MSG_CMD_HR_START), any(byte[].class));
      sendSuccessCallbacks.get(1).onSuccess(1);

      nowMillis.set(16_500);
      sendHeartRate(listener, "watch-node", "81");
      assertEquals(WearHRConnectionState.Status.CONNECTED, provider.getConnectionStatus());
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void recoveryTimeoutStopsWatchAndMarksProviderUnavailable() {
    Context context = mock(Context.class);
    Handler handler = mock(Handler.class);
    MessageClient messageClient = mock(MessageClient.class);
    Task<Integer> sendTask = mock(Task.class);
    HRProvider.HRClient hrClient = mock(HRProvider.HRClient.class);
    AtomicLong nowMillis = new AtomicLong(1_000);
    List<Runnable> delayedCallbacks = new ArrayList<>();
    List<OnSuccessListener<Integer>> sendSuccessCallbacks = new ArrayList<>();

    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return true;
            })
        .when(handler)
        .post(any(Runnable.class));
    doAnswer(
            invocation -> {
              delayedCallbacks.add(invocation.getArgument(0));
              return true;
            })
        .when(handler)
        .postDelayed(any(Runnable.class), anyLong());
    when(messageClient.sendMessage(any(), any(), any(byte[].class))).thenReturn(sendTask);
    when(sendTask.addOnSuccessListener(any(OnSuccessListener.class)))
        .thenAnswer(
            invocation -> {
              sendSuccessCallbacks.add(invocation.getArgument(0));
              return sendTask;
            });
    when(sendTask.addOnFailureListener(any())).thenReturn(sendTask);

    try (MockedStatic<Wearable> wearable = mockStatic(Wearable.class);
        MockedStatic<SystemClock> systemClock = mockStatic(SystemClock.class)) {
      wearable.when(() -> Wearable.getMessageClient(context)).thenReturn(messageClient);
      systemClock.when(SystemClock::elapsedRealtime).thenAnswer(ignored -> nowMillis.get());
      systemClock
          .when(SystemClock::elapsedRealtimeNanos)
          .thenAnswer(ignored -> nowMillis.get() * 1_000_000);

      WearHRProvider provider = new WearHRProvider(context);
      provider.open(handler, hrClient);
      provider.connect(HRDeviceRef.create(WearHRProvider.NAME, "Watch", "watch-node"));
      sendSuccessCallbacks.get(0).onSuccess(1);
      ArgumentCaptor<MessageClient.OnMessageReceivedListener> listenerCaptor =
          ArgumentCaptor.forClass(MessageClient.OnMessageReceivedListener.class);
      verify(messageClient).addListener(listenerCaptor.capture());
      sendHeartRate(listenerCaptor.getValue(), "watch-node", "80");

      nowMillis.set(16_001);
      delayedCallbacks.get(delayedCallbacks.size() - 1).run();
      Runnable recoveryTimeout = delayedCallbacks.get(delayedCallbacks.size() - 1);
      nowMillis.set(46_002);
      recoveryTimeout.run();

      assertEquals(WearHRConnectionState.Status.UNAVAILABLE, provider.getConnectionStatus());
      verify(messageClient)
          .sendMessage(
              eq("watch-node"), eq(Constants.Wear.Path.MSG_CMD_HR_STOP), any(byte[].class));
      verify(messageClient).removeListener(any(MessageClient.OnMessageReceivedListener.class));
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void disconnectCancelsPendingRecoveryAndClearsConnectionState() {
    Context context = mock(Context.class);
    Handler handler = mock(Handler.class);
    MessageClient messageClient = mock(MessageClient.class);
    Task<Integer> sendTask = mock(Task.class);
    HRProvider.HRClient hrClient = mock(HRProvider.HRClient.class);
    AtomicLong nowMillis = new AtomicLong(1_000);
    AtomicReference<OnSuccessListener<Integer>> sendSuccess = new AtomicReference<>();
    List<Runnable> delayedCallbacks = new ArrayList<>();

    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return true;
            })
        .when(handler)
        .post(any(Runnable.class));
    doAnswer(
            invocation -> {
              delayedCallbacks.add(invocation.getArgument(0));
              return true;
            })
        .when(handler)
        .postDelayed(any(Runnable.class), anyLong());
    when(messageClient.sendMessage(any(), any(), any(byte[].class))).thenReturn(sendTask);
    when(sendTask.addOnSuccessListener(any(OnSuccessListener.class)))
        .thenAnswer(
            invocation -> {
              sendSuccess.set(invocation.getArgument(0));
              return sendTask;
            });
    when(sendTask.addOnFailureListener(any())).thenReturn(sendTask);

    try (MockedStatic<Wearable> wearable = mockStatic(Wearable.class);
        MockedStatic<SystemClock> systemClock = mockStatic(SystemClock.class)) {
      wearable.when(() -> Wearable.getMessageClient(context)).thenReturn(messageClient);
      systemClock.when(SystemClock::elapsedRealtime).thenAnswer(ignored -> nowMillis.get());
      systemClock
          .when(SystemClock::elapsedRealtimeNanos)
          .thenAnswer(ignored -> nowMillis.get() * 1_000_000);

      WearHRProvider provider = new WearHRProvider(context);
      provider.open(handler, hrClient);
      provider.connect(HRDeviceRef.create(WearHRProvider.NAME, "Watch", "watch-node"));
      sendSuccess.get().onSuccess(1);
      ArgumentCaptor<MessageClient.OnMessageReceivedListener> listenerCaptor =
          ArgumentCaptor.forClass(MessageClient.OnMessageReceivedListener.class);
      verify(messageClient).addListener(listenerCaptor.capture());
      sendHeartRate(listenerCaptor.getValue(), "watch-node", "80");

      nowMillis.set(16_001);
      delayedCallbacks.get(delayedCallbacks.size() - 1).run();
      assertEquals(WearHRConnectionState.Status.RECONNECTING, provider.getConnectionStatus());

      provider.disconnect();

      assertEquals(WearHRConnectionState.Status.DISCONNECTED, provider.getConnectionStatus());
      verify(handler, org.mockito.Mockito.atLeastOnce()).removeCallbacks(any(Runnable.class));
      verify(messageClient)
          .sendMessage(
              eq("watch-node"), eq(Constants.Wear.Path.MSG_CMD_HR_STOP), any(byte[].class));
    }
  }

  private static void sendHeartRate(
      MessageClient.OnMessageReceivedListener listener, String sourceNode, String value) {
    MessageEvent event = mock(MessageEvent.class);
    when(event.getPath()).thenReturn(Constants.Wear.Path.MSG_HEART_RATE);
    when(event.getSourceNodeId()).thenReturn(sourceNode);
    when(event.getData()).thenReturn(value.getBytes(StandardCharsets.US_ASCII));
    listener.onMessageReceived(event);
  }
}
