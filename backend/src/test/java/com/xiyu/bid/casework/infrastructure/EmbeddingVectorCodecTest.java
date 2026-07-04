package com.xiyu.bid.casework.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmbeddingVectorCodec - float[] 与 byte[] 互转")
class EmbeddingVectorCodecTest {

    @Test
    @DisplayName("空数组应编码为空字节数组")
    void encode_emptyArray_shouldReturnEmptyBytes() {
        assertThat(EmbeddingVectorCodec.encode(new float[0])).isEmpty();
    }

    @Test
    @DisplayName("null 应编码为 null")
    void encode_nullArray_shouldReturnNull() {
        assertThat(EmbeddingVectorCodec.encode(null)).isNull();
    }

    @Test
    @DisplayName("单精度浮点数组应按 little-endian 4 字节 float 编码")
    void encode_shouldReturnLittleEndianFloatBytes() {
        float[] vector = {1.0f, -2.5f, 0.0f};

        byte[] bytes = EmbeddingVectorCodec.encode(vector);

        assertThat(bytes).hasSize(12);
        float[] decoded = EmbeddingVectorCodec.decode(bytes);
        assertThat(decoded).containsExactly(vector);
    }

    @Test
    @DisplayName("1024 维向量应编码为 4096 字节")
    void encode_1024Dimension_shouldReturn4096Bytes() {
        float[] vector = new float[1024];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = i * 0.001f;
        }

        byte[] bytes = EmbeddingVectorCodec.encode(vector);

        assertThat(bytes).hasSize(4096);
        assertThat(EmbeddingVectorCodec.decode(bytes)).containsExactly(vector);
    }

    @Test
    @DisplayName("空字节数组应解码为空 float 数组")
    void decode_emptyBytes_shouldReturnEmptyArray() {
        assertThat(EmbeddingVectorCodec.decode(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("null 应解码为 null")
    void decode_nullBytes_shouldReturnNull() {
        assertThat(EmbeddingVectorCodec.decode(null)).isNull();
    }

    @Test
    @DisplayName("非 4 倍长度的字节数组应抛出 IllegalArgumentException")
    void decode_invalidLength_shouldThrow() {
        byte[] bytes = new byte[]{0x01, 0x02, 0x03};

        assertThatThrownBy(() -> EmbeddingVectorCodec.decode(bytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding bytes length");
    }

    @Test
    @DisplayName("编码后再解码应精确还原原向量")
    void roundTrip_shouldPreserveValues() {
        float[] vector = {Float.MAX_VALUE, Float.MIN_VALUE, 3.14159f, -0.0001f};

        byte[] bytes = EmbeddingVectorCodec.encode(vector);
        float[] decoded = EmbeddingVectorCodec.decode(bytes);

        assertThat(decoded).containsExactly(vector);
    }
}
