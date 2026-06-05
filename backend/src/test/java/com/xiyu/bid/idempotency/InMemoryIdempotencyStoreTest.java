package com.xiyu.bid.idempotency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    @Test
    void shouldReturnSavedResponse() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(
                200, "application/json", "{\"ok\":true}".getBytes(), "hash-a"
        );

        store.save("key-1", cached, Duration.ofSeconds(60));

        Optional<IdempotencyStore.CachedResponse> hit = store.find("key-1");
        assertThat(hit).isPresent();
        assertThat(hit.get().getStatus()).isEqualTo(200);
        assertThat(new String(hit.get().getBody())).isEqualTo("{\"ok\":true}");
        assertThat(hit.get().getRequestBodyHash()).isEqualTo("hash-a");
    }

    @Test
    void shouldReturnEmptyForMissing() {
        assertThat(store.find("never-stored")).isEmpty();
    }

    @Test
    void shouldExpireAfterTtl() throws Exception {
        store.save("key-2", new IdempotencyStore.CachedResponse(200, "application/json", new byte[0], "hash"),
                Duration.ofMillis(10));

        Thread.sleep(25);
        assertThat(store.find("key-2")).isEmpty();
    }

    @Test
    void shouldIgnoreNullOrBlankKey() {
        store.save(null, new IdempotencyStore.CachedResponse(200, "application/json", new byte[0], "hash"),
                Duration.ofSeconds(60));
        store.save("", new IdempotencyStore.CachedResponse(200, "application/json", new byte[0], "hash"),
                Duration.ofSeconds(60));

        assertThat(store.find(null)).isEmpty();
        assertThat(store.find("")).isEmpty();
    }
}
