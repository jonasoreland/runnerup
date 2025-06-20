package org.runnerup.service;

import static com.google.android.gms.wearable.PutDataRequest.WEAR_URI_SCHEME;

import android.content.Context;
import android.net.Uri;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.util.function.Consumer;

public class WearableClient {

  private DataClient mDataClient = null;

  public WearableClient(Context context) {
    mDataClient = Wearable.getDataClient(context);
  }

  public void readData(String path, Consumer<DataItem> consumer) {
    mDataClient.getDataItems(new Uri.Builder()
                             .scheme(WEAR_URI_SCHEME)
                             .path(path)
                             .build())
        .addOnCompleteListener(new OnCompleteListener<DataItemBuffer>() {
            @Override
            public void onComplete(Task<DataItemBuffer> task) {
              if (task.isSuccessful()) {
                DataItemBuffer dataItems = task.getResult();
                if (dataItems.getCount() == 0) {
                  consumer.accept(null);
                } else {
                  for (DataItem dataItem : dataItems) {
                    consumer.accept(dataItem);
                  }
                }
                dataItems.release();
              } else {
                System.out.println("task.getException(): " + task.getException());
              }
            }
          });
  }

  public void putData(String path) {
    mDataClient.putDataItem(PutDataRequest.create(path));
  }

  public void deleteData(String path) {
    mDataClient.deleteDataItems(
        new Uri.Builder().scheme(WEAR_URI_SCHEME).path(path).build());
  }
}
