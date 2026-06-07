package com.xiyu.bid.settings.repository;

import com.xiyu.bid.settings.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    Optional<SystemSetting> findByConfigKey(String configKey);
}
