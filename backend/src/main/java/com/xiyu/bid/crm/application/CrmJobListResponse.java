package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 获取用户角色列表响应。
 * POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList
 */
@Data
public class CrmJobListResponse {
    /** 返回信息. */
    private String msg;
    /** 状态码 0成功 其他失败. */
    private double code;
    /** 角色数据，key为工号. */
    private Map<String, JobInfo> data;
    /** 时间戳. */
    private long timestamp;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobInfo {
        /** 任职角色. */
        private String jobName;
        /** 员工状态. */
        private String employeeStatus;
        /** 工号. */
        private String jobNumber;
        /** 姓名. */
        private String username;
        /** 状态 0无效 1有效. */
        private String status;
        /** 系统角色列表. */
        private List<SysRole> sysRoleList;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SysRole {
        /** 角色id. */
        private String id;
        /** 角色名称. */
        private String roleName;
        /** 状态 0无效 1有效. */
        private String status;
        /** 是否默认角色. */
        private String isDefault;
        /** 创建时间. */
        private String createAt;
        /** 创建人. */
        private String createBy;
        /** 更新时间. */
        private String updateAt;
        /** 更新人. */
        private String updateBy;
        /** 删除标记. */
        private Boolean del;
    }
}
