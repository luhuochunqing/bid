package com.xiyu.bid.casework.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ArchiveLogRepository extends JpaRepository<ArchiveLog, Long> {
    List<ArchiveLog> findByArchiveIdOrderByCreatedAtDesc(Long archiveId);
}
