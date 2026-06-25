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
    void searchActiveUsers_ExcludesDisabledUsers() {
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
