package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Qualification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 资质Repository接口
 */
@Repository
public interface QualificationRepository extends JpaRepository<Qualification, Long> {

    /**
     * 根据类型查找资质（分页）
     */
    Page<Qualification> findByType(Qualification.Type type, Pageable pageable);

    /**
     * 根据级别查找资质（分页）
     */
    Page<Qualification> findByLevel(Qualification.Level level, Pageable pageable);

    /**
     * 根据名称查找资质（模糊查询，分页）
     */
    Page<Qualification> findByNameContaining(String name, Pageable pageable);

    /**
     * 查找未过期的资质（分页）
     */
    Page<Qualification> findByExpiryDateAfter(LocalDate date, Pageable pageable);

    /**
     * 查找即将过期的资质（分页）
     */
    Page<Qualification> findByExpiryDateBefore(LocalDate date, Pageable pageable);

    /**
     * 根据类型和级别查找资质（分页）
     */
    Page<Qualification> findByTypeAndLevel(Qualification.Type type, Qualification.Level level, Pageable pageable);

    /**
     * 统计类型的资质数量
     */
    Long countByType(Qualification.Type type);

    /**
     * 根据类型查找资质（限制返回数量）
     */
    List<Qualification> findByType(Qualification.Type type);

    /**
     * 根据级别查找资质（限制返回数量）
     */
    List<Qualification> findByLevel(Qualification.Level level);
}
