package org.runnerup.activity;

import org.runnerup.common.util.Constants;
import org.runnerup.db.Column;

/**
 * Created by itdog on 23.01.15.
 */
public class Lap extends BaseEntity{
    @Column(name = "_id", isAutoincrement = true)
    private long id;
    @Column(name = Constants.DB.LAP.ACTIVITY)
    private Long activityId;
    @Column(name = Constants.DB.LAP.DISTANCE)
    private Float distance;
    @Column(name = Constants.DB.LAP.TIME)
    private Long time;
    @Column(name = Constants.DB.LAP.AVG_HR)
    private Integer hrAvg;
    @Column(name = Constants.DB.LAP.MAX_HR)
    private Integer hrMax;
    @Column(name = Constants.DB.LAP.AVG_CADENCE)
    private Integer cadenceAvg;
    @Column(name = Constants.DB.LAP.INTENSITY)
    private Short intensity;
    @Column(name = Constants.DB.LAP.LAP)
    private Integer lap;
    @Column(name = Constants.DB.LAP.PLANNED_DISTANCE)
    private Float plannedDistance;
    @Column(name = Constants.DB.LAP.PLANNED_PACE)
    private Float plannedPace;
    @Column(name = Constants.DB.LAP.PLANNED_TIME)
    private Long plannedTime;

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Float getDistance() {
        return distance;
    }

    public void setDistance(Float distance) {
        this.distance = distance;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Integer getHrAvg() {
        return hrAvg;
    }

    public void setHrAvg(Integer hrAvg) {
        this.hrAvg = hrAvg;
    }

    public Integer getHrMax() {
        return hrMax;
    }

    public void setHrMax(Integer hrMax) {
        this.hrMax = hrMax;
    }

    public Integer getCadenceAvg() {
        return cadenceAvg;
    }

    public void setCadenceAvg(Integer cadenceAvg) {
        this.cadenceAvg = cadenceAvg;
    }

    public Short getIntensity() {
        return intensity;
    }

    public void setIntensity(Short intensity) {
        this.intensity = intensity;
    }

    public Integer getLap() {
        return lap;
    }

    public void setLap(Integer lap) {
        this.lap = lap;
    }

    public Float getPlannedDistance() {
        return plannedDistance;
    }

    public void setPlannedDistance(Float plannedDistance) {
        this.plannedDistance = plannedDistance;
    }

    public Float getPlannedPace() {
        return plannedPace;
    }

    public void setPlannedPace(Float plannedPace) {
        this.plannedPace = plannedPace;
    }

    public Long getPlannedTime() {
        return plannedTime;
    }

    public void setPlannedTime(Long plannedTime) {
        this.plannedTime = plannedTime;
    }
}
