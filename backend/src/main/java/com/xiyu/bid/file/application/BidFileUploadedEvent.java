package com.xiyu.bid.file.application;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文件已上传到 OBS 事件。
 */
@Getter
public class BidFileUploadedEvent extends ApplicationEvent {

    private final String uploadId;

    public BidFileUploadedEvent(Object source, String uploadId) {
        super(source);
        this.uploadId = uploadId;
    }
}
