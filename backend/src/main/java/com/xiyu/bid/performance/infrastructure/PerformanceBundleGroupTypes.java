package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;

import java.util.List;

/**
 * 业绩合订本四级分组结果数据结构。
 *
 * <p>从 {@link PerformanceWordBundleOrganizationPolicy} 拆分而来，仅包含纯数据 record，
 * 与分组策略逻辑分离，便于独立引用与测试。
 *
 * <pre>
 * CustomerTypeGroup (H1 客户类型)
 *   └─ GroupCompanyGroup (H2 集团)
 *       ├─ ContractGroup (H3 合同)
 *       │   └─ AttachmentTypeGroup (H3 附件分类)
 *       └─ AttachmentTypeGroup (H3 共享附件，央企专用)
 * </pre>
 */
public final class PerformanceBundleGroupTypes {

    private PerformanceBundleGroupTypes() {
        // 纯数据结构集合，禁止实例化
    }

    /** H1: 客户类型分组（央企 / 地方国企 / 民企 / 政府机关/事业单位/高校 / 港澳台及外企）。 */
    public record CustomerTypeGroup(
            CustomerType customerType,
            List<GroupCompanyGroup> groups
    ) {}

    /** H2: 集团分组，含合同列表与（央企专用）共享附件列表。 */
    public record GroupCompanyGroup(
            String groupCompany,
            List<ContractGroup> contracts,
            List<AttachmentTypeGroup> sharedAttachments
    ) {}

    /** H3: 合同分组，含按类型分组的附件列表。 */
    public record ContractGroup(
            String contractName,
            List<AttachmentTypeGroup> attachmentTypes
    ) {}

    /** H3/H4: 附件类型分组（如合同协议、央企名录、中标通知书等）。 */
    public record AttachmentTypeGroup(
            String fileType,
            String label,
            List<PerformanceDTO.AttachmentDTO> attachments
    ) {}
}
