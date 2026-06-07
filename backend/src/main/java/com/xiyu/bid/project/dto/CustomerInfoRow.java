// Input: 客户信息表格单行（15 列）
// Output: 序列化到 customer_info_json 的单元
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户信息角色表格行（15 列 × 14 行）。
 * 对应蓝图 §3.3.1.1 立项表单「客户信息」区。
 * 每行代表一个角色/岗位的客户触达信息。
 * 整表以 JSON 数组形式存入 project_initiation_details.customer_info_json。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInfoRow {
    /** 角色（如：项目决策人/技术负责人/采购负责人等）。 */
    private String role;
    /** 姓名。 */
    private String name;
    /** 职位。 */
    private String position;
    /** 西域对接人。 */
    private String xiyuContact;
    /** 是否已触达(YES/NO)。 */
    private String reached;
    /** 触达方式。 */
    private String reachMethod;
    /** 偏好（我方/中立/对手）。 */
    private String preference;
    /** 偏好依据。 */
    private String preferenceBasis;
    /** 是否有高层会晤(YES/NO)。 */
    private String hasHighLevelMeeting;
    /** 是否引导投标(YES/NO)。 */
    private String guideBid;
    /** 是否能获取关键信息(YES/NO)。 */
    private String canGetKeyInfo;
    /** 是否能清除不利项(YES/NO)。 */
    private String canRemoveAdverse;
    /** 是否重点目标(YES/NO)。 */
    private String isKeyTarget;
    /** 是否能同步评估(YES/NO)。 */
    private String canSyncEval;
    /** 是否能确认中标(YES/NO)。 */
    private String canConfirmWin;
}
