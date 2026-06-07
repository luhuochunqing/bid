package com.xiyu.bid.security;

import com.xiyu.bid.enums.DataScopeType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DataScopeContext {
    private DataScopeType scopeType;
    private String userAlias;
    private String deptAlias;
    private Long currentUserId;
    private List<String> allowedDeptCodes;
    private List<Long> explicitProjectIds;
}
