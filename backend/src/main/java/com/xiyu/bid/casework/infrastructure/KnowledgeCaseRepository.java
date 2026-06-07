package com.xiyu.bid.casework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeCaseRepository extends JpaRepository<KnowledgeCase, Long>, JpaSpecificationExecutor<KnowledgeCase> {
}
