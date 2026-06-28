// Input: Spring ApplicationContext with StringRedisTemplate + ObjectMapper beans
// Output: verifies Spring picks the @Autowired constructor (not the no-arg one), so redisTemplate is non-empty
// Pos: crm/application - regression test for CO-362 root cause: dual constructors without @Autowired caused Spring to pick no-arg ctor, making Redis persistence dead code
package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-362 回归根因：双构造方法歧义导致 Spring 选错构造方法。
 * <p>
 * OssPermissionCache 有两个构造方法：
 * <ul>
 *   <li>主构造 {@code (Optional<StringRedisTemplate>, ObjectMapper)} —— 应走 Redis</li>
 *   <li>无参构造 {@code ()} —— 纯内存，供单测</li>
 * </ul>
 * 修复前无 {@code @Autowired} 标注，Spring 默认选无参构造 → {@code redisTemplate} 恒为
 * {@code Optional.empty()} → Redis 写入分支 {@code ifPresent(...)} 永不执行。
 * <p>
 * 本测试启动最小 Spring 上下文（含 StringRedisTemplate bean），验证容器实际注入的
 * OssPermissionCache 走的是主构造，{@code redisTemplate} 非空。
 */
@SpringBootTest(
    classes = {
        OssPermissionCache.class,
        OssPermissionCacheSpringInjectionTest.TestBeanConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(OssPermissionCacheSpringInjectionTest.TestBeanConfig.class)
@DisplayName("CO-362 回归：Spring 容器必须选择 @Autowired 主构造（Redis 注入生效）")
class OssPermissionCacheSpringInjectionTest {

    @Autowired
    OssPermissionCache ossPermissionCache;

    @MockBean
    @SuppressWarnings("unused")
    private StringRedisTemplate stringRedisTemplate;

    @TestConfiguration
    static class TestBeanConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @DisplayName("容器注入的 OssPermissionCache 其 redisTemplate 应非 empty（走主构造，非无参构造）")
    void springPicksAutowiredConstructorWithRedis() {
        Optional<StringRedisTemplate> injected = ossPermissionCache.getRedisTemplate();

        assertThat(injected)
            .as("Spring 必须选择 @Autowired 主构造方法，注入 StringRedisTemplate；"
                + "若为 empty 说明 Spring 选了无参构造（CO-362 回归根因）")
            .isPresent();
    }

    @Test
    @DisplayName("注入的 StringRedisTemplate 应为容器中的 mock bean（确认是真实注入，非自造）")
    void injectedRedisTemplateIsTheContextBean() {
        Optional<StringRedisTemplate> injected = ossPermissionCache.getRedisTemplate();

        assertThat(injected).isPresent();
        assertThat(injected.get())
            .as("注入的应是 Spring 上下文中的 StringRedisTemplate bean")
            .isSameAs(stringRedisTemplate);
    }
}
