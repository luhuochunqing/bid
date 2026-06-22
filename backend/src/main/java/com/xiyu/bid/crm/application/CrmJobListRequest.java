package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 获取用户角色列表请求。
 * POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList
 */
@Data
public class CrmJobListRequest {
    /** 工号列表. */
    private List<String> data;
}
