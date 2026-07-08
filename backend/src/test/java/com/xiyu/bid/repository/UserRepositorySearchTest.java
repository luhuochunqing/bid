package com.xiyu.bid.repository;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositorySearchTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void searchActiveUsers_MatchesEmployeeNumber() {
        User user = User.builder()
            .username("zhangsan")
            .email("zhangsan@example.com")
            .password("p")
            .fullName("张三")
            .employeeNumber("E009")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(user);
        entityManager.flush();

        List<User> results = userRepository.searchActiveUsers("E009", 10);

        assertThat(results)
            .extracting(User::getFullName)
            .containsExactly("张三");
    }

    @Test
    void searchActiveUsers_IncludesDisabledUsersWithFullName() {
        // 选人场景：组织同步进来的真实员工即使 enabled=false（不确定登录权限）
        // 也应能被搜到选中——enabled 只管登录，不该过滤选人。
        // 详见 docs/implementation-notes/user-picker-unify-pinyin.md 的 enabled 语义决策。
        User user = User.builder()
            .username("lisi")
            .email("lisi@example.com")
            .password("p")
            .fullName("李四")
            .employeeNumber("E010")
            .role(User.Role.MANAGER)
            .enabled(false)
            .build();
        entityManager.persist(user);
        entityManager.flush();

        List<User> results = userRepository.searchActiveUsers("E010", 10);

        assertThat(results)
            .extracting(User::getFullName)
            .contains("李四");
    }

    @Test
    void searchActiveUsers_RanksEnabledUsersBeforeDisabled() {
        // 同等相关（都姓王、都是前缀匹配）时，enabled 作 tiebreaker 让可登录用户靠前
        User disabled = User.builder()
            .username("wangwu")
            .email("wangwu@example.com")
            .password("p")
            .fullName("王五")
            .employeeNumber("E011")
            .role(User.Role.MANAGER)
            .enabled(false)
            .build();
        User enabled = User.builder()
            .username("wangliu")
            .email("wangliu@example.com")
            .password("p")
            .fullName("王六")
            .employeeNumber("E012")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(disabled);
        entityManager.persist(enabled);
        entityManager.flush();

        List<User> results = userRepository.searchActiveUsers("王", 10);

        assertThat(results).extracting(User::getFullName).containsExactly("王六", "王五");
    }

    @Test
    void searchActiveUsers_ExactNameMatchBeatsEnabledNoise() {
        // 用户报告的 Bug 场景：搜"郑蓉蓉"时，被一堆 enabled=true 的无关人员挤到中间。
        // 修复后排序：精确姓名匹配 > 姓名前缀匹配 > enabled 噪声。
        // 这里"郑蓉蓉"是 enabled=false（组织同步保守置灰），另有两个 enabled=true 的"郑某"
        // 仅前缀匹配"郑"——精确匹配的郑蓉蓉必须排第一。
        User target = User.builder()
            .username("zhengrr")
            .email("zhengrr@example.com")
            .password("p")
            .fullName("郑蓉蓉")
            .employeeNumber("E050")
            .role(User.Role.MANAGER)
            .enabled(false)
            .build();
        User enabledPrefix1 = User.builder()
            .username("zhengyi")
            .email("zhengyi@example.com")
            .password("p")
            .fullName("郑一")
            .employeeNumber("E051")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        User enabledPrefix2 = User.builder()
            .username("zhenger")
            .email("zhenger@example.com")
            .password("p")
            .fullName("郑二")
            .employeeNumber("E052")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(target);
        entityManager.persist(enabledPrefix1);
        entityManager.persist(enabledPrefix2);
        entityManager.flush();

        List<User> results = userRepository.searchActiveUsers("郑蓉蓉", 10);

        assertThat(results).isNotEmpty();
        // 精确匹配的郑蓉蓉必须排第一位，不能被 enabled=true 的"郑一""郑二"挤下去
        assertThat(results.get(0).getFullName()).isEqualTo("郑蓉蓉");
    }

    @Test
    void searchActiveUsers_ExcludesUsersWithoutFullName() {
        // 空姓名的脏数据/测试账号不应出现在选人结果（full_name 列有 NOT NULL 约束，
        // 但同步数据可能写入空串，SQL 用 <> '' 排除）
        User noName = User.builder()
            .username("probe_123")
            .email("probe@example.com")
            .password("p")
            .fullName("")
            .employeeNumber("E013")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(noName);
        entityManager.flush();

        List<User> results = userRepository.searchActiveUsers("E013", 10);

        assertThat(results).isEmpty();
    }

    @Test
    void searchActiveUsers_MatchesFullNamePinyin() {
        // @PrePersist 在 persist 时回填 fullNamePinyin（PinyinUtils.toPinyin）
        User user = User.builder()
            .username("zhangsan")
            .email("zhangsan-py@example.com")
            .password("p")
            .fullName("张三")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(user);
        entityManager.flush();

        // 搜全拼 zhangsan 应命中"张三"（通过 full_name_pinyin 列匹配，而非 full_name 汉字）
        List<User> results = userRepository.searchActiveUsers("zhangsan", 10);

        assertThat(results)
            .extracting(User::getFullName)
            .contains("张三");
    }

    @Test
    void searchActiveUsers_MatchesPinyinPrefix() {
        User user = User.builder()
            .username("ouyang")
            .email("ouyang-py@example.com")
            .password("p")
            .fullName("欧阳小明")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(user);
        entityManager.flush();

        // 拼音前缀 ouyang 应命中"欧阳小明"
        List<User> prefixResults = userRepository.searchActiveUsers("ouyang", 10);
        assertThat(prefixResults)
            .extracting(User::getFullName)
            .contains("欧阳小明");

        // 拼音片段 ming 也应命中（LIKE '%ming%' 匹配 ouyangxiaoming）
        List<User> fragmentResults = userRepository.searchActiveUsers("ming", 10);
        assertThat(fragmentResults)
            .extracting(User::getFullName)
            .contains("欧阳小明");
    }

    @Test
    void searchActiveUsers_MatchesPinyinInitials() {
        // 用户报告的 Bug：搜 zrr（郑蓉蓉的拼音首字母缩写）搜不到。
        // 根因：full_name_pinyin 原来只存全拼 zhengrongrong，LIKE '%zrr%' 匹配不上。
        // 修复：PinyinUtils.toPinyin 现在返回 "zhengrongrong zrr"（全拼+空格+首字母缩写）。
        User user = User.builder()
            .username("06234")
            .email("zhengrr@example.com")
            .password("p")
            .fullName("郑蓉蓉")
            .employeeNumber("E050")
            .role(User.Role.MANAGER)
            .enabled(true)
            .build();
        entityManager.persist(user);
        entityManager.flush();

        // 搜首字母缩写 zrr 应命中"郑蓉蓉"
        List<User> initialsResults = userRepository.searchActiveUsers("zrr", 10);
        assertThat(initialsResults)
            .extracting(User::getFullName)
            .contains("郑蓉蓉");
    }

    @Test
    void roleProfile_IsEagerLoaded_RegressionP0Rollback() throws Exception {
        // P0 回滚（2026-07-08）：roleProfile 从 LAZY 恢复为 EAGER。
        // 原因：JwtAuthenticationFilter 在非事务 Filter 中调用 EffectiveRoleResolver.resolveRoleCode(user)
        // → user.getRoleCode() → roleProfile.getCode() 触发 LazyInitializationException，
        // 导致所有本地用户登录后请求均认证失败。
        // 在 Filter 链不再访问 roleProfile 或仓库统一使用 join-fetch 前，保持 EAGER。
        java.lang.reflect.Field field = User.class.getDeclaredField("roleProfile");
        field.setAccessible(true);
        jakarta.persistence.ManyToOne manyToOne = field.getAnnotation(jakarta.persistence.ManyToOne.class);

        // 必须是 @ManyToOne
        assertThat(manyToOne).as("roleProfile 必须用 @ManyToOne").isNotNull();
        // 当前策略为 EAGER（P0 回滚）
        assertThat(manyToOne.fetch())
            .as("P0 回滚：roleProfile 当前必须为 EAGER 加载，避免非事务 Filter 中触发 LazyInitializationException")
            .isEqualTo(jakarta.persistence.FetchType.EAGER);
    }

    @Test
    void roleProfile_CanBeAccessedWithinTransaction() {
        // 验证 roleProfile 在事务内能正常访问（EAGER 加载下同样成立）
        RoleProfile role = RoleProfile.builder()
            .code("/bidAdmin")
            .name("投标管理员")
            .build();
        entityManager.persist(role);

        User user = User.builder()
            .username("admin-lazy-test")
            .email("admin-lazy@example.com")
            .password("p")
            .fullName("管理员测试")
            .role(User.Role.ADMIN)
            .roleProfile(role)
            .enabled(true)
            .build();
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User loaded = userRepository.findByUsername("admin-lazy-test").orElseThrow();

        // 在 @DataJpaTest 事务内，LAZY 加载应该正常工作
        assertThat(loaded.getRoleProfile()).isNotNull();
        assertThat(loaded.getRoleProfile().getCode()).isEqualTo("/bidAdmin");
        assertThat(loaded.getRoleCode()).isEqualTo("/bidAdmin");
    }
}
