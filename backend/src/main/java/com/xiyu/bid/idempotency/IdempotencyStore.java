// Input: 幂等键、首次响应快照
// Output: 缓存快照（命中时复用）
// Pos: Idempotency/存储抽象
// 维护声明: 仅维护幂等存储契约；后端切换不应影响调用方语义。
package com.xiyu.bid.idempotency;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {

    Optional<CachedResponse> find(String key);

    void save(String key, CachedResponse response, Duration ttl);

    final class CachedResponse {
        private final int status;
        private final String contentType;
        private final byte[] body;
        private final String requestBodyHash;

        public CachedResponse(int status, String contentType, byte[] body, String requestBodyHash) {
            this.status = status;
            this.contentType = contentType;
            this.body = body == null ? new byte[0] : body.clone();
            this.requestBodyHash = requestBodyHash;
        }

        public int getStatus() {
            return status;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getBody() {
            return body.clone();
        }

        public String getRequestBodyHash() {
            return requestBodyHash;
        }
    }
}
