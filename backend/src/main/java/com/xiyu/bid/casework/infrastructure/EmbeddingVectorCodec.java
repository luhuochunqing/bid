package com.xiyu.bid.casework.infrastructure;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pure-core codec for float[] embedding vectors.
 *
 * <p>Encodes a float array into little-endian IEEE-754 bytes and decodes it back.
 * This class has no framework dependencies and can be unit-tested in isolation.</p>
 */
public final class EmbeddingVectorCodec {

    private static final int BYTES_PER_FLOAT = Float.BYTES;

    private EmbeddingVectorCodec() {
        // utility class
    }

    /**
     * Encodes a float vector to a little-endian byte array.
     *
     * @param vector the vector to encode; may be {@code null}
     * @return the encoded bytes, or {@code null} if input is {@code null}
     */
    public static byte[] encode(float[] vector) {
        if (vector == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * BYTES_PER_FLOAT)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    /**
     * Decodes a little-endian byte array back to a float vector.
     *
     * @param bytes the bytes to decode; may be {@code null}
     * @return the decoded vector, or {@code null} if input is {@code null}
     * @throws IllegalArgumentException if {@code bytes} length is not a multiple of 4
     */
    public static float[] decode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length % BYTES_PER_FLOAT != 0) {
            throw new IllegalArgumentException(
                    "embedding bytes length must be a multiple of " + BYTES_PER_FLOAT
                            + ", got " + bytes.length);
        }
        if (bytes.length == 0) {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / BYTES_PER_FLOAT];
        buffer.asFloatBuffer().get(vector);
        return vector;
    }
}
