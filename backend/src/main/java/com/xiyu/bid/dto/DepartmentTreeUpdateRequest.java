package com.xiyu.bid.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DepartmentTreeUpdateRequest {
    private List<DataScopeConfigResponse.DepartmentTreeItem> deptTree =
            new ArrayList<>();
}
