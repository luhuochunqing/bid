package com.xiyu.bid.tendersource.repository;

import com.xiyu.bid.tendersource.entity.TenderSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 标讯源配置数据访问接口。
 * 单例模式，id 始终为 1。
 */
@Repository
public interface TenderSourceConfigRepository extends JpaRepository<TenderSourceConfig, Long> {
}
