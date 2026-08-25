package com.xiyu.bid.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 验证 {@link ConfigurationBindingProbe} 依赖的"isBound() 识别法"能正确区分配置命中与失配。
 *
 * <p>回归锚点：若某 {@code @ConfigurationProperties} 前缀因 YAML 缩进/键路径错位而未被 Environment
 * 命中，{@code Binder.bind(prefix, Map.class).isBound()} 必须返回 false，探针据此发出 WARN。
 *
 * <p>被动识别（回归保护）：{@link #allRealConfiguredPrefixesMustBeBound} 直接加载真实的
 * {@code application.yml} 及全部 profile 专属 yml，对反射到的真实配置类前缀逐个断言已绑定。
 * 断言集合取自<b>代码侧反射</b>（稳定、不受 yml 污染）：一旦 YAML 缩进/键路径把某前缀挤出
 * Environment，代码侧前缀仍在断言集内 → isBound=false → 测试立刻红，把缩进回归挡在 CI，0 运行成本。
 */
class ConfigurationBindingProbeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    /**
     * 被动识别（回归保护）：加载真实 {@code application.yml} + 全部 profile yml，断言每个真实配置类
     * 前缀均已绑定。断言集合取自代码侧反射——YAML 缩进/键路径错位会让某前缀从 Environment 消失，
     * 但代码侧仍有此前缀 → isBound=false → 测试立刻红。
     *
     * @see #NOT_YML_BOUND_BY_DESIGN 设计上不依赖静态 yml 的前缀（豁免）
     */
    @Test
    void allRealConfiguredPrefixesMustBeBound() throws IOException {
        StandardEnvironment env = realConfigEnvironment();
        Binder binder = Binder.get(env);
        List<String> missing = new ArrayList<>();
        for (String prefix : realConfiguredPrefixes()) {
            if (!NOT_YML_BOUND_BY_DESIGN.contains(prefix) && !ConfigurationBindingProbe.isBound(binder, prefix)) {
                missing.add(prefix);
            }
        }
        assertThat(missing).as("以下 @ConfigurationProperties 前缀在真实 yml 中未命中"
                + "（YAML 缩进/键路径错位?）: %s", missing).isEmpty();
    }

    /**
     * 设计上不依赖静态 yml 绑定、仅靠 Java 默认值 / 运行时环境变量注入的前缀。
     * 它们不在本测试断言范围内：本测试专门守 YAML 缩进回归，而非这些 env 注入项。
     */
    private static final Set<String> NOT_YML_BOUND_BY_DESIGN = Set.of(
            // 测试类自身嵌套的 SampleConfig，反射扫描时误入
            "xiyu.sample",
            // 纯 Java 默认值，无 yml 块（时区默认 Asia/Shanghai），可选 XIYU_BIZ_* 覆盖
            "xiyu.biz",
            // 全默认权重，无 yml 块
            "xiyu.case-slice.match-policy",
            // 全默认值，无 yml 块，可选 env 覆盖
            "performance.bundle-export");

    /**
     * 加载真实 {@code application.yml} 及全部 profile 专属 yml 到 StandardEnvironment。
     * 关键：直接读取 ClassPath 上的真实配置文件，使用与 Spring Boot 相同的
     * {@link YamlPropertySourceLoader}；某前缀越界/缩进错位 → 未命中 → 测试红。
     */
    private static StandardEnvironment realConfigEnvironment() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        for (String file : List.of("application.yml", "application-dev.yml",
                "application-prod.yml", "application-mysql.yml", "application-e2e.yml")) {
            new YamlPropertySourceLoader().load("file:" + file, new ClassPathResource(file))
                    .forEach(sources::addLast);
        }
        return env;
    }

    /** 反射扫描真实配置类，收集全部 {@code @ConfigurationProperties} 前缀。 */
    private static List<String> realConfiguredPrefixes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));
        List<String> prefixes = new ArrayList<>();
        scanner.findCandidateComponents("com.xiyu.bid").forEach(bd -> {
            if (bd instanceof AnnotatedBeanDefinition abd) {
                Map<String, Object> attrs = abd.getMetadata()
                        .getAnnotationAttributes(ConfigurationProperties.class.getName());
                if (attrs != null && attrs.get("prefix") != null) {
                    prefixes.add((String) attrs.get("prefix"));
                }
            }
        });
        return prefixes;
    }

    @Test
    void prefixMatchWhenEnvironmentHitShouldReportBound() {
        runner.withUserConfiguration(SampleConfig.class)
                .withPropertyValues("xiyu.sample.foo=bar")
                .run(ctx -> {
                    Binder binder = Binder.get(ctx.getEnvironment());
                    assertThat(binder.bind("xiyu.sample", Bindable.of(Map.class)).isBound())
                            .as("Environment 命中 xiyu.sample.foo 时应 isBound=true")
                            .isTrue();
                });
    }

    @Test
    void prefixUntouchedInEnvironmentShouldReportUnbound() {
        runner.withUserConfiguration(SampleConfig.class)
                .run(ctx -> {
                    Binder binder = Binder.get(ctx.getEnvironment());
                    assertThat(binder.bind("xiyu.sample", Bindable.of(Map.class)).isBound())
                            .as("Environment 无任何 xiyu.sample.* 属性时应 isBound=false（缩进/键路径错位场景）")
                            .isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan
    static class SampleConfig {}

    @ConfigurationProperties(prefix = "xiyu.sample")
    static class SampleProperties {
        private String foo;

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }
    }
}