package com.xiyu.bid.repository;

import com.xiyu.bid.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** CO-382: 批量按用户名查询，用于任务看板创建人姓名解析。 */
    List<User> findAllByUsernameIn(Collection<String> usernames);

    Optional<User> findByEmail(String email);

    Optional<User> findByWecomUserId(String wecomUserId);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmployeeNumber(String employeeNumber);

    List<User> findByFullName(String fullName);

    List<User> findByFullNameIn(Collection<String> fullNames);

    Optional<User> findByExternalOrgSourceAppAndExternalOrgUserId(String sourceApp, String externalOrgUserId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByRoleProfile_CodeIgnoreCaseAndEnabledTrue(String roleCode);

    long countByRoleProfile_Id(Long roleProfileId);

    /** 查询所有启用用户。 */
    List<User> findByEnabledTrue();

    /** 查询所有用户（包括 OSS 用户），按部门编码过滤。用于团队工作量统计。 */
    @Query("SELECT u FROM User u WHERE u.departmentCode IN :deptCodes OR u.departmentCode IS NULL")
    List<User> findAllByDepartmentCodeIn(@Param("deptCodes") Collection<String> deptCodes);

    /** 查找需要（重新）回填 full_name_pinyin 的用户：null 行 + 旧格式行（不含空格=未升级首字母缩写）。 */
    @Query("SELECT u FROM User u WHERE u.fullName IS NOT NULL AND u.fullName <> '' "
        + "AND (u.fullNamePinyin IS NULL "
        + "     OR (u.fullNamePinyin NOT LIKE '% %' AND u.fullNamePinyin <> u.fullName))")
    List<User> findByFullNamePinyinNullAndFullNameNotNull();

    List<User> findByIdIn(Collection<Long> ids);

    /** 根据 RoleProfile.code 查找所有启用用户（用于按角色发送通知）。 */
    @Query("SELECT u FROM User u JOIN u.roleProfile rp WHERE rp.code IN :codes AND u.enabled = TRUE")
    List<User> findEnabledByRoleProfileCodes(@Param("codes") Collection<String> codes);

    /** 查找指定用户名的启用账号（系统操作者兜底）。 */
    Optional<User> findFirstByUsernameAndEnabledTrue(String username);

    /** 查找指定 Role 的最早启用账号（无指定 admin 用户时回退）。 */
    Optional<User> findFirstByRoleAndEnabledTrueOrderByIdAsc(User.Role role);

    /** 查找存量的拼音回填用户 — V1097 已删除 full_name_pinyin 列，此方法不再使用。 */
    // List<User> findEnabledWithNullPinyin();

    @Query(value = "SELECT * FROM users u WHERE u.full_name IS NOT NULL AND u.full_name <> '' "
        + "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(u.employee_number) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(u.full_name_pinyin) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(u.employee_number_pinyin) LIKE LOWER(CONCAT('%', :q, '%'))) "
        // 相关性优先排序：精确匹配 > 姓名前缀 > 拼音前缀 > 工号前缀；
        // enabled 仅作同等相关时的 tiebreaker（组织同步保守地把大量真实员工置为 enabled=false，
        // 不能让 enabled 抢占搜索相关性，否则会出现"搜郑蓉蓉却排在固定人员中间"）。
        + "ORDER BY "
        + "  (CASE WHEN LOWER(u.full_name) = LOWER(:q) THEN 0 ELSE 1 END), "
        + "  (CASE WHEN LOWER(u.full_name) LIKE LOWER(CONCAT(:q, '%')) THEN 0 ELSE 1 END), "
        + "  (CASE WHEN LOWER(u.full_name_pinyin) LIKE LOWER(CONCAT(:q, '%')) THEN 0 ELSE 1 END), "
        + "  (CASE WHEN LOWER(u.employee_number) LIKE LOWER(CONCAT(:q, '%')) "
        + "         OR LOWER(u.username) LIKE LOWER(CONCAT(:q, '%')) THEN 0 ELSE 1 END), "
        + "  u.enabled DESC, "
        + "  u.full_name "
        + "LIMIT :lim", nativeQuery = true)
    List<User> searchActiveUsers(@Param("q") String query, @Param("lim") int limit);
}
