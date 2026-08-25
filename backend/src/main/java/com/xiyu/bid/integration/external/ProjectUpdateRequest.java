// Input: CRM 项目修改请求体（接口规范 v3.12 §3.6）
// Output: 请求反序列化载体（所有字段可选，部分更新语义）
// Pos: integration/external/ - 对外集成接口契约
// 维护声明: 字段命名与 TenderUpdateRequest.EvaluationUpdate 评估段保持一致，取值口径对齐接口文档 §3.3。
package com.xiyu.bid.integration.external;

import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 接口六：项目修改（v3.12 新增）请求体。
 *
 * <p>按 (sourceSystem, sourceId) 定位标讯再定位其关联项目；仅写入项目数据，
 * 不修改标讯本身的任何字段（sourceSystem/sourceId 仅作定位依据）。
 *
 * <p>更新语义：非 null 字段覆盖现值，null 或未传字段保留原值；
 * {@code evaluationCustomerInfos} 传入时全量替换整个客户信息表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUpdateRequest {

    /** 项目 ID，可选备用定位方式；传入时优先按 projectId 定位。 */
    private Long projectId;

    /** 项目名称（项目公共信息），传入时覆盖现值。 */
    private String projectName;

    /**
     * 投标信息（项目立项 tab），字段命名与标讯修改接口 §3.3 evaluationBasic 一致。
     * 仅接收其中与项目立项对应的 7 个字段：mroOfficeFlowAmount、customerRevenue、
     * riskAssessment、contingencyPlan、processKnowledge、supportNotes、projectPlanGapFiles。
     * <p>projectPlanGapFiles 传空数组表示清空，不传（null）表示保留（CO-262 P1-3 语义）。
     */
    private EvaluationBasicDTO evaluationBasic;

    /**
     * 客户信息表行数组（15 列，项目立项 tab），字段命名与标讯修改接口 §3.3
     * evaluationCustomerInfos 一致：每行 15 个大写字段（NAME/CONTACT/TEL/POSITION/...），
     * 行顺序与前端表格展示行对应，传入时全量替换。
     */
    private List<Map<String, Object>> evaluationCustomerInfos;
}
