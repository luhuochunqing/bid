package com.xiyu.bid.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 业务时区配置。
 * 统一管理所有业务时间计算所依赖的时区，消除容器默认时区与业务时区不一致导致的边界漂移。
 *
 * <p>配置项：{@code xiyu.biz.timezone}，默认为 Asia/Shanghai。
 * 在容器化部署（容器时区通常为 UTC）时显式指定业务时区，确保"今天/本周/本月"的
 * 边界计算在业务时区下正确运行。
 *
 * <p>使用时注入本组件，调用 {@link #today()} 和 {@link #now()} 获取业务时区时间。
 */
@Component
@ConfigurationProperties(prefix = "xiyu.biz")
@Getter
@Slf4j
public class BizTimezoneProperties {

    private String timezone = "Asia/Shanghai";
    private ZoneId zoneId;

    @PostConstruct
    public void init() {
        this.zoneId = ZoneId.of(timezone);
        log.info("Business timezone initialized: zoneId={}", zoneId);
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }
}
