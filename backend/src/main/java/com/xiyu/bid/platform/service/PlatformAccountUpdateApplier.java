// Input: PlatformAccount、PlatformAccountCreateRequest、密码加密器、Repository
// Output: 应用编辑字段到实体（含唯一性校验）
// Pos: Service/字段应用协作层
// 维护声明: 仅维护字段应用与唯一性校验；业务编排留在 PlatformAccountService.
package com.xiyu.bid.platform.service;

import com.xiyu.bid.platform.dto.PlatformAccountCreateRequest;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * CO-522: 把"编辑账号时的字段校验 + 应用"从 PlatformAccountService 拆出，
 * 避免 service 越过 300 行预算。单一职责：applier 只做字段应用，service 只编排。
 */
@Component
@RequiredArgsConstructor
public class PlatformAccountUpdateApplier {

    private final PlatformAccountRepository repository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /** 校验 accountName 在编辑场景下的唯一性。 */
    public void validateUniqueness(PlatformAccountCreateRequest request, PlatformAccount account) {
        if (request.getAccountName() != null && !request.getAccountName().trim().isEmpty()
                && !request.getAccountName().equals(account.getAccountName())
                && repository.findByAccountName(request.getAccountName()).isPresent()) {
            throw new IllegalArgumentException("Account name already exists: " + request.getAccountName());
        }
    }

    /** 应用 request 中非 null 的字段到 account（null 字段跳过，保留原值）。 */
    public void applyFields(PlatformAccount account, PlatformAccountCreateRequest request) {
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            account.setPassword(passwordEncryptionUtil.encrypt(request.getPassword()));
        }
        account.setUsername(Optional.ofNullable(request.getUsername()).orElse(account.getUsername()));
        account.setAccountName(Optional.ofNullable(request.getAccountName()).orElse(account.getAccountName()));
        account.setUrl(Optional.ofNullable(request.getUrl()).orElse(account.getUrl()));
        account.setContactPerson(request.getContactPerson() != null ? request.getContactPerson() : account.getContactPerson());
        account.setRegistrant(request.getRegistrant() != null ? request.getRegistrant() : account.getRegistrant());
        account.setRegisterPhone(request.getRegisterPhone() != null ? request.getRegisterPhone() : account.getRegisterPhone());
        account.setRegisterEmail(request.getRegisterEmail() != null ? request.getRegisterEmail() : account.getRegisterEmail());
        account.setHasCa(request.getHasCa() != null ? request.getHasCa() : account.getHasCa());
        account.setRemarks(request.getRemarks() != null ? request.getRemarks() : account.getRemarks());
    }
}
