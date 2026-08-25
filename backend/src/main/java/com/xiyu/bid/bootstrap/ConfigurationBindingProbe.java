package com.xiyu.bid.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 通用配置绑定探针：识别"声明了 prefix 但在运行环境无任何命中"的 {@code @ConfigurationProperties}。
 *
 * <p>背景：YAML 缩进错位 / 键路径写错时，Spring 不会绑定失败，而会把该前缀下属性静默回退到默认值
 * （如 {@code enabled} 回退 false），且不产生任何异常或日志。这类"配置静默失配"问题极难在
 * code review / 单测 / 架构门禁阶段发现——因为它们根本不产生失败信号。
 *
 * <p>本探针在启动期扫描 Spring 已识别到的所有配置类 prefix，逐个用
 * {@link Binder#bind(String, Bindable)} 检查 Environment 是否有命中。
 * 失配者以 WARN 输出（不阻断启动），把"静默回退"变成"有信号的启动期告警"。
 *
 * <p>参照：本类借鉴 {@code StartupConfigurationSummaryLogger} 的启动期检测定位，
 * 但不锁定具体配置类——它从第一性出发覆盖全部 {@code @ConfigurationProperties}，
 * 避免为每个配置类手写探针。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class ConfigurationBindingProbe implements ApplicationRunner {

    private final ApplicationContext context;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        Binder binder = Binder.get(environment);
        List<String> misaligned = new ArrayList<>();

        for (ConfigurationPropertiesBean bean : ConfigurationPropertiesBean.getAll(context).values()) {
            String prefix = bean.getAnnotation().prefix();
            if (prefix.isEmpty()) {
                prefix = bean.getAnnotation().value();
            }
            if (prefix.isEmpty()) {
                continue;
            }
            if (!isBound(binder, prefix)) {
                misaligned.add(bean.getName() + " (prefix=" + prefix + ")");
            }
        }

        if (misaligned.isEmpty()) {
            log.info("[cfg-binding-probe] 全部 {} 个 @ConfigurationProperties 均有 Environment 命中，配置绑定健康",
                    ConfigurationPropertiesBean.getAll(context).size());
        } else {
            log.warn("[cfg-binding-probe] 共有 {} 个 @ConfigurationProperties 失配: {}", misaligned.size(), misaligned);
        }
    }

    /**
     * 判定某 prefix 在给定 {@link Binder} 下是否被 Environment 命中。
     *
     * <p>用宽松 Map 绑定判断该 prefix 下是否有任何属性被命中。prefix 完全未出现在配置源
     * （YAML 缩进 / 键路径错位）时返回 false。纯函数，供探针与被动识别回归测试共用。
     */
    static boolean isBound(Binder binder, String prefix) {
        return binder.bind(prefix, Bindable.of(Map.class)).isBound();
    }
}