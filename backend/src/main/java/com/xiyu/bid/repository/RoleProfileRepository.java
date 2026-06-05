package com.xiyu.bid.repository;

import com.xiyu.bid.entity.RoleProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleProfileRepository extends JpaRepository<RoleProfile, Long> {

    Optional<RoleProfile> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
