package com.xiyu.bid.templatecatalog.domain.valueobject;

final class EnumValueParser {

    private EnumValueParser() {
    }

    static <E extends Enum<E>> E parseOrNull(Class<E> enumClass, String value) {
        return parse(enumClass, value, null).value();
    }

    static <E extends Enum<E>> EnumParseResult<E> parse(Class<E> enumClass, String value, String label) {
        if (value == null || value.isBlank()) {
            return EnumParseResult.success(null);
        }

        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return EnumParseResult.success(constant);
            }
            if (constant instanceof ProductType productType && productType.getLabel().equalsIgnoreCase(value)) {
                return EnumParseResult.success(enumClass.cast(productType));
            }
            if (constant instanceof IndustryType industryType && industryType.getLabel().equalsIgnoreCase(value)) {
                return EnumParseResult.success(enumClass.cast(industryType));
            }
            if (constant instanceof DocumentType documentType && documentType.getLabel().equalsIgnoreCase(value)) {
                return EnumParseResult.success(enumClass.cast(documentType));
            }
        }

        return EnumParseResult.failure("不支持的" + label + ": " + value);
    }
}
