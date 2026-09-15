// Input: projectId → BidTenderDocumentSnapshotRepository
// Output: TenderQualificationInfo（结构化资格项与标书原文）
// Pos: casework/infrastructure/ - 招标文件资格查询 JPA 适配器
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.casework.application.port.TenderQualificationQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * TenderQualificationQueryPort 的 JPA 适配器。
 *
 * <p>封装 BidTenderDocumentSnapshotRepository 访问，将快照中的 profileJson 与
 * extractedText 安全反序列化与解耦，使 application 层保持纯编排。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderQualificationQueryAdapter implements TenderQualificationQueryPort {

    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<TenderQualificationInfo> findByProjectId(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        return snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .map(snapshot -> new TenderQualificationInfo(
                        parseQualificationRequirements(snapshot.getProfileJson()),
                        snapshot.getExtractedText()
                ));
    }

    private List<String> parseQualificationRequirements(String profileJson) {
        if (profileJson == null || profileJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(profileJson);
            JsonNode qualNode = root.get("qualificationRequirements");
            if (qualNode != null && qualNode.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : qualNode) {
                    if (item.isTextual() && !item.asText().isBlank()) {
                        result.add(item.asText().trim());
                    }
                }
                return result;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse qualificationRequirements from profileJson: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
