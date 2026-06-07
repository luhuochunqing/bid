package com.xiyu.bid.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DataScopeTypeTest {

    @Test
    void testDataScopeTypeEnum() {
        assertEquals("all", DataScopeType.ALL.getCode());
        assertEquals("全部数据", DataScopeType.ALL.getDescription());
        
        assertEquals("deptAndSub", DataScopeType.DEPT_AND_SUB.getCode());
        assertEquals("本部门及下级", DataScopeType.DEPT_AND_SUB.getDescription());
        
        assertEquals("dept", DataScopeType.DEPT.getCode());
        assertEquals("本部门", DataScopeType.DEPT.getDescription());
        
        assertEquals("self", DataScopeType.SELF.getCode());
        assertEquals("仅本人", DataScopeType.SELF.getDescription());
        
        assertEquals("custom", DataScopeType.CUSTOM.getCode());
        assertEquals("自定义项目组", DataScopeType.CUSTOM.getDescription());
    }
}
