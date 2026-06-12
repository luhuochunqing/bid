package com.xiyu.bid.casework.domain.model;

import java.time.LocalDateTime;

/**
 * 案例库案例的领域读模型。
 *
 * <p>策略层（CaseExportPolicy、KnowledgeCaseMatchPolicy）只依赖此接口，
 * 不直接依赖基础设施层的 JPA Entity。实现类由基础设施层提供。
 */
public interface KnowledgeCaseReadModel {

    Long getId();

    Long getSourceProjectId();

    String getSourceProjectName();

    String getScoringPointTitle();

    String getRequirementRaw();

    String getResponseText();

    Integer getReuseCount();

    Boolean getIsPinned();

    String getStatus();

    String getCustomerType();

    String getProjectType();

    String getBidResult();

    String getScoringCategory();

    String getProductLine();

    LocalDateTime getCreatedAt();
}
