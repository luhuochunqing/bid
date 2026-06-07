package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * CRM 通用分页响应。
 * <p>客户 CRM 分页接口的返回格式统一为：
 * {@code {code, msg, totalCount, pageSize, pageIndex, dataList}}。
 *
 * @param <T> dataList 元素类型
 */
public record CrmPageResponse<T>(
    @JsonProperty("code") String code,
    @JsonProperty("msg") String msg,
    @JsonProperty("totalCount") int totalCount,
    @JsonProperty("pageSize") int pageSize,
    @JsonProperty("pageIndex") int pageIndex,
    @JsonProperty("dataList") List<T> dataList
) {

    /** 判断业务是否成功。 */
    public boolean isSuccess() {
        return "0".equals(code) || "".equals(code);
    }
}
