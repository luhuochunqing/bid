package com.xiyu.bid.common.display;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 枚举元数据端点。
 *
 * <p>前端可在应用启动时调用 {@code GET /api/enums/metadata}，
 * 获取所有 {@link DisplayableEnum} 的值-标签映射，无需在前端硬编码。
 * 后端新增/修改枚举值后，前端下次启动自动生效。</p>
 */
@RestController
@RequestMapping("/api/enums")
public class EnumMetadataController {

    @GetMapping("/metadata")
    public Map<String, List<EnumMetadataResponse.EnumPair>> getEnumMetadata() {
        Map<String, List<EnumMetadataResponse.EnumPair>> result = new LinkedHashMap<>();
        EnumDisplayRegistry.getRegisteredEnums().forEach((enumClass, displayValues) ->
                result.put(enumClass.getSimpleName(), displayValues)
        );
        return result;
    }
}
