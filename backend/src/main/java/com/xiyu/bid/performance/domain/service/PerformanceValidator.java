package com.xiyu.bid.performance.domain.service;

import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 业绩验证核心策略类 (FP-Java Profile: Pure Core)
 * 业务规则、逻辑与校验，无任何框架及数据库依赖，可独立进行单元测试。
 * 函数式架构：方法必须返回值，不得抛出/捕获异常。
 */
public final class PerformanceValidator {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern TEL_PATTERN = Pattern.compile("^(0\\d{2,3}-?)?\\d{7,8}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+$");

    private PerformanceValidator() {}

    /**
     * 执行业务校验规则
     * @param record 业绩实体数据
     * @return Optional.empty() 表示验证通过，Optional.of("错误信息") 表示验证失败
     */
    public static Optional<String> validate(PerformanceRecord record) {
        if (record == null) {
            return Optional.of("业绩记录不能为空");
        }

        // 1. 合同名称必填
        if (record.contractName() == null || record.contractName().trim().isEmpty()) {
            return Optional.of("请输入合同名称");
        }

        // 2. 签约单位和集团公司必填
        if (record.signingEntity() == null || record.signingEntity().trim().isEmpty()
                || record.groupCompany() == null || record.groupCompany().trim().isEmpty()) {
            return Optional.of("请填写完整必填项");
        }

        // 3. 客户类型必选
        if (record.customerType() == null) {
            return Optional.of("请选择客户类型");
        }

        // 4. 项目类型 / 对接方式 / 客户级别必选
        if (record.projectType() == null || record.dockingMethod() == null || record.customerLevel() == null) {
            return Optional.of("请选择");
        }

        // 5. 日期大小关系校验
        LocalDate signingDate = record.signingDate();
        LocalDate expiryDate = record.expiryDate();
        if (signingDate == null || expiryDate == null) {
            return Optional.of("请选择关键日期");
        }
        if (!expiryDate.isAfter(signingDate)) {
            return Optional.of("截止日期必须晚于签约日期");
        }

        // 6. 总截止日期需晚于截止日期
        LocalDate totalExpiryDate = record.totalExpiryDate();
        if (totalExpiryDate != null && totalExpiryDate.isBefore(expiryDate)) {
            return Optional.of("总截止日期需晚于截止日期");
        }

        // 7. 联系人与地址必填
        if (record.contactPerson() == null || record.contactPerson().trim().isEmpty()
                || record.territory() == null || record.territory().trim().isEmpty()
                || record.customerAddress() == null || record.customerAddress().trim().isEmpty()) {
            return Optional.of("请填写完整必填项");
        }

        // 8. 客户联系方式格式校验
        String contactInfo = record.contactInfo();
        if (contactInfo == null || contactInfo.trim().isEmpty()) {
            return Optional.of("请填写完整必填项");
        }
        String trimmedContact = contactInfo.trim();
        boolean matchesPhone = PHONE_PATTERN.matcher(trimmedContact).matches();
        boolean matchesTel = TEL_PATTERN.matcher(trimmedContact).matches();
        boolean matchesEmail = EMAIL_PATTERN.matcher(trimmedContact).matches();
        if (!matchesPhone && !matchesTel && !matchesEmail) {
            return Optional.of("请输入有效的联系方式");
        }

        // 9. 核心必传附件校验：合同协议
        // 附件「存在」的判定：fileName 或 fileUrl 任一非空即可。
        //  - 页面上传：fileUrl 为完整 URL；
        //  - 批量导入：fileName 来自 Excel 模板，fileUrl 由附件包归档器后置回填（暂为空）。
        // 两者都属于「已声明附件」，校验应放行；只有 fileName 与 fileUrl 均空才视为缺失。
        if (!hasAttachment(record, "CONTRACT_AGREEMENT")) {
            return Optional.of("请上传合同协议");
        }

        // 10. 央企客户附件校验：央企名录截图 (SOE_DIRECTORY) 和 关系证明 (RELATIONSHIP_PROOF)
        if (record.customerType() == CustomerType.CENTRAL_SOE) {
            if (!hasAttachment(record, "SOE_DIRECTORY")) {
                return Optional.of("央企客户必须上传央企名录截图");
            }
            if (!hasAttachment(record, "RELATIONSHIP_PROOF")) {
                return Optional.of("央企客户必须上传关系证明");
            }
        }

        // 11. 中标通知书联动校验
        if (record.hasBidNotice() && !hasAttachment(record, "BID_NOTICE")) {
            return Optional.of("当中标通知书为是的时候，必传");
        }

        return Optional.empty();
    }

    /**
     * 判断指定类型的附件是否「已声明」。
     * fileName（批量导入来自 Excel 模板）或 fileUrl（页面上传的完整 URL）任一非空即视为存在；
     * 二者均空才视为缺失。
     */
    private static boolean hasAttachment(PerformanceRecord record, String fileType) {
        return record.attachments().stream().anyMatch(a ->
                fileType.equals(a.fileType())
                        && (isPresent(a.fileName()) || isPresent(a.fileUrl())));
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
