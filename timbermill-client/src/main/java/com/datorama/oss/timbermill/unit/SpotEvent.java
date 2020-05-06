package com.datorama.oss.timbermill.unit;

import com.datorama.oss.timbermill.common.TimbermillDatesUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class SpotEvent extends Event {
    private TaskStatus status;

    public SpotEvent() {
    }

    public SpotEvent(String taskId, String name, String primaryId, String parentId, TaskStatus status, @NotNull LogParams logParams) {
        super(taskId, name, logParams, parentId);
        if (primaryId == null){
            this.primaryId = this.taskId;
        } else {
            this.primaryId = primaryId;
        }
        this.status = status;
    }

    @JsonIgnore
    public ZonedDateTime getEndTime() {
        return time;
    }

    @JsonIgnore
    @Override
    public TaskStatus getStatusFromExistingStatus(TaskStatus status) {
        return this.status;
    }

    public TaskStatus getStatus() {
        return this.status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @JsonIgnore
    @Override
    public boolean isStartEvent(){
        return true;
    }

    @JsonIgnore
    @Override
    ZonedDateTime getDateToDelete(long defaultDaysRotation) {
        return TimbermillDatesUtils.getDateToDeleteWithDefault(defaultDaysRotation, this.dateToDelete);
    }
}
