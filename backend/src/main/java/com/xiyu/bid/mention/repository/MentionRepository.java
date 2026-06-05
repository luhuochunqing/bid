package com.xiyu.bid.mention.repository;

import com.xiyu.bid.mention.entity.Mention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentionRepository extends JpaRepository<Mention, Long> {

    List<Mention> findByMentionedUserIdOrderByCreatedAtDesc(Long userId);

    List<Mention> findBySourceEntityTypeAndSourceEntityId(String entityType, Long entityId);
}
