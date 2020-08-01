package org.runnerup.hr;

import androidx.annotation.NonNull;

import java.util.Arrays;

/**
 * An object representing a single data point from a heart rate device
 *
 * Contains:
 *  * heart rate (as calculated on the device
 *  * time stamp (either from the device, or an estimate)
 *  * R-R interval
 *
 * Depending on the API provided by the protocol module, it's possible that not all
 * fields are available. Because of this, each datum has a corresponding flag that determines
 * whether the value has been set or not.
 *
 * These need to be checked before using the data. For example
 *
 * <pre>
 * {@code
 * HRData data = <from_device>;
 *
 * if (data.hasHeartRate)
 *      processHeartRate(data.hrValue);
 *
 * if (data.hasRrIntervals)
 *      processRrIntervals(data.rrIntervals);
 *
 * if (data.timeStampIsFromDevice)
 *      processTimeStampAccurate(data.timestamp)
 * else
 *      processTimeStampEstimate(data.timestamp)
 * }
 * </pre>
 */

public class HRData {
    
    public boolean hasHeartRate = false;
    public long hrValue = -1;
    private boolean timeStampIsFromDevice = false;
    public long timestamp = -1;
    private boolean hasRrIntervals = false;
    private long[] rrIntervals = null;

    @NonNull
    @Override
    public String toString() {
        return "HRData{" + ", hrValue=" + (hasHeartRate ? hrValue : "<no_heart_rate_data>") +
                '\n' +
                ", timeStampIsFromDevice=" + timeStampIsFromDevice +
                '\n' +
                ", timestamp=" + timestamp +
                '\n' +
                ", rrIntervals=" + (hasRrIntervals ? Arrays.toString(rrIntervals) : "<no_rr_interval_data>") +
                '\n' +
                '}';
    }

    public HRData setHeartRate(long heartRate){
        hasHeartRate = true;
        hrValue = heartRate;
        return this;
    }
    
    public HRData setTimestampEstimate(long timestamp){
        timeStampIsFromDevice = false;
        this.timestamp = timestamp;
        return this;
    }

    public HRData setTimestamp(long timestampFromDevice){
        timeStampIsFromDevice = true;
        this.timestamp = timestampFromDevice;
        return this;
    }

    public HRData setRrIntervals(long[] rrIntervals){
        hasRrIntervals = true;
        this.rrIntervals = rrIntervals;
        return this;
    }
    
}
