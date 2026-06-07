package com.xiyu.bid.platform.async.domain;

public enum AsyncAction {
    DROP,
    SUCCEED_WITH_LOG,
    RETRY,
    DEAD_LETTER,
    FAIL_MAIN_TRANSACTION
}
