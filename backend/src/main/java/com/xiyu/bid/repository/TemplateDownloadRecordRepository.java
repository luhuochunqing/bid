package com.xiyu.bid.repository;

import com.xiyu.bid.entity.TemplateDownloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TemplateDownloadRecordRepository extends JpaRepository<TemplateDownloadRecord, Long> {
    long countByTemplateId(Long templateId);

    @Query("select record.template.id, count(record) from TemplateDownloadRecord record where record.template.id in :templateIds group by record.template.id")
    List<Object[]> countGroupedByTemplateIds(Collection<Long> templateIds);
}
