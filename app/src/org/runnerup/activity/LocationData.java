package org.runnerup.activity;

import org.runnerup.common.util.Constants;
import org.runnerup.db.Column;

/**
 * Created by itdog on 23.01.15.
 */
public class LocationData extends BaseEntity{
    @Column(name = "_id", isAutoincrement = true)
    private long id;
    @Column(name = Constants.DB.LOCATION.ACTIVITY)
    private Long activityId;
    @Column(name = Constants.DB.LOCATION.LAP)
    private Integer lap;
    @Column(name = Constants.DB.LOCATION.TYPE)
    private Short type;
    @Column(name = Constants.DB.LOCATION.TIME)
    private Long time;
    @Column(name = Constants.DB.LOCATION.LATITUDE)
    private Double latitude;
    @Column(name = Constants.DB.LOCATION.LONGITUDE)
    private Double longitude;
    @Column(name = Constants.DB.LOCATION.ALTITUDE)
    private Double altitude;
    @Column(name = Constants.DB.LOCATION.ACCURANCY)
    private Float accurancy;
    @Column(name = Constants.DB.LOCATION.SPEED)
    private Float speed;
    @Column(name = Constants.DB.LOCATION.BEARING)
    private Float bearing;
    @Column(name = Constants.DB.LOCATION.HR)
    private Integer hr;
    @Column(name = Constants.DB.LOCATION.ACCURANCY)
    private Integer cadence;

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Integer getLap() {
        return lap;
    }

    public void setLap(Integer lap) {
        this.lap = lap;
    }

    public Short getType() {
        return type;
    }

    public void setType(Short type) {
        this.type = type;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public Float getAccurancy() {
        return accurancy;
    }

    public void setAccurancy(Float accurancy) {
        this.accurancy = accurancy;
    }

    public Float getSpeed() {
        return speed;
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
    }

    public Float getBearing() {
        return bearing;
    }

    public void setBearing(Float bearing) {
        this.bearing = bearing;
    }

    public Integer getHr() {
        return hr;
    }

    public void setHr(Integer hr) {
        this.hr = hr;
    }

    public Integer getCadence() {
        return cadence;
    }

    public void setCadence(Integer cadence) {
        this.cadence = cadence;
    }
}
