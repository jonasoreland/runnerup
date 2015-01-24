package org.runnerup.activity;

import org.runnerup.common.util.Constants;
import org.runnerup.db.Column;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by itdog on 21.01.15.
 */
public class SportActivity extends BaseEntity{
    @Column(name = "_id", isAutoincrement = true)
    private long id;
    @Column(name = Constants.DB.ACTIVITY.START_TIME)
    private Long startTime;
    @Column(name = Constants.DB.ACTIVITY.DISTANCE)
    private Float distance;
    @Column(name = Constants.DB.ACTIVITY.TIME)
    private Long time;
    @Column(name = Constants.DB.ACTIVITY.NAME)
    private String name;
    @Column(name = Constants.DB.ACTIVITY.COMMENT)
    private String comment;
    @Column(name = Constants.DB.ACTIVITY.SPORT)
    private Short sport;
    @Column(name = Constants.DB.ACTIVITY.AVG_HR)
    private Integer hrAvg;
    @Column(name = Constants.DB.ACTIVITY.MAX_HR)
    private Integer hrMax;
    @Column(name = Constants.DB.ACTIVITY.AVG_CADENCE)
    private Integer cadenceAvg;
    @Column(name = Constants.DB.ACTIVITY.TYPE)
    private String type;
    @Column(name = Constants.DB.ACTIVITY.EXTERNAL_ID)
    private String externalId;
    private List<Lap> laps = new ArrayList<Lap>();
    private List<LocationData> locationData = new ArrayList<LocationData>();

    public List<Lap> laps() {
        return laps;
    }

    public List<LocationData> locationData() {
        return locationData;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Short getSport() {
        return sport;
    }

    public void setSport(Short sport) {
        this.sport = sport;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}
