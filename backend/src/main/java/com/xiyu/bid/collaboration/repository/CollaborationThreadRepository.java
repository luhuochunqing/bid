package com.xiyu.bid.collaboration.repository;

import com.xiyu.bid.collaboration.entity.CollaborationThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 协作讨论线程数据访问接口
 */
@Repository
public interface CollaborationThreadRepository extends JpaRepository<CollaborationThread, Long> {

    /**
     * 根据项目ID查找讨论线程
     */
    List<CollaborationThread> findByProjectId(Long projectId);
}
