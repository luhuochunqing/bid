// Input: 提交/更新立项的 HTTP 请求体
// Output: 通过 Bean Validation 与 InitiationFieldPolicy 共同校验
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiationDto {
    private String ownerUnit;
    private Integer expectedBidders;
    private Integer contractPeriodMonths;
    private InitiationFieldPolicy.ProjectType projectType;
    private InitiationFieldPolicy.CustomerType customerType;
    /** @deprecated superseded by customerRevenue (评估表客户营收字段) */
    @Deprecated
    private BigDecimal annualRevenue;
    /** 年度电商采购额(万)。蓝图 §3.3.1.1 新增。 */
    private BigDecimal annualEcommerceAmount;
    @JsonDeserialize(using = LenientLocalDateTimeDeserializer.class)
    private LocalDateTime bidOpenTime;
    private Long ownerUserId;
    private String departmentSnapshot;
    private BigDecimal depositAmount;
    private String depositPaymentMethod;
    /** 保证金缴纳截止日期（用于自动创建缴纳保证金任务的 dueDate；可空）。 */
    @JsonDeserialize(using = LenientLocalDateTimeDeserializer.class)
    private LocalDateTime depositDueDate;
    /** 是否需要保证金(YES/NO)。蓝图 §3.3.1.1 新增。 */
    private String needDeposit;
    private String competitors;
    /** 招标文件不利项。蓝图 §3.3.1.1 新增。 */
    private String tenderAdverseItems;
    /** 风险预判（举例说明）。蓝图 §3.3.1.1 新增。 */
    private String riskAssessment;
    /** 针对风险的兜底方案。蓝图 §3.3.1.1 新增。 */
    private String riskMitigationPlan;
    /** 项目经理是否了解评标全流程(YES/NO)。蓝图 §3.3.1.1 新增。 */
    private String pmUnderstandsProcess;
    /** 需要的支持及其他关键信息备注。蓝图 §3.3.1.1 新增。 */
    private String supportNeeded;
    /** 项目计划GAP说明。蓝图 §3.3.1.1 新增。 */
    private String projectPlanGap;
    /** 客户等级。V133 新增。 */
    private String customerGrade;
    /** 投标状态。V133 新增。 */
    private String bidStatus;
    /** 投标负责人姓名。V133 新增。 */
    private String biddingLeaderName;
    /** 投标平台。V133 新增。 */
    private String biddingPlatform;
    /** 中标状态。V133 新增。 */
    private String bidResultStatus;
    /** 项目负责人姓名。V133 新增。 */
    private String projectLeaderName;
    /** 负责人部门。V133 新增。 */
    private String leaderDepartment;
    /** 总部所在地。蓝图 §3.3.1.1 新增。 */
    private String headquartersLocation;
    /** 客户信息表格行列表（15列 x 14行）。V133 新增。 */
    private List<CustomerInfoRow> customerInfoRows;
    /** 招标文件附件 id。V133 新增。 */
    private Long tenderDocumentId;
    /** AI风险评估说明。蓝图 §3.3.1.1 新增。 */
    private String aiRiskAssessmentNotes;
}
