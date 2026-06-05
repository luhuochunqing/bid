package com.xiyu.bid.collaboration.repository;

import com.xiyu.bid.collaboration.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评论数据访问接口
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 根据线程ID查找未删除的评论（按创建时间升序）
     */
    List<Comment> findByThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(Long threadId);

    /**
     * 查找提及特定用户的评论（未删除）
     */
    List<Comment> findByMentionsContainingAndIsDeletedFalse(String mentions);
}
