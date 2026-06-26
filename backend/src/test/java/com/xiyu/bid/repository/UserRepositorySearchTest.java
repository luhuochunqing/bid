package com.xiyu.bid.repository;

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
        // enabled 用户排在前面（优先选可登录的），disabled 排后面
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
}
