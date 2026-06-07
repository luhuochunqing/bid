package com.xiyu.bid.apikey.infrastructure;

import com.xiyu.bid.apikey.entity.ApiKey;
import com.xiyu.bid.apikey.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByStatus(ApiKeyStatus status);

    boolean existsByKeyHash(String keyHash);
}
