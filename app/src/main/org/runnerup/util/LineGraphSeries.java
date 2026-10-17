package org.runnerup.util;

import java.util.ArrayList;
import java.util.List;

public class LineGraphSeries<T> {
  private List<T> data;

  public LineGraphSeries(T[] initialData) {
    data = new ArrayList<>();
    for (T item : initialData) {
      data.add(item);
    }
  }

  public void resetData(T[] newData) {
    data.clear();
    for (T item : newData) {
      data.add(item);
    }
  }

  public void appendData(T newDataPoint, boolean scrollToEnd, int maxSize) {
    data.add(newDataPoint);
    if (data.size() > maxSize) {
      data.remove(0);
    }
  }
}
