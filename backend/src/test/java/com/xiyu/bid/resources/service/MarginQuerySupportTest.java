package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarginQuerySupport.mapRow 行为测试（纯单元，无 Spring/DB 依赖）。
 *
 * <p>覆盖场景：项目负责人工号字段（projectLeaderEmployeeNumber）的列读取与 null 兜底。
 * 列索引 C_PROJ_LEAD_EMP_NO = 16（在 C_STATUS=15 之后追加，不破坏既有列顺序）。
 *
 * <p>注意：{@link MarginSqlDateCoercionContractTest} 已覆盖日期强转 / 状态标签等场景，
 * 本测试只覆盖 projectLeaderEmployeeNumber 字段的新增列读取契约。
 */
class MarginQuerySupportTest {

    /**
     * 构造 17 列 Object[]，仅 C_PROJ_LEAD_EMP_NO (idx=16) 由调用方传入。
     * 其他列填合法值（金额 1000，退回 0，服务费 0 → 不命中规则3，走规则1/2）。
     */
    private static Object[] rowWithProjLeadEmpNo(final Object empNo) {
        return new Object[]{
                1L,                                          // 0: C_FEE_ID
                100L,                                        // 1: C_PROJ_ID
                "测试项目",                                  // 2: C_PROJ_NAME
                "西域",                                      // 3: C_OWNER
                "张三",                                      // 4: C_PROJ_LEAD
                "李四",                                      // 5: C_BID_LEAD
                new BigDecimal("1000"),                     // 6: C_AMT
                null,                                        // 7: C_PAY_DATE
                "电汇",                                      // 8: C_PAY_METHOD
                "收款方",                                    // 9: C_PAYEE
                "账号",                                      // 10: C_PAYEE_ACCT
                null,                                        // 11: C_EXP_RETURN
                BigDecimal.ZERO,                             // 12: C_RET_AMT
                BigDecimal.ZERO,                             // 13: C_SVC_FEE
                null,                                        // 14: C_ACT_RETURN
                "PENDING",                                   // 15: C_STATUS
                empNo                                        // 16: C_PROJ_LEAD_EMP_NO
        };
    }

    @Test
    void mapRow_FillsProjectLeaderEmployeeNumber_WhenColumnPresent() {
        Object[] row = rowWithProjLeadEmpNo("05972");
        MarginDTO dto = MarginQuerySupport.mapRow(row);
        assertThat(dto.getProjectLeaderEmployeeNumber())
                .as("第 16 列 (C_PROJ_LEAD_EMP_NO) 为 '05972' 时，dto.projectLeaderEmployeeNumber 必须等于 '05972'")
                .isEqualTo("05972");
    }

    @Test
    void mapRow_LeavesProjectLeaderEmployeeNumberNull_WhenColumnIsNull() {
        Object[] row = rowWithProjLeadEmpNo(null);
        MarginDTO dto = MarginQuerySupport.mapRow(row);
        assertThat(dto.getProjectLeaderEmployeeNumber())
                .as("第 16 列为 null 时，dto.projectLeaderEmployeeNumber 必须为 null")
                .isNull();
    }
}
