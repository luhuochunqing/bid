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

    Optional<User> findByEmail(String email);
    
    Optional<User> findByWecomUserId(String wecomUserId);

    Optional<User> findByPhone(String phone);

    Optional<User> findByExternalOrgSourceAppAndExternalOrgUserId(String sourceApp, String externalOrgUserId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByRoleProfile_CodeIgnoreCaseAndEnabledTrue(String roleCode);

    long countByRoleProfile_Id(Long roleProfileId);

    List<User> findByEnabledTrue();

    List<User> findByIdIn(Collection<Long> ids);

    /** 根据 RoleProfile.code 查找所有启用用户（用于按角色发送通知）。 */
    @Query("SELECT u FROM User u JOIN u.roleProfile rp WHERE rp.code IN :codes AND u.enabled = TRUE")
    List<User> findEnabledByRoleProfileCodes(@Param("codes") Collection<String> codes);

    @Query(value = "SELECT * FROM users u WHERE u.enabled = TRUE "
        + "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%', :q, '%')) "
        + "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))) "
        + "ORDER BY u.full_name LIMIT :lim", nativeQuery = true)
    List<User> searchActiveUsers(@Param("q") String query, @Param("lim") int limit);
}
