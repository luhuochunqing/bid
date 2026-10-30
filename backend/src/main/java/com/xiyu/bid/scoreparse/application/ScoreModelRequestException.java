// Input: failed model transport or response processing with original cause
// Output: safe task-facing error, retaining cause for server diagnostics
// Pos: scoreparse application boundary between model transport and task orchestration
package com.xiyu.bid.scoreparse.application;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

public class ScoreModelRequestException extends RuntimeException {
    public ScoreModelRequestException(RuntimeException cause) {
        super(isTimeout(cause) ? "评分模型请求超时，请稍后重试" : "评分模型调用失败，请稍后重试", cause);
    }

    private static boolean isTimeout(Throwable cause) {
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) return true;
        }
        return false;
    }
}
