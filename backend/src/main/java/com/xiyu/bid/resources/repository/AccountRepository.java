package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Page<Account> findByType(Account.AccountType type, Pageable pageable);

    Page<Account> findByIndustry(String industry, Pageable pageable);

    Page<Account> findByRegion(String region, Pageable pageable);

    Page<Account> findByCreditLevel(Account.CreditLevel creditLevel, Pageable pageable);

    Page<Account> searchByNameContainingIgnoreCase(@Param("keyword") String keyword, Pageable pageable);

    Optional<Account> findByName(String name);

    long countByType(Account.AccountType type);
}
