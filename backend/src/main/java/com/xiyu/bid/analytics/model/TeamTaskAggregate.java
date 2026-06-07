package com.xiyu.bid.analytics.model;

public class TeamTaskAggregate {
    private long totalTaskCount;
    private long completedTaskCount;
    private long overdueTaskCount;

    public static TeamTaskAggregate empty() {
        return new TeamTaskAggregate();
    }

    public long totalTaskCount() {
        return totalTaskCount;
    }

    public void setTotalTaskCount(long pTotalTaskCount) {
        this.totalTaskCount = pTotalTaskCount;
    }

    public long completedTaskCount() {
        return completedTaskCount;
    }

    public void setCompletedTaskCount(long pCompletedTaskCount) {
        this.completedTaskCount = pCompletedTaskCount;
    }

    public long overdueTaskCount() {
        return overdueTaskCount;
    }

    public void setOverdueTaskCount(long pOverdueTaskCount) {
        this.overdueTaskCount = pOverdueTaskCount;
    }
}
