package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.domain.PerformanceWordBundleOrganizationPolicy;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.AttachmentTypeGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.ContractGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.CustomerTypeGroup;
import com.xiyu.bid.performance.infrastructure.PerformanceBundleGroupTypes.GroupCompanyGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业绩合订本组织策略单元测试。
 *
 * <p>覆盖需求规格：
 * <ul>
 *   <li>四级层级分组：H1 客户类型 → H2 集团 → H3 合同/附件类型 → H4 中标通知书</li>
 *   <li>央企共享附件去重：关系证明按签约抬头去重，央企名录/品类页/商城截图按集团去重</li>
 *   <li>非央企客户类型：共享附件不去重，每份合同独立展示</li>
 *   <li>合同级别跳过央企共享类型（由集团级别处理）</li>
 *   <li>附件类型展示顺序（DISPLAY_ORDER）</li>
 *   <li>空值与边界条件</li>
 * </ul>
 *
 * <p>纯函数测试，不依赖 Spring/IO。
 */
class PerformanceWordBundleOrganizationPolicyTest {

    // ========== 四级层级分组基础测试 ==========

    @Test
    void groupByHierarchy_emptyRecords_returnsEmptyList() {
        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(), Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void groupByHierarchy_nullRecords_returnsEmptyList() {
        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(null, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void groupByHierarchy_singleRecord_centralSoe_correctHierarchy() {
        PerformanceDTO record = record(
                CustomerType.CENTRAL_SOE, "国家电网", "北京电力合同",
                "国网北京", contractAgreement(), bidNotice());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        assertThat(result).hasSize(1);
        CustomerTypeGroup ctGroup = result.get(0);
        assertThat(ctGroup.customerType()).isEqualTo(CustomerType.CENTRAL_SOE);
        assertThat(ctGroup.groups()).hasSize(1);

        GroupCompanyGroup gcGroup = ctGroup.groups().get(0);
        assertThat(gcGroup.groupCompany()).isEqualTo("国家电网");
        assertThat(gcGroup.contracts()).hasSize(1);
        assertThat(gcGroup.sharedAttachments()).isEmpty(); // 共享附件在多合同时才去重，单合同无意义

        ContractGroup contract = gcGroup.contracts().get(0);
        assertThat(contract.contractName()).isEqualTo("北京电力合同");
        // 央企：合同级别跳过共享附件，只保留合同协议、中标通知书
        assertThat(contract.attachmentTypes()).hasSize(2);
        assertThat(contract.attachmentTypes()).extracting(AttachmentTypeGroup::fileType)
                .containsExactly(
                        PerformanceAttachmentTypeLabels.TYPE_CONTRACT_AGREEMENT,
                        PerformanceAttachmentTypeLabels.TYPE_BID_NOTICE);
    }

    @Test
    void groupByHierarchy_multipleCustomerTypes_groupedInOrder() {
        PerformanceDTO soeRecord = record(
                CustomerType.CENTRAL_SOE, "国家电网", "合同A", "抬头A",
                contractAgreement());
        PerformanceDTO privateRecord = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同B", "抬头B",
                contractAgreement());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(
                        List.of(privateRecord, soeRecord), Set.of());

        // 客户类型按枚举 ordinal 顺序（政府机关 → 央企 → 地方国企 → 民企 → 港澳台外企）
        assertThat(result).extracting(CustomerTypeGroup::customerType)
                .containsExactly(
                        CustomerType.CENTRAL_SOE,
                        CustomerType.PRIVATE_ENTERPRISE);
    }

    @Test
    void groupByHierarchy_sameContractName_aggregatedInOneGroup() {
        PerformanceDTO r1 = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "同一合同", "抬头A",
                contractAgreement());
        PerformanceDTO r2 = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "同一合同", "抬头B",
                contractAgreement());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1, r2), Set.of());

        assertThat(result).hasSize(1);
        GroupCompanyGroup gc = result.get(0).groups().get(0);
        // 同名合同应聚合为一个 ContractGroup，附件合并展示
        assertThat(gc.contracts()).hasSize(1);
        assertThat(gc.contracts().get(0).attachmentTypes()).hasSize(1);
        assertThat(gc.contracts().get(0).attachmentTypes().get(0).attachments()).hasSize(2);
    }

    // ========== 央企共享附件去重测试 ==========

    @Test
    void centralSoe_sameSigningEntity_relationshipProof_dedupOnce() {
        // 央企：同集团同签约抬头多份合同，关系证明应只展示一次
        PerformanceDTO r1 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "北京合同1", "国网北京",
                contractAgreement(), relationshipProof("proof_v1.pdf"));
        PerformanceDTO r2 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "北京合同2", "国网北京",
                contractAgreement(), relationshipProof("proof_v2.pdf"));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1, r2), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        // 关系证明在集团级别去重：同签约抬头仅保留第一份
        List<AttachmentTypeGroup> shared = gc.sharedAttachments();
        AttachmentTypeGroup relProofGroup = findAttachmentByType(
                shared, PerformanceAttachmentTypeLabels.TYPE_RELATIONSHIP_PROOF);
        assertThat(relProofGroup.attachments()).hasSize(1);
        assertThat(relProofGroup.attachments().get(0).fileName()).isEqualTo("proof_v1.pdf");
    }

    @Test
    void centralSoe_differentSigningEntity_relationshipProof_keepsEachEntity() {
        // 央企：同集团不同签约抬头，关系证明应每个抬头各展示一次
        PerformanceDTO r1 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "北京合同", "国网北京",
                relationshipProof("proof_beijing.pdf"));
        PerformanceDTO r2 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "上海合同", "国网上海",
                relationshipProof("proof_shanghai.pdf"));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1, r2), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        AttachmentTypeGroup relProofGroup = findAttachmentByType(
                gc.sharedAttachments(), PerformanceAttachmentTypeLabels.TYPE_RELATIONSHIP_PROOF);
        assertThat(relProofGroup.attachments()).hasSize(2);
    }

    @Test
    void centralSoe_sameGroup_soeDirectory_dedupOnce() {
        // 央企：同集团多合同，央企名录应只展示一次（按集团去重）
        PerformanceDTO r1 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "北京合同", "国网北京",
                soeDirectory("directory_v1.pdf"));
        PerformanceDTO r2 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "上海合同", "国网上海",
                soeDirectory("directory_v2.pdf"));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1, r2), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        AttachmentTypeGroup directoryGroup = findAttachmentByType(
                gc.sharedAttachments(), PerformanceAttachmentTypeLabels.TYPE_SOE_DIRECTORY);
        assertThat(directoryGroup.attachments()).hasSize(1);
        assertThat(directoryGroup.attachments().get(0).fileName()).isEqualTo("directory_v1.pdf");
    }

    @Test
    void centralSoe_sameGroup_categoryPageAndMallScreenshot_dedupOnce() {
        // 央企：品类页和商城截图按集团去重
        PerformanceDTO r1 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "合同1", "国网北京",
                categoryPage("cat_v1.pdf"), mallScreenshot("mall_v1.png"));
        PerformanceDTO r2 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "合同2", "国网上海",
                categoryPage("cat_v2.pdf"), mallScreenshot("mall_v2.png"));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1, r2), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        AttachmentTypeGroup categoryGroup = findAttachmentByType(
                gc.sharedAttachments(), PerformanceAttachmentTypeLabels.TYPE_CATEGORY_PAGE);
        AttachmentTypeGroup mallGroup = findAttachmentByType(
                gc.sharedAttachments(), PerformanceAttachmentTypeLabels.TYPE_MALL_SCREENSHOT);
        assertThat(categoryGroup.attachments()).hasSize(1);
        assertThat(mallGroup.attachments()).hasSize(1);
    }

    @Test
    void centralSoe_sharedAttachments_skippedAtContractLevel() {
        // 央企：共享附件在合同级别应被跳过
        PerformanceDTO r1 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "合同1", "国网北京",
                contractAgreement(), relationshipProof("proof.pdf"), soeDirectory("dir.pdf"));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1), Set.of());

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        // 合同级别只剩 CONTRACT_AGREEMENT（共享类型已跳过到集团级别）
        assertThat(contract.attachmentTypes()).hasSize(1);
        assertThat(contract.attachmentTypes().get(0).fileType())
                .isEqualTo(PerformanceAttachmentTypeLabels.TYPE_CONTRACT_AGREEMENT);
    }

    @Test
    void centralSoe_differentGroups_sharedAttachmentsDedupPerGroup() {
        // 央企：不同集团的关系证明/央企名录各自独立去重
        PerformanceDTO r1 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "合同1", "国网北京",
                soeDirectory("dir_sgcc.pdf"));
        PerformanceDTO r2 = record(
                CustomerType.CENTRAL_SOE, "南方电网", "合同2", "南方电网深圳",
                soeDirectory("dir_csg.pdf"));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1, r2), Set.of());

        // 应有两个集团
        List<GroupCompanyGroup> groups = result.get(0).groups();
        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(GroupCompanyGroup::groupCompany)
                .containsExactly("国家电网", "南方电网");

        // 每个集团各自有一份央企名录
        AttachmentTypeGroup dir1 = findAttachmentByType(
                groups.get(0).sharedAttachments(), PerformanceAttachmentTypeLabels.TYPE_SOE_DIRECTORY);
        AttachmentTypeGroup dir2 = findAttachmentByType(
                groups.get(1).sharedAttachments(), PerformanceAttachmentTypeLabels.TYPE_SOE_DIRECTORY);
        assertThat(dir1.attachments().get(0).fileName()).isEqualTo("dir_sgcc.pdf");
        assertThat(dir2.attachments().get(0).fileName()).isEqualTo("dir_csg.pdf");
    }

    // ========== 非央企客户类型测试 ==========

    @Test
    void nonCentralSoe_sharedAttachments_keptAtContractLevel() {
        // 非央企：关系证明、央企名录等共享附件不去重，每份合同独立展示
        PerformanceDTO r1 = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同1", "阿里云",
                contractAgreement(), relationshipProof("proof1.pdf"), relationshipProof("proof2.pdf"));
        PerformanceDTO r2 = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同2", "淘宝",
                relationshipProof("proof3.pdf"));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1, r2), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        // 非央企不收集集团级别共享附件
        assertThat(gc.sharedAttachments()).isEmpty();
        // 合同1：含 2 份关系证明
        ContractGroup contract1 = gc.contracts().get(0);
        AttachmentTypeGroup relProof1 = findAttachmentByType(
                contract1.attachmentTypes(), PerformanceAttachmentTypeLabels.TYPE_RELATIONSHIP_PROOF);
        assertThat(relProof1.attachments()).hasSize(2);
        // 合同2：含 1 份关系证明
        ContractGroup contract2 = gc.contracts().get(1);
        AttachmentTypeGroup relProof2 = findAttachmentByType(
                contract2.attachmentTypes(), PerformanceAttachmentTypeLabels.TYPE_RELATIONSHIP_PROOF);
        assertThat(relProof2.attachments()).hasSize(1);
    }

    // ========== 附件类型筛选测试 ==========

    @Test
    void groupByHierarchy_attachmentTypesFilter_onlyKeepsMatchingTypes() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同", "阿里云",
                contractAgreement(), bidNotice(), otherAttachment());

        // 只导出 BID_NOTICE
        Set<String> filter = Set.of(PerformanceAttachmentTypeLabels.TYPE_BID_NOTICE);
        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), filter);

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.attachmentTypes()).hasSize(1);
        assertThat(contract.attachmentTypes().get(0).fileType())
                .isEqualTo(PerformanceAttachmentTypeLabels.TYPE_BID_NOTICE);
    }

    @Test
    void groupByHierarchy_emptyAttachmentTypesFilter_keepsAll() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同", "阿里云",
                contractAgreement(), bidNotice());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.attachmentTypes()).hasSize(2);
    }

    @Test
    void groupByHierarchy_nullAttachmentTypesFilter_keepsAll() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同", "阿里云",
                contractAgreement());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), null);

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.attachmentTypes()).hasSize(1);
    }

    // ========== 附件展示顺序测试 ==========

    @Test
    void attachmentTypes_displayOrder_followsDisplayOrderConstant() {
        // 故意以乱序附件构造，验证输出按 DISPLAY_ORDER 排序
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同", "阿里云",
                bidNotice(),          // 应排第 7
                otherAttachment(),    // 应排第 6
                contractAgreement()   // 应排第 1
        );

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.attachmentTypes()).extracting(AttachmentTypeGroup::fileType)
                .containsExactly(
                        PerformanceAttachmentTypeLabels.TYPE_CONTRACT_AGREEMENT,
                        PerformanceAttachmentTypeLabels.TYPE_OTHER,
                        PerformanceAttachmentTypeLabels.TYPE_BID_NOTICE);
    }

    @Test
    void centralSoe_sharedAttachments_displayOrderFollowsDisplayOrderConstant() {
        // 央企共享附件输出顺序应遵循 DISPLAY_ORDER
        PerformanceDTO r1 = record(
                CustomerType.CENTRAL_SOE, "国家电网", "合同1", "国网北京",
                mallScreenshot("mall.png"),     // 应排第 5
                soeDirectory("dir.pdf"),        // 应排第 2
                relationshipProof("proof.pdf"), // 应排第 3
                categoryPage("cat.pdf"));       // 应排第 4

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(r1), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        // DISPLAY_ORDER 中共享类型顺序：SOE_DIRECTORY, RELATIONSHIP_PROOF, CATEGORY_PAGE, MALL_SCREENSHOT
        assertThat(gc.sharedAttachments()).extracting(AttachmentTypeGroup::fileType)
                .containsExactly(
                        PerformanceAttachmentTypeLabels.TYPE_SOE_DIRECTORY,
                        PerformanceAttachmentTypeLabels.TYPE_RELATIONSHIP_PROOF,
                        PerformanceAttachmentTypeLabels.TYPE_CATEGORY_PAGE,
                        PerformanceAttachmentTypeLabels.TYPE_MALL_SCREENSHOT);
    }

    // ========== 边界条件 ==========

    @Test
    void groupByHierarchy_nullGroupCompany_groupedAsUnGrouped() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, null, "合同", "阿里云",
                contractAgreement());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        assertThat(gc.groupCompany()).isEqualTo("未分组");
    }

    @Test
    void groupByHierarchy_blankGroupCompany_groupedAsUnGrouped() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "  ", "合同", "阿里云",
                contractAgreement());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        GroupCompanyGroup gc = result.get(0).groups().get(0);
        assertThat(gc.groupCompany()).isEqualTo("未分组");
    }

    @Test
    void groupByHierarchy_nullContractName_groupedAsUnnamedContract() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", null, "阿里云",
                contractAgreement());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.contractName()).isEqualTo("未命名合同");
    }

    @Test
    void groupByHierarchy_recordWithNoAttachments_contractHasNoAttachmentTypes() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同", "阿里云");

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.attachmentTypes()).isEmpty();
    }

    @Test
    void groupByHierarchy_nullFileType_normalizedToOther() {
        PerformanceDTO record = record(
                CustomerType.PRIVATE_ENTERPRISE, "阿里", "合同", "阿里云",
                new PerformanceDTO.AttachmentDTO(1L, "file.pdf", "/path/file.pdf", null));

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.attachmentTypes()).hasSize(1);
        assertThat(contract.attachmentTypes().get(0).fileType())
                .isEqualTo(PerformanceAttachmentTypeLabels.TYPE_OTHER);
        assertThat(contract.attachmentTypes().get(0).label()).isEqualTo("其他附件");
    }

    @Test
    void groupByHierarchy_emptyAttachmentsList_handledGracefully() {
        PerformanceDTO record = new PerformanceDTO(
                1L, "合同", "阿里云", "阿里", CustomerType.PRIVATE_ENTERPRISE,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, false, null, List.of(),
                null, null);

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        ContractGroup contract = result.get(0).groups().get(0).contracts().get(0);
        assertThat(contract.attachmentTypes()).isEmpty();
    }

    @Test
    void groupByHierarchy_nullCustomerType_recordSkipped() {
        // customerType 为 null 的记录应被跳过
        PerformanceDTO record = record(
                null, "阿里", "合同", "阿里云", contractAgreement());

        List<CustomerTypeGroup> result =
                PerformanceWordBundleOrganizationPolicy.groupByHierarchy(List.of(record), Set.of());

        assertThat(result).isEmpty();
    }

    // ========== 测试辅助方法 ==========

    private static AttachmentTypeGroup findAttachmentByType(
            List<AttachmentTypeGroup> groups, String fileType) {
        return groups.stream()
                .filter(g -> g.fileType().equals(fileType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到附件类型: " + fileType));
    }

    private static PerformanceDTO record(
            CustomerType customerType, String groupCompany, String contractName,
            String signingEntity, PerformanceDTO.AttachmentDTO... attachments) {
        return new PerformanceDTO(
                1L, contractName, signingEntity, groupCompany, customerType,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, false, null,
                List.of(attachments), null, null);
    }

    private static PerformanceDTO.AttachmentDTO contractAgreement() {
        return attachment(PerformanceAttachmentTypeLabels.TYPE_CONTRACT_AGREEMENT, "contract.pdf");
    }

    private static PerformanceDTO.AttachmentDTO bidNotice() {
        return attachment(PerformanceAttachmentTypeLabels.TYPE_BID_NOTICE, "bid_notice.pdf");
    }

    private static PerformanceDTO.AttachmentDTO relationshipProof(String fileName) {
        return attachment(PerformanceAttachmentTypeLabels.TYPE_RELATIONSHIP_PROOF, fileName);
    }

    private static PerformanceDTO.AttachmentDTO soeDirectory(String fileName) {
        return attachment(PerformanceAttachmentTypeLabels.TYPE_SOE_DIRECTORY, fileName);
    }

    private static PerformanceDTO.AttachmentDTO categoryPage(String fileName) {
        return attachment(PerformanceAttachmentTypeLabels.TYPE_CATEGORY_PAGE, fileName);
    }

    private static PerformanceDTO.AttachmentDTO mallScreenshot(String fileName) {
        return attachment(PerformanceAttachmentTypeLabels.TYPE_MALL_SCREENSHOT, fileName);
    }

    private static PerformanceDTO.AttachmentDTO otherAttachment() {
        return attachment(PerformanceAttachmentTypeLabels.TYPE_OTHER, "other.pdf");
    }

    private static PerformanceDTO.AttachmentDTO attachment(String fileType, String fileName) {
        return new PerformanceDTO.AttachmentDTO(1L, fileName, "/path/" + fileName, fileType);
    }
}
