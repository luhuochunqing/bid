// Input: PlatformAccountDTO list, UserRepository
// Output: PlatformAccountDTO with contactPersonLabel populated
// Pos: Service/展示层派生
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.PlatformAccountDTO;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CO-390: 把 PlatformAccountDTO.contactPerson (userId) 解析为
 * "姓名（工号）" 格式的展示标签 (contactPersonLabel)。
 * 单独成类以避免 PlatformAccountService 越过 300 行预算，
 * 并遵守单一职责：service 只编排，enricher 只做展示派生。
 */
@Component
@RequiredArgsConstructor
public class PlatformAccountContactLabelEnricher {

    private final UserRepository userRepository;

    /** 批量填充 contactPersonLabel，单次查询避免 N+1。 */
    public List<PlatformAccountDTO> enrich(List<PlatformAccountDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return dtos;
        Set<Long> userIds = dtos.stream()
            .map(PlatformAccountDTO::getContactPerson)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (userIds.isEmpty()) return dtos;
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (PlatformAccountDTO dto : dtos) {
            dto.setContactPersonLabel(resolveLabel(dto.getContactPerson(), userMap));
        }
        return dtos;
    }

    /** 单条填充 contactPersonLabel。 */
    public PlatformAccountDTO enrich(PlatformAccountDTO dto) {
        if (dto == null || dto.getContactPerson() == null) return dto;
        Map<Long, User> userMap = userRepository.findAllById(Collections.singleton(dto.getContactPerson()))
            .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        dto.setContactPersonLabel(resolveLabel(dto.getContactPerson(), userMap));
        return dto;
    }

    private String resolveLabel(Long userId, Map<Long, User> userMap) {
        if (userId == null) return null;
        User user = userMap.get(userId);
        if (user == null) return null;
        String name = user.getFullName();
        String empNo = user.getDisplayEmployeeNumber();
        if (name == null || name.isBlank()) return String.valueOf(userId);
        if (empNo == null || empNo.isBlank()) return name;
        return name + "（" + empNo + "）";
    }
}
