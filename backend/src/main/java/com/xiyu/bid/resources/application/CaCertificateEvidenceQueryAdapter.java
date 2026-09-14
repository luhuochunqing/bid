// Input: 交易平台关键词与数量上限
// Output: 电子标 CA 证书证据与知识库匹配记录列表
// Pos: resources/application/ - compliance CA 证书证据端口适配器（依赖倒置实现侧）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.application;

import com.xiyu.bid.compliance.domain.policy.CaCertificateEvidence;
import com.xiyu.bid.compliance.domain.policy.CaCertificateEvidencePort;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 电子标 CA 证书证据查询适配器（compliance 端口的 resources 实现）.
 *
 * <p>实现 compliance 定义的 {@link CaCertificateEvidencePort}（依赖倒置），
 * 查询真实 CA 证书台账记录并转为领域证据与 KnowledgeMatch；保证架构无环.
 */
@Component
public class CaCertificateEvidenceQueryAdapter implements CaCertificateEvidencePort {

    private static final String INACTIVE_STATUS = "INACTIVE";
    private final CaCertificateRepository certificateRepository;

    public CaCertificateEvidenceQueryAdapter(final CaCertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Override
    public List<KnowledgeMatch> findCaMatches(final String platformKeyword, final int limit) {
        if (certificateRepository == null) {
            return List.of();
        }
        int max = limit > 0 ? limit : 20;
        List<CaCertificateEvidence> evidences = findAvailableCertificates();
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (CaCertificateEvidence evidence : evidences) {
            if (evidence.matchesPlatform(platformKeyword)) {
                matches.add(evidence.toKnowledgeMatch(true));
                if (matches.size() >= max) {
                    break;
                }
            }
        }
        return matches;
    }

    @Override
    public List<CaCertificateEvidence> findAvailableCertificates() {
        if (certificateRepository == null) {
            return List.of();
        }
        List<CaCertificateEntity> entities = certificateRepository.findByStatusNot(INACTIVE_STATUS);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(this::toEvidence)
                .toList();
    }

    private CaCertificateEvidence toEvidence(final CaCertificateEntity entity) {
        return new CaCertificateEvidence(
                entity.getId(),
                entity.getCaType(),
                entity.getSealType(),
                entity.getIssuer(),
                entity.getExpiryDate(),
                entity.getRelatedPlatforms(),
                entity.getBorrowStatus(),
                entity.getStatus(),
                entity.getCustodianName(),
                entity.getHolderName()
        );
    }
}
