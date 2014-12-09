package org.runnerup.tracker;

public interface GpsInformation {
    String getGpsAccuracy();

    int getSatellitesAvailable();

    int getSatellitesFixed();
}
