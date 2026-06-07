package com.xiyu.bid.resources.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BarSiteSopRequest {

    private String resetUrl;

    private String unlockUrl;

    private List<String> contacts = new ArrayList<>();

    private List<RequiredDocItem> requiredDocs = new ArrayList<>();

    private List<FaqItem> faqs = new ArrayList<>();

    private List<HistoryItem> history = new ArrayList<>();

    private String estimatedTime;

    @Data
    public static class RequiredDocItem {
        private String name;
        private boolean required;
    }

    @Data
    public static class FaqItem {
        private String q;
        private String a;
    }

    @Data
    public static class HistoryItem {
        private String date;
        private String action;
        private String user;
        private String duration;
    }
}
