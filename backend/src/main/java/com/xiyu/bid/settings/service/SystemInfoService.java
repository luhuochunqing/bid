package com.xiyu.bid.settings.service;

import com.xiyu.bid.settings.dto.SystemInfoResponse;
import com.xiyu.bid.settings.dto.SystemInfoResponse.BuildInfo;
import com.xiyu.bid.settings.dto.SystemInfoResponse.ChangelogEntry;
import com.xiyu.bid.settings.dto.SystemInfoResponse.GitInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class SystemInfoService {

    @Value("${app.version:1.0.3}")
    private String projectVersion;

    @Value("${project.artifact:xiyu-bid-poc}")
    private String projectArtifact;

    @Value("${project.name:XiYu Bid POC}")
    private String projectName;

    @Value("${project.description:西域数智化投标管理平台}")
    private String projectDescription;

    public SystemInfoResponse getSystemInfo() {
        BuildInfo buildInfo = buildBuildInfo();
        GitInfo gitInfo = buildGitInfo();
        List<ChangelogEntry> changelog = buildDefaultChangelog();

        return SystemInfoResponse.builder()
                .build(buildInfo)
                .git(gitInfo)
                .changelog(changelog)
                .build();
    }

    private BuildInfo buildBuildInfo() {
        Instant buildTime = Instant.now();
        try {
            Properties buildProps = loadGitProperties();
            if (buildProps != null) {
                String buildTimeStr = buildProps.getProperty("git.build.time");
                if (buildTimeStr != null && !buildTimeStr.isEmpty()) {
                    try {
                        buildTime = Instant.parse(buildTimeStr);
                    } catch (Exception e) {
                        log.debug("Failed to parse build time: {}", buildTimeStr);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not load build properties", e);
        }

        return BuildInfo.builder()
                .version(projectVersion)
                .artifact(projectArtifact)
                .name(projectName)
                .description(projectDescription)
                .buildTime(buildTime)
                .build();
    }

    private GitInfo buildGitInfo() {
        String branch = "unknown";
        String commitId = "unknown";
        String commitIdAbbrev = "unknown";
        String commitTime = "unknown";
        String commitMessage = "unknown";
        String commitAuthorName = "unknown";

        try {
            Properties gitProps = loadGitProperties();
            if (gitProps != null) {
                branch = gitProps.getProperty("git.branch", branch);
                commitId = gitProps.getProperty("git.commit.id.full", commitId);
                commitIdAbbrev = gitProps.getProperty("git.commit.id.abbrev", commitIdAbbrev);
                commitTime = gitProps.getProperty("git.commit.time", commitTime);
                commitMessage = gitProps.getProperty("git.commit.message.full", commitMessage);
                commitAuthorName = gitProps.getProperty("git.commit.author.name", commitAuthorName);

                if (commitMessage != null && commitMessage.length() > 100) {
                    commitMessage = commitMessage.substring(0, 97) + "...";
                }
            }
        } catch (Exception e) {
            log.debug("Could not load git properties", e);
        }

        return GitInfo.builder()
                .branch(branch)
                .commitId(commitId)
                .commitIdAbbrev(commitIdAbbrev)
                .commitTime(commitTime)
                .commitMessage(commitMessage)
                .commitAuthorName(commitAuthorName)
                .build();
    }

    private Properties loadGitProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) {
                props.load(is);
                return props;
            }
        } catch (IOException e) {
            log.debug("Could not load git.properties", e);
        }
        return null;
    }

    private List<ChangelogEntry> buildDefaultChangelog() {
        List<ChangelogEntry> changelog = new ArrayList<>();

        changelog.add(ChangelogEntry.builder()
                .version(projectVersion)
                .date(DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        .withZone(ZoneId.of("Asia/Shanghai"))
                        .format(Instant.now()))
                .content("当前版本")
                .type("current")
                .build());

        changelog.add(ChangelogEntry.builder()
                .version("1.0.2")
                .date("2026-04-01")
                .content("历史版本记录可通过 CHANGELOG.md 维护")
                .type("history")
                .build());

        changelog.add(ChangelogEntry.builder()
                .version("1.0.1")
                .date("2026-03-15")
                .content("初始版本发布")
                .type("history")
                .build());

        return changelog;
    }
}
