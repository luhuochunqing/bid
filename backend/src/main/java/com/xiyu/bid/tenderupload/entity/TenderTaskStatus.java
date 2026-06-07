package com.xiyu.bid.tenderupload.entity;

public enum TenderTaskStatus {
    QUEUED,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    DLQ
}
