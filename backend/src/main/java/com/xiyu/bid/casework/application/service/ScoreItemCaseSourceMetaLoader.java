// Input: Collection<Long> sourceProjectIds
// Output: Map<sourceProjectId, SourceMeta(bidEntity, projectStatus)>
// Pos: backend/src/main/java/com/xiyu/bid/casework/application/service/ - App Service Layer
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 为推荐案例批量装配来源项目元信息（招标主体 / 项目状态）。
 */
@Component
public class ScoreItemCaseSourceMetaLoader {

    public record SourceMeta(String bidEntity, String projectStatus) {
    }

    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;

    public ScoreItemCaseSourceMetaLoader(ProjectRepository projectRepository, TenderRepository tenderRepository) {
        this.projectRepository = projectRepository;
        this.tenderRepository = tenderRepository;
    }

    public Map<Long, SourceMeta> load(Collection<Long> sourceProjectIds) {
        Set<Long> ids = sourceProjectIds == null
                ? Set.of()
                : sourceProjectIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<Project> sourceProjects = projectRepository.findAllById(ids);
        Set<Long> tenderIds = sourceProjects.stream()
                .map(Project::getTenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Tender> tenderById = tenderIds.isEmpty()
                ? Map.of()
                : tenderRepository.findAllById(tenderIds).stream()
                        .collect(Collectors.toMap(Tender::getId, tender -> tender, (left, right) -> left));

        Map<Long, SourceMeta> result = new HashMap<>();
        for (Project project : sourceProjects) {
            Tender tender = project.getTenderId() == null ? null : tenderById.get(project.getTenderId());
            result.put(project.getId(), new SourceMeta(resolveBidEntity(tender), resolveStatus(project)));
        }
        return result;
    }

    private static String resolveBidEntity(Tender tender) {
        if (tender == null) {
            return null;
        }
        if (tender.getPurchaserName() != null && !tender.getPurchaserName().isBlank()) {
            return tender.getPurchaserName();
        }
        return tender.getTenderAgency();
    }

    private static String resolveStatus(Project project) {
        return project.getStatus() == null ? null : project.getStatus().name();
    }
}
