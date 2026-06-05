package com.xiyu.bid.analytics.model;

import com.xiyu.bid.entity.Project;

import java.math.BigDecimal;

public class TeamAggregate {

    private long projectCount;
    private long managedProjectCount;
    private long wonCount;
    private long activeProjectCount;
    private long totalTaskCount;
    private long completedTaskCount;
    private long overdueTaskCount;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    public void addProject(Project.Status status, BigDecimal amount, boolean won, boolean active, boolean manager) {
        projectCount++;
        totalAmount = totalAmount.add(amount == null ? BigDecimal.ZERO : amount);
        if (won) {
            wonCount++;
        }
        if (active) {
            activeProjectCount++;
        }
        if (manager) {
            managedProjectCount++;
        }
    }

    public void setTaskMetrics(long pTotalTaskCount, long pCompletedTaskCount, long pOverdueTaskCount) {
        this.totalTaskCount = pTotalTaskCount;
        this.completedTaskCount = pCompletedTaskCount;
        this.overdueTaskCount = pOverdueTaskCount;
    }

    public long projectCount() {
        return projectCount;
    }

    public long managedProjectCount() {
        return managedProjectCount;
    }

    public long wonCount() {
        return wonCount;
    }

    public long activeProjectCount() {
        return activeProjectCount;
    }

    public long totalTaskCount() {
        return totalTaskCount;
    }

    public long completedTaskCount() {
        return completedTaskCount;
    }

    public long overdueTaskCount() {
        return overdueTaskCount;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }
}
