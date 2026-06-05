package com.xiyu.bid.tenderfavorite.repository;

import com.xiyu.bid.tenderfavorite.entity.TenderFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 标讯收藏数据访问接口
 */
@Repository
public interface TenderFavoriteRepository extends JpaRepository<TenderFavorite, Long> {

    /**
     * 根据用户ID和标讯ID查询收藏记录
     */
    Optional<TenderFavorite> findByUserIdAndTenderId(Long userId, Long tenderId);

    /**
     * 查询用户是否收藏了某标讯
     */
    boolean existsByUserIdAndTenderId(Long userId, Long tenderId);

    /**
     * 查询用户收藏的所有标讯ID列表
     */
    List<TenderFavorite> findByUserId(Long userId);

    /**
     * 分页查询用户收藏记录，按收藏时间降序
     */
    Page<TenderFavorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 删除用户对某标讯的收藏
     */
    void deleteByUserIdAndTenderId(Long userId, Long tenderId);

    /**
     * 统计用户收藏数量
     */
    long countByUserId(Long userId);
}
