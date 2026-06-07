package com.xiyu.bid.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemInfoResponse {

    private BuildInfo build;
    private GitInfo git;
    private List<ChangelogEntry> changelog;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuildInfo {
        private String version;
        private String artifact;
        private String name;
        private String description;
        private Instant buildTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GitInfo {
        private String branch;
        private String commitId;
        private String commitIdAbbrev;
        private String commitTime;
        private String commitMessage;
        private String commitAuthorName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangelogEntry {
        private String version;
        private String date;
        private String content;
        private String type;
    }
}
