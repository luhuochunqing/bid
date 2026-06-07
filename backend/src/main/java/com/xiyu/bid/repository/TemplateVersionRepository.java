package com.xiyu.bid.repository;

import com.xiyu.bid.entity.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {
    List<TemplateVersion> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
}
