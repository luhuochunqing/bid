package com.xiyu.bid.resources.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;

/**
 * CO-451: 保管员工号查询器.
 * 从 User 表获取保管员的 employeeNumber，用于 CA 证书 DTO 展示"姓名（工号）"格式。
 */
@Component
public class CustodianEmployeeNumberResolver {

    private final UserRepository userRepository;

    public CustodianEmployeeNumberResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 单个保管员的工号查询.
     * 用于 create/update/getById/revealPassword 等单条记录场景.
     */
    public String fetchEmployeeNumber(Long custodianId) {
        if (custodianId == null) return null;
        return userRepository.findById(custodianId)
                .map(User::getEmployeeNumber)
                .orElse(null);
    }

    /**
     * 批量保管员的工号查询.
     * 用于 list 等批量记录场景，避免 N+1 查询问题.
     */
    public Map<Long, String> batchFetchEmployeeNumbers(List<Long> custodianIds) {
        if (custodianIds == null || custodianIds.isEmpty()) return Map.of();
        List<Long> distinctIds = custodianIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) return Map.of();
        List<User> users = userRepository.findAllById(distinctIds);
        return users.stream()
                .filter(u -> u.getEmployeeNumber() != null)
                .collect(Collectors.toMap(User::getId, User::getEmployeeNumber));
    }
}
