package org.runnerup.wear;

import static com.google.android.gms.wearable.PutDataRequest.WEAR_URI_SCHEME;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.util.function.Consumer;

public class WearableClient {

  private DataClient mDataClient = null;

  public WearableClient(Context context) {
    mDataClient = Wearable.getDataClient(context);
  }

  public void readData(String path, Consumer<DataItem> consumer) {
    mDataClient
        .getDataItems(new Uri.Builder().scheme(WEAR_URI_SCHEME).path(path).build())
        .addOnCompleteListener(
            new OnCompleteListener<>() {
              @Override
              public void onComplete(@NonNull Task<DataItemBuffer> task) {
                if (task.isSuccessful()) {
                  DataItemBuffer dataItems = task.getResult();
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (dataItems.getCount() == 0) {
                      consumer.accept(null);
                    } else {
                      for (DataItem dataItem : dataItems) {
                        consumer.accept(dataItem);
                      }
                    }
                  }
                  dataItems.release();
                } else {
                  System.out.println("task.getException(): " + task.getException());
                }
              }
            });
  }

  public Task<DataItem> putData(String path) {
    return mDataClient.putDataItem(PutDataRequest.create(path));
  }

  public Task<DataItem> putData(String path, Bundle b) {
    return mDataClient.putDataItem(
        PutDataRequest.create(path).setData(DataMap.fromBundle(b).toByteArray()));
  }

  public Task<Integer> deleteData(String path) {
    return mDataClient.deleteDataItems(
        new Uri.Builder().scheme(WEAR_URI_SCHEME).path(path).build());
  }
}
