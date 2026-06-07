// Input: 标讯自由文本 → 立项枚举值映射
// Output: ApiResponse<Map<String, Map<String, String>>>
// Pos: project/controller/ - 数据字典映射查询，前端用于 auto-fill 时标讯文本转立项枚举
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标讯 → 立项 枚举值映射查询。
 *
 * <p>标讯系统存储的是中文文本（如"工业品"）或其他系统枚举值（如"INDUSTRIAL_EC"），
 * 而立项模块使用 InitiationFieldPolicy 定义的枚举（ProjectType / CustomerType）。
 * 本接口提供转换映射，前端 auto-fill 时调用此接口获取映射表，避免前端硬编码。
 *
 * <p>定义在项目模块 controller 包中，因为映射目标枚举属于项目核心域。
 * 映射关系变更时只需修改此处，所有客户端自动同步。
 */
@RestController
@RequestMapping("/api/project/tender-init-mapping")
public class TenderInitMappingController {

    private static final Map<String, String> PROJECT_TYPE_MAPPING = buildProjectTypeMapping();
    private static final Map<String, String> CUSTOMER_TYPE_MAPPING = buildCustomerTypeMapping();

    /**
     * 项目类型映射：标讯文本 → InitiationFieldPolicy.ProjectType 枚举名。
     *
     * <p>标讯来源:
     * <ul>
     *   <li>中文文本：工业品、办公、集采</li>
     *   <li>其他系统枚举：INDUSTRIAL_EC、GROUP_PURCHASE</li>
     * </ul>
     */
    private static Map<String, String> buildProjectTypeMapping() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("工业品", "INDUSTRIAL");
        m.put("INDUSTRIAL_EC", "INDUSTRIAL");
        m.put("办公", "OFFICE");
        m.put("集采", "COLLECTIVE");
        m.put("GROUP_PURCHASE", "COLLECTIVE");
        return Map.copyOf(m);
    }

    /**
     * 客户类型映射：标讯文本 → InitiationFieldPolicy.CustomerType 枚举名。
     *
     * <p>标讯来源:
     * <ul>
     *   <li>中文文本：政府机关/事业单位/高校、央企、地方国企、民企、港澳台及外企</li>
     *   <li>其他系统枚举：GOVERNMENT_INSTITUTION、PRIVATE_ENTERPRISE、FOREIGN_HK_MACAO_TW</li>
     * </ul>
     */
    private static Map<String, String> buildCustomerTypeMapping() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("政府机关/事业单位/高校", "GOVERNMENT");
        m.put("GOVERNMENT_INSTITUTION", "GOVERNMENT");
        m.put("央企", "CENTRAL_SOE");
        m.put("地方国企", "LOCAL_SOE");
        m.put("民企", "PRIVATE");
        m.put("PRIVATE_ENTERPRISE", "PRIVATE");
        m.put("港澳台及外企", "FOREIGN");
        m.put("FOREIGN_HK_MACAO_TW", "FOREIGN");
        return Map.copyOf(m);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Map<String, String>>>> getMapping() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        result.put("projectType", PROJECT_TYPE_MAPPING);
        result.put("customerType", CUSTOMER_TYPE_MAPPING);
        return ResponseEntity.ok(ApiResponse.success("ok", result));
    }
}
