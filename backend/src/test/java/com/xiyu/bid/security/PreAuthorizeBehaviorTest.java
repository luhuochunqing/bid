package com.xiyu.bid.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 Spring Security 类级+方法级 @PreAuthorize 的实际行为。
 *
 * Spring Boot 3.2.0 = Spring Security 6.2.x
 *
 * 结论（已验证）：方法级 @PreAuthorize 覆盖类级，不是 AND 逻辑。
 * 类级仅作为没有方法级注解的方法的默认值。
 */
@SpringBootTest(
    classes = {
        PreAuthorizeBehaviorTest.TestConfig.class,
        PreAuthorizeBehaviorTest.ControllerA.class,
        PreAuthorizeBehaviorTest.ControllerB.class,
        PreAuthorizeBehaviorTest.ControllerC.class,
        PreAuthorizeBehaviorTest.ControllerD.class,
        PreAuthorizeBehaviorTest.ControllerE.class,
        PreAuthorizeBehaviorTest.TestUserConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@DisplayName("Spring Security @PreAuthorize 类级+方法级行为验证")
class PreAuthorizeBehaviorTest {

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestConfig {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Configuration
    static class TestUserConfig {
        @Bean
        public UserDetailsService userDetailsService() {
            UserDetails admin = User.withUsername("admin")
                .password("{noop}pass").roles("ADMIN").build();
            UserDetails manager = User.withUsername("manager")
                .password("{noop}pass").roles("MANAGER").build();
            UserDetails bidspecialist = User.withUsername("bidspecialist")
                .password("{noop}pass").roles("BID_TEAM").build();
            UserDetails bidLead = User.withUsername("bidlead")
                .password("{noop}pass").roles("BID_TEAMLEADER").build();
            UserDetails bidSenior = User.withUsername("bidsenior")
                .password("{noop}pass").roles("BIDADMIN").build();
            return new InMemoryUserDetailsManager(admin, manager, bidspecialist, bidLead, bidSenior);
        }
    }

    // ==================== Controller A：类级+方法级 不同角色 ====================
    // 类级: ADMIN/MANAGER/BID_TEAM
    // 方法级: ADMIN/BID_TEAMLEADER/BIDADMIN

    @RestController
    @RequestMapping("/test/a")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAM')")
    static class ControllerA {
        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')")
        public String endpoint() {
            return "A-ok";
        }
    }

    // ==================== Controller B：仅类级 ====================
    // 类级: ADMIN/MANAGER/BID_TEAM

    @RestController
    @RequestMapping("/test/b")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAM')")
    static class ControllerB {
        @GetMapping
        public String endpoint() {
            return "B-ok";
        }
    }

    // ==================== Controller C：仅方法级 ====================
    // 方法级: ADMIN/BID_TEAMLEADER/BIDADMIN

    @RestController
    @RequestMapping("/test/c")
    static class ControllerC {
        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')")
        public String endpoint() {
            return "C-ok";
        }
    }

    // ==================== Controller D：类级比方法级更严格 ====================
    // 类级: ADMIN/MANAGER（没有 BID_TEAM）
    // 方法级: ADMIN/BID_TEAM/BID_TEAMLEADER

    @RestController
    @RequestMapping("/test/d")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    static class ControllerD {
        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAM', 'BID_TEAMLEADER')")
        public String endpoint() {
            return "D-ok";
        }
    }

    // ==================== Controller E：混合方法（有/无方法级注解） ====================
    // 类级: ADMIN/MANAGER/BID_TEAM
    // endpoint1: 有方法级 ADMIN/BID_TEAMLEADER
    // endpoint2: 无方法级（使用类级默认）

    @RestController
    @RequestMapping("/test/e")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAM')")
    static class ControllerE {
        @GetMapping("/with-method")
        @PreAuthorize("hasAnyRole('ADMIN', 'BID_TEAMLEADER')")
        public String withMethodLevel() {
            return "E-withMethod";
        }

        @GetMapping("/without-method")
        public String withoutMethodLevel() {
            return "E-withoutMethod";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // ==================== 场景 A：类级+方法级 不同角色 ====================

    @Test
    @DisplayName("A: ADMIN 在两个注解中都存在 → 200")
    void A_admin() throws Exception {
        mockMvc.perform(get("/test/a").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().string("A-ok"));
    }

    @Test
    @DisplayName("A: MANAGER 类级通过，方法级失败 → 403（方法级覆盖）")
    void A_manager() throws Exception {
        mockMvc.perform(get("/test/a").with(user("manager").roles("MANAGER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A: BID_TEAMLEADER 类级失败，方法级通过 → 200（方法级覆盖类级）")
    void A_bidLead() throws Exception {
        mockMvc.perform(get("/test/a").with(user("bidlead").roles("BID_TEAMLEADER")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A: BID_TEAM 类级通过，方法级失败 → 403（方法级覆盖）")
    void A_bidspecialist() throws Exception {
        mockMvc.perform(get("/test/a").with(user("bidspecialist").roles("BID_TEAM")))
            .andExpect(status().isForbidden());
    }

    // ==================== 场景 B：仅类级 ====================

    @Test
    @DisplayName("B: ADMIN/MANAGER/BID_TEAM 可访问")
    void B_admin() throws Exception {
        mockMvc.perform(get("/test/b").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void B_manager() throws Exception {
        mockMvc.perform(get("/test/b").with(user("manager").roles("MANAGER")))
            .andExpect(status().isOk());
    }

    @Test
    void B_bidspecialist() throws Exception {
        mockMvc.perform(get("/test/b").with(user("bidspecialist").roles("BID_TEAM")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("B: BID_TEAMLEADER 不在类级角色中 → 403")
    void B_bidLead() throws Exception {
        mockMvc.perform(get("/test/b").with(user("bidlead").roles("BID_TEAMLEADER")))
            .andExpect(status().isForbidden());
    }

    // ==================== 场景 C：仅方法级 ====================

    @Test
    @DisplayName("C: ADMIN/BID_TEAMLEADER/BIDADMIN 可访问")
    void C_admin() throws Exception {
        mockMvc.perform(get("/test/c").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void C_bidLead() throws Exception {
        mockMvc.perform(get("/test/c").with(user("bidlead").roles("BID_TEAMLEADER")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("C: MANAGER 不在方法级角色中 → 403")
    void C_manager() throws Exception {
        mockMvc.perform(get("/test/c").with(user("manager").roles("MANAGER")))
            .andExpect(status().isForbidden());
    }

    // ==================== 场景 D：类级比方法级更严格 ====================

    @Test
    @DisplayName("D: ADMIN 在两个注解中都存在 → 200")
    void D_admin() throws Exception {
        mockMvc.perform(get("/test/d").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("D: BID_TEAM 类级失败，方法级通过 → 200（方法级覆盖类级）")
    void D_bidspecialist() throws Exception {
        mockMvc.perform(get("/test/d").with(user("bidspecialist").roles("BID_TEAM")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("D: BID_TEAMLEADER 类级失败，方法级通过 → 200（方法级覆盖类级）")
    void D_bidLead() throws Exception {
        mockMvc.perform(get("/test/d").with(user("bidlead").roles("BID_TEAMLEADER")))
            .andExpect(status().isOk());
    }

    // ==================== 场景 E：混合方法（有/无方法级注解） ====================

    @Test
    @DisplayName("E-withMethod: BID_TEAMLEADER 方法级通过 → 200")
    void E_withMethod_bidLead() throws Exception {
        mockMvc.perform(get("/test/e/with-method").with(user("bidlead").roles("BID_TEAMLEADER")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("E-withMethod: MANAGER 方法级失败 → 403")
    void E_withMethod_manager() throws Exception {
        mockMvc.perform(get("/test/e/with-method").with(user("manager").roles("MANAGER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("E-withoutMethod: BID_TEAMLEADER 类级失败 → 403（类级作为默认值生效）")
    void E_withoutMethod_bidLead() throws Exception {
        mockMvc.perform(get("/test/e/without-method").with(user("bidlead").roles("BID_TEAMLEADER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("E-withoutMethod: MANAGER 类级通过 → 200")
    void E_withoutMethod_manager() throws Exception {
        mockMvc.perform(get("/test/e/without-method").with(user("manager").roles("MANAGER")))
            .andExpect(status().isOk());
    }
}
