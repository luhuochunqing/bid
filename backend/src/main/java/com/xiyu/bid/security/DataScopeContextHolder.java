package com.xiyu.bid.security;

public class DataScopeContextHolder {
    private static final ThreadLocal<DataScopeContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(DataScopeContext context) {
        CONTEXT.set(context);
    }

    public static DataScopeContext getContext() {
        return CONTEXT.get();
    }

    public static void clearContext() {
        CONTEXT.remove();
    }
}
