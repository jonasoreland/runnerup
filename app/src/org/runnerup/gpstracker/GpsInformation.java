package org.runnerup.gpstracker;

public interface GpsInformation {
    String getGpsAccuracy();

    int getSatellitesAvailable();

    int getSatellitesFixed();
}
