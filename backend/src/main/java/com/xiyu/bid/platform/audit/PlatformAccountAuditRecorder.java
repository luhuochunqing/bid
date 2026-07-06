// Input: 更新前后的 PlatformAccount、当前操作人、待审批申请数
// Output: 字段级 diff 审计日志（写入 IAuditLogService）
// Pos: Audit/审计协作层（独立于 service 包，允许注入 IAuditLogService）
// 维护声明: 仅维护字段 diff 计算与审计写入；业务规则留在 PlatformAccountService.
package com.xiyu.bid.platform.audit;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CO-522: 记录平台账号编辑操作的**字段级 diff** 审计日志。
 * <p>对齐项目里 {@code @Auditable(UPDATE)} 自动记录的概览日志（无 diff），
 * 本类补一条带 oldValue/newValue 的精细日志，满足"变更字段 + 变更前后值"需求。
 * <p>独立成类以避免 {@link PlatformAccountService} 越过 300 行预算，
 * 并遵守单一职责：service 只编排，recorder 只做 diff + 审计写入。
 * <p>安全：密码字段变更时，oldValue/newValue 统一写成 "已更新"，绝不写明文。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountAuditRecorder {

    private static final String ENTITY_TYPE = "PlatformAccount";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_TRANSFER_CONTACT = "TRANSFER_CONTACT";
    private static final String MASKED_PASSWORD = "已更新";

    private final IAuditLogService auditLogService;

    /**
     * 记录编辑操作的字段级 diff。
     *
     * @param oldAccount    更新前的账号快照
     * @param newAccount    更新后的账号（已 applyUpdateFields，尚未 save 也可）
     * @param operator       操作人
     * @param pendingApprovalCount 转派时的待审批借用申请数（仅联系人也变更时才 > 0）
     */
    public void recordUpdate(PlatformAccount oldAccount, PlatformAccount newAccount,
                             User operator, int pendingApprovalCount) {
        if (oldAccount == null || newAccount == null || operator == null) {
            log.warn("recordUpdate called with null argument, skipping audit");
            return;
        }
        String accountId = String.valueOf(newAccount.getId());
        boolean contactChanged = !Objects.equals(oldAccount.getContactPerson(), newAccount.getContactPerson());
        String diff = buildDiff(oldAccount, newAccount);

        // 联系人变更 → 单独一条 TRANSFER_CONTACT 日志，含转派待审批数
        if (contactChanged) {
            String transferDetail = String.format(
                    "更换绑定联系人：%s → %s；转派的待审批申请数：%d",
                    formatContact(oldAccount.getContactPerson()),
                    formatContact(newAccount.getContactPerson()),
                    pendingApprovalCount);
            auditLogService.log(AuditLogService.AuditLogEntry.builder()
                    .userId(String.valueOf(operator.getId()))
                    .username(operator.getUsername())
                    .action(ACTION_TRANSFER_CONTACT)
                    .entityType(ENTITY_TYPE)
                    .entityId(accountId)
                    .description(transferDetail)
                    .oldValue(formatContact(oldAccount.getContactPerson()))
                    .newValue(formatContact(newAccount.getContactPerson()))
                    .success(true)
                    .build());
        }

        // 通用 UPDATE diff 日志（包含所有变更字段，密码 mask）
        auditLogService.log(AuditLogService.AuditLogEntry.builder()
                .userId(String.valueOf(operator.getId()))
                .username(operator.getUsername())
                .action(ACTION_UPDATE)
                .entityType(ENTITY_TYPE)
                .entityId(accountId)
                .description(diff.isEmpty() ? "编辑平台账号" : diff)
                .oldValue(oldAccount.getAccountName())  // 概览字段，详细 diff 在 description
                .newValue(newAccount.getAccountName())
                .success(true)
                .build());
    }

    /**
     * 构建人类可读的字段 diff 文本（分号分隔）。
     * 密码字段：只写"密码：已更新"，不写明文。
     */
    private String buildDiff(PlatformAccount oldAccount, PlatformAccount newAccount) {
        List<String> changes = new ArrayList<>();
        appendChange(changes, "平台名称", oldAccount.getAccountName(), newAccount.getAccountName());
        appendChange(changes, "平台账号", oldAccount.getUsername(), newAccount.getUsername());
        appendChange(changes, "平台类型",
                oldAccount.getPlatformType() == null ? null : oldAccount.getPlatformType().name(),
                newAccount.getPlatformType() == null ? null : newAccount.getPlatformType().name());
        appendChange(changes, "网址", oldAccount.getUrl(), newAccount.getUrl());
        appendChange(changes, "绑定联系人",
                formatContact(oldAccount.getContactPerson()),
                formatContact(newAccount.getContactPerson()));
        appendChange(changes, "注册人", oldAccount.getRegistrant(), newAccount.getRegistrant());
        appendChange(changes, "注册手机", oldAccount.getRegisterPhone(), newAccount.getRegisterPhone());
        appendChange(changes, "注册邮箱", oldAccount.getRegisterEmail(), newAccount.getRegisterEmail());
        appendChange(changes, "是否有CA",
                oldAccount.getHasCa() == null ? null : oldAccount.getHasCa().toString(),
                newAccount.getHasCa() == null ? null : newAccount.getHasCa().toString());
        appendChange(changes, "备注", oldAccount.getRemarks(), newAccount.getRemarks());
        // 密码字段：mask
        if (!Objects.equals(oldAccount.getPassword(), newAccount.getPassword())) {
            changes.add("密码：" + MASKED_PASSWORD);
        }
        return String.join("；", changes);
    }

    private void appendChange(List<String> changes, String label, String oldVal, String newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            changes.add(label + "：" + (oldVal == null ? "空" : oldVal) + " → " + (newVal == null ? "空" : newVal));
        }
    }

    private String formatContact(Long contactPersonId) {
        return contactPersonId == null ? "无" : "用户(" + contactPersonId + ")";
    }

    /**
     * CO-522: 深拷贝账号关键字段，作为更新前快照供 diff 计算。
     * 不拷贝集合/关联，只拷贝审计关心的标量字段。
     */
    public PlatformAccount snapshot(PlatformAccount src) {
        if (src == null) return null;
        return PlatformAccount.builder()
            .id(src.getId())
            .username(src.getUsername())
            .password(src.getPassword())
            .accountName(src.getAccountName())
            .contactPerson(src.getContactPerson())
            .platformType(src.getPlatformType())
            .url(src.getUrl())
            .hasCa(src.getHasCa())
            .remarks(src.getRemarks())
            .registrant(src.getRegistrant())
            .registerPhone(src.getRegisterPhone())
            .registerEmail(src.getRegisterEmail())
            .build();
    }

    /**
     * CO-522: 若 newContactPerson 与当前不同（且非 null），返回该账号待审批借用申请数；否则返回 0。
     * 抽到 recorder 让 service 不必持有 borrowApplicationRepository 查询细节。
     */
    public int resolvePendingApprovalCount(PlatformAccount account, Long newContactPerson,
                                           AccountBorrowApplicationRepository repo) {
        if (newContactPerson == null
                || Objects.equals(newContactPerson, account.getContactPerson())) {
            return 0;
        }
        return repo.findByAccountIdAndStatus(account.getId(), BorrowStatus.PENDING_APPROVAL).size();
    }
}
