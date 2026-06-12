package com.xiyu.bid.common.display;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DisplayableEnum 注册中心。
 *
 * <p>实现 DisplayableEnum 的枚举类在此显式注册，供 {@link EnumMetadataController}
 * 统一暴露给前端（手动注册，避免 classpath 扫描开销）。</p>
 *
 * <h3>添加新枚举的步骤</h3>
 * <ol>
 *   <li>让枚举类实现 {@link DisplayableEnum} 接口</li>
 *   <li>在本类的 {@code static} 块中注册该枚举类的 FQCN</li>
 *   <li>前端调用 {@code GET /api/enums/metadata} 即可自动获取新映射</li>
 * </ol>
 */
public final class EnumDisplayRegistry {

    private static final Map<Class<? extends DisplayableEnum>, List<EnumMetadataResponse.EnumPair>> REGISTRY = new LinkedHashMap<>();

    static {
        //
        // 在此注册实现了 DisplayableEnum 的枚举类。
        register("com.xiyu.bid.biddraftagent.domain.validation.QualificationMatchStatus");
    }

    private EnumDisplayRegistry() {}

    /**
     * 注册一个实现了 {@link DisplayableEnum} 的枚举类。
     */
    @SuppressWarnings("unchecked")
    public static void register(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isEnum()) {
                throw new IllegalArgumentException(clazz.getSimpleName() + " must be an enum");
            }
            Class<? extends DisplayableEnum> enumClass = (Class<? extends DisplayableEnum>) clazz;
            List<EnumMetadataResponse.EnumPair> pairs = new ArrayList<>();
            for (DisplayableEnum value : enumClass.getEnumConstants()) {
                pairs.add(new EnumMetadataResponse.EnumPair(
                        ((Enum<?>) value).name(),
                        value.getDisplayName()
                ));
            }
            REGISTRY.put(enumClass, List.copyOf(pairs));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Enum class not found: " + className, e);
        }
    }

    /**
     * 返回当前已注册的所有枚举及其值-标签映射（不可修改）。
     */
    public static Map<Class<? extends DisplayableEnum>, List<EnumMetadataResponse.EnumPair>> getRegisteredEnums() {
        return Map.copyOf(REGISTRY);
    }
}
