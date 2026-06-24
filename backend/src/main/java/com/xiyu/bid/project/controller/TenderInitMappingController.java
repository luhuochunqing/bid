// Input: 标讯自由文本 → 立项枚举值映射
// Output: ApiResponse<Map<String, Map<String, String>>>
// Pos: project/controller/ - 数据字典映射查询，前端用于 auto-fill 时标讯文本转立项枚举
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标讯 → 立项 枚举值映射查询。
 *
 * <p>标讯系统存储的是中文文本（如"工业品"）或其他系统枚举值（如"INDUSTRIAL_EC"），
 * 而立项模块使用 {@link InitiationFieldPolicy} 定义的枚举（ProjectType / CustomerType）。
 * 本接口提供转换映射，前端 auto-fill 时调用此接口获取映射表，避免前端硬编码。
 *
 * <p>映射数据源统一来自 {@link InitiationFieldPolicy#PROJECT_TYPE_MAPPING} 和
 * {@link InitiationFieldPolicy#CUSTOMER_TYPE_MAPPING}，变更时只需修改 InitiationFieldPolicy。
 */
@RestController
@RequestMapping("/api/project/tender-init-mapping")
@PreAuthorize("isAuthenticated()")
public class TenderInitMappingController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Map<String, String>>>> getMapping() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        result.put("projectType", InitiationFieldPolicy.PROJECT_TYPE_MAPPING);
        result.put("customerType", InitiationFieldPolicy.CUSTOMER_TYPE_MAPPING);
        return ResponseEntity.ok(ApiResponse.success("ok", result));
    }
}
