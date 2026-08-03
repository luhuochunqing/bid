package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.AttachmentTypeGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.ContractGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.CustomerTypeGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.GroupCompanyGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业绩合订本组织策略 — 四级层级分组 + 央企共享去重（纯核心函数）。
 *
 * <p>需求规格：
 * <pre>
 * H1 客户类型（央企 / 地方国企 / 民企 / 政府机关/事业单位 / 港澳台及外企）
 *  └─ H2 集团名称
 *      ├─ H3 合同名称（聚合展示同一集团下相同合同）
 *      │    ├─ H3 合同协议 / 其他附件
 *      │    └─ H4 中标通知书
 *      └─ H3 共享附件（央企专用，集团级别去重）
 *           ├─ H3 关系证明（按签约抬头去重，每个签约抬头一次）
 *           ├─ H3 品类页（按集团去重，每个集团一次）
 *           ├─ H3 商城截图（按集团去重，每个集团一次）
 *           └─ H3 央企名录（按集团去重，每个集团一次）
 * </pre>
 *
 * <p>央企共享去重（需求 §3，参考用户桌面参考文件）：
 * <ul>
 *   <li>关系证明：按 (signingEntity, fileType) 去重 — 每个签约抬头展示一次</li>
 *   <li>央企名录、品类页、商城截图：按 (fileType) 去重 — 每个集团展示一次</li>
 *   <li>去重在集团级别生效，跨合同去重，非合同内部去重</li>
 *   <li>仅对央企（CENTRAL_SOE）客户类型生效</li>
 * </ul>
 *
 * <p>纯函数设计：无副作用，不依赖 Spring，可独立单测。
 * 分组结果数据结构见 {@link PerformanceBundleGroupTypes}。
 */
public final class PerformanceWordBundleOrganizationPolicy {

    private PerformanceWordBundleOrganizationPolicy() {
        // 工具类，禁止实例化
    }

    /**
     * 将业绩记录按 H1 客户类型 → H2 集团 → H3 合同/附件类型 四级分组。
     *
     * @param records         业绩记录列表
     * @param attachmentTypes 要导出的附件类型集合；null 或空 = 全部
     * @return 四级嵌套分组结果，保持插入顺序（LinkedHashMap）
     */
    public static List<CustomerTypeGroup> groupByHierarchy(
            List<PerformanceDTO> records,
            Set<String> attachmentTypes) {

        if (records == null || records.isEmpty()) {
            return List.of();
        }

        // 第一级：按客户类型分组，使用枚举 ordinal 排序保证稳定
        Map<CustomerType, List<PerformanceDTO>> byCustomerType = new LinkedHashMap<>();
        for (CustomerType ct : CustomerType.values()) {
            byCustomerType.put(ct, new ArrayList<>());
        }
        for (PerformanceDTO r : records) {
            if (r.customerType() == null) continue;
            byCustomerType.get(r.customerType()).add(r);
        }

        List<CustomerTypeGroup> result = new ArrayList<>();
        for (Map.Entry<CustomerType, List<PerformanceDTO>> entry : byCustomerType.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CustomerTypeGroup group = new CustomerTypeGroup(
                    entry.getKey(),
                    groupByGroupCompany(entry.getValue(), attachmentTypes, entry.getKey())
            );
            result.add(group);
        }
        return result;
    }

    /**
     * 第二级：按集团名称分组。
     * 集团为空的记录归入"未分组"。
     * 央企客户类型在此级别提取共享附件并跨合同去重。
     */
    private static List<GroupCompanyGroup> groupByGroupCompany(
            List<PerformanceDTO> records,
            Set<String> attachmentTypes,
            CustomerType customerType) {

        Map<String, List<PerformanceDTO>> byGroup = new LinkedHashMap<>();
        for (PerformanceDTO r : records) {
            String groupCompany = normalizeGroup(r.groupCompany());
            byGroup.computeIfAbsent(groupCompany, k -> new ArrayList<>()).add(r);
        }

        List<GroupCompanyGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<PerformanceDTO>> entry : byGroup.entrySet()) {
            String groupCompany = entry.getKey();
            List<PerformanceDTO> groupRecords = entry.getValue();

            List<ContractGroup> contracts =
                    groupByContract(groupRecords, attachmentTypes, customerType);

            // 央企：在集团级别收集共享附件并去重
            List<AttachmentTypeGroup> sharedAttachments = List.of();
            if (customerType == CustomerType.CENTRAL_SOE) {
                sharedAttachments = collectSharedAttachments(groupRecords, attachmentTypes);
            }

            result.add(new GroupCompanyGroup(groupCompany, contracts, sharedAttachments));
        }
        return result;
    }

    /**
     * 第三级：按合同名称聚合。
     * 同一集团下相同合同名称的业绩记录合并为一个合同分组。
     * 央企客户类型在合同级别跳过共享附件类型（由集团级别处理）。
     */
    private static List<ContractGroup> groupByContract(
            List<PerformanceDTO> records,
            Set<String> attachmentTypes,
            CustomerType customerType) {

        Map<String, List<PerformanceDTO>> byContract = new LinkedHashMap<>();
        for (PerformanceDTO r : records) {
            String contractName = normalizeContract(r.contractName());
            byContract.computeIfAbsent(contractName, k -> new ArrayList<>()).add(r);
        }

        List<ContractGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<PerformanceDTO>> entry : byContract.entrySet()) {
            ContractGroup group = new ContractGroup(
                    entry.getKey(),
                    collectAttachments(entry.getValue(), attachmentTypes, customerType)
            );
            result.add(group);
        }
        return result;
    }

    /**
     * 第四级：收集合同级别附件并按类型分组。
     * 央企客户类型跳过共享附件类型（SOE_DIRECTORY/RELATIONSHIP_PROOF/CATEGORY_PAGE/MALL_SCREENSHOT），
     * 这些由 {@link #collectSharedAttachments} 在集团级别处理。
     */
    private static List<AttachmentTypeGroup> collectAttachments(
            List<PerformanceDTO> records,
            Set<String> attachmentTypes,
            CustomerType customerType) {

        Map<String, List<PerformanceDTO.AttachmentDTO>> byType = new LinkedHashMap<>();
        for (PerformanceDTO r : records) {
            List<PerformanceDTO.AttachmentDTO> attachments = r.attachments();
            if (attachments == null) continue;

            for (PerformanceDTO.AttachmentDTO att : attachments) {
                String fileType = att.fileType();
                if (fileType == null) fileType = PerformanceAttachmentTypeLabels.TYPE_OTHER;

                if (!matchesAttachmentFilter(attachmentTypes, fileType)) continue;

                // 央企：共享附件在集团级别处理，合同级别跳过
                if (customerType == CustomerType.CENTRAL_SOE
                        && PerformanceAttachmentTypeLabels.isSoeShareable(fileType)) {
                    continue;
                }

                byType.computeIfAbsent(fileType, k -> new ArrayList<>()).add(att);
            }
        }

        return sortAttachmentTypes(byType);
    }

    /**
     * 央企共享附件收集与去重（集团级别）。
     *
     * <p>去重规则（参考用户桌面参考文件）：
     * <ul>
     *   <li>关系证明（RELATIONSHIP_PROOF）：按 (signingEntity, fileType) 去重</li>
     *   <li>央企名录（SOE_DIRECTORY）/ 品类页（CATEGORY_PAGE）/ 商城截图（MALL_SCREENSHOT）：按 (fileType) 去重</li>
     * </ul>
     */
    private static List<AttachmentTypeGroup> collectSharedAttachments(
            List<PerformanceDTO> records,
            Set<String> attachmentTypes) {

        Set<String> seenBySigningEntity = new LinkedHashSet<>();
        Set<String> seenByGroup = new LinkedHashSet<>();
        Map<String, List<PerformanceDTO.AttachmentDTO>> byType = new LinkedHashMap<>();

        for (PerformanceDTO r : records) {
            String signingEntity = normalizeSigningEntity(r.signingEntity());
            List<PerformanceDTO.AttachmentDTO> attachments = r.attachments();
            if (attachments == null) continue;

            for (PerformanceDTO.AttachmentDTO att : attachments) {
                String fileType = att.fileType();
                if (fileType == null) fileType = PerformanceAttachmentTypeLabels.TYPE_OTHER;

                if (!matchesAttachmentFilter(attachmentTypes, fileType)) continue;
                if (!PerformanceAttachmentTypeLabels.isSoeShareable(fileType)) continue;

                if (isDuplicate(fileType, signingEntity, seenBySigningEntity, seenByGroup)) continue;

                byType.computeIfAbsent(fileType, k -> new ArrayList<>()).add(att);
            }
        }

        return sortAttachmentTypes(byType);
    }

    /**
     * 判断附件类型是否通过筛选条件。
     * null 或空集合 = 全部通过。
     */
    private static boolean matchesAttachmentFilter(Set<String> attachmentTypes, String fileType) {
        if (attachmentTypes == null || attachmentTypes.isEmpty()) return true;
        return attachmentTypes.contains(fileType);
    }

    /**
     * 央企共享附件去重判定。
     * <ul>
     *   <li>关系证明：按 (signingEntity, fileType) 去重</li>
     *   <li>其他共享类型：按 (fileType) 去重</li>
     * </ul>
     *
     * @return true 表示已出现过应跳过，false 表示首次出现应保留
     */
    private static boolean isDuplicate(String fileType, String signingEntity,
                                        Set<String> seenBySigningEntity, Set<String> seenByGroup) {
        if (PerformanceAttachmentTypeLabels.TYPE_RELATIONSHIP_PROOF.equals(fileType)) {
            String key = signingEntity + "|" + fileType;
            return !seenBySigningEntity.add(key);
        }
        return !seenByGroup.add(fileType);
    }

    /**
     * 按展示顺序排序附件类型分组。
     */
    private static List<AttachmentTypeGroup> sortAttachmentTypes(
            Map<String, List<PerformanceDTO.AttachmentDTO>> byType) {
        List<AttachmentTypeGroup> result = new ArrayList<>();
        for (String fileType : PerformanceAttachmentTypeLabels.DISPLAY_ORDER) {
            List<PerformanceDTO.AttachmentDTO> atts = byType.get(fileType);
            if (atts == null || atts.isEmpty()) continue;
            result.add(new AttachmentTypeGroup(
                    fileType,
                    PerformanceAttachmentTypeLabels.labelOf(fileType),
                    atts
            ));
        }
        return result;
    }

    private static String normalizeGroup(String s) {
        return (s == null || s.isBlank()) ? "未分组" : s.trim();
    }

    private static String normalizeContract(String s) {
        return (s == null || s.isBlank()) ? "未命名合同" : s.trim();
    }

    private static String normalizeSigningEntity(String s) {
        return (s == null || s.isBlank()) ? "" : s.trim();
    }
}
