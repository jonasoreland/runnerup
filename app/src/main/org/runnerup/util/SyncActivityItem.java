package org.runnerup.util;

import org.runnerup.db.entities.ActivityEntity;
import org.runnerup.export.SyncManager;

public class SyncActivityItem {
    private Long id;

    private Long duration;
    private Long startTime; // in seconds since epoch
    private String uri;
    private Double distance;
    private Integer sport;

    private Boolean skipFlag;
    private Boolean presentFlag;

    public SyncActivityItem(ActivityEntity ac) {
        this.id = ac.getId();
        this.duration = ac.getTime();
        this.startTime = ac.getStartTime();
        this.distance = ac.getDistance();
        this.sport = ac.getSport();
        this.skipFlag = Boolean.FALSE;
        this.presentFlag = Boolean.TRUE;
    }

    public SyncActivityItem() {
        this.id = null;
        this.duration = null;
        this.startTime = null;
        this.distance = null;
        this.sport = null;
        this.skipFlag = Boolean.TRUE;
        this.presentFlag = Boolean.FALSE;
    }

    public boolean isSimilarTo(SyncActivityItem o) {
        return this.getSport().equals(o.getSport()) &&
               Math.abs(this.getStartTime() - o.getStartTime()) < 30 &&
                (Math.abs(this.getDuration() - o.getDuration()) < 30 ||
                 Math.abs(this.getDistance().longValue() - o.getDistance().longValue()) < 100);

    }

    public Boolean skipActivity() {
        return skipFlag;
    }

    public void setSkipFlag(Boolean skipFlag) {
        this.skipFlag = skipFlag;
    }

    public Boolean isRelevantForSynch(SyncManager.SyncMode syncMode) {
        return (presentFlag && SyncManager.SyncMode.UPLOAD.equals(syncMode)) || (!presentFlag && SyncManager.SyncMode.DOWNLOAD.equals(syncMode));
    }

    public void setPresentFlag(Boolean presentFlag) {
        this.presentFlag = presentFlag;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setURI(String url) {
        this.uri = url;
    }

    public String getURI() {
        return uri;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Double getDistance() {
        return distance;
    }

    public void setSport(Integer sport) {
        this.sport = sport;
    }

    public Integer getSport() {
        return sport;
    }

    public boolean synchronize(SyncManager.SyncMode syncMode) {
            return !this.skipActivity() && this.isRelevantForSynch(syncMode);
    }
}
