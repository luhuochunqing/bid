package com.xiyu.bid.templatecatalog.domain.valueobject;

public record EnumParseResult<E>(E value, String message) {

    public static <E> EnumParseResult<E> success(E value) {
        return new EnumParseResult<>(value, null);
    }

    public static <E> EnumParseResult<E> failure(String message) {
        return new EnumParseResult<>(null, message);
    }

    public boolean valid() {
        return message == null;
    }
}
