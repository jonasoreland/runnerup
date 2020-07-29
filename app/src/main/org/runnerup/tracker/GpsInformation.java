package org.runnerup.tracker;

public interface GpsInformation {
    float getGpsAccuracy();

    int getSatellitesAvailable();

    int getSatellitesFixed();
}
