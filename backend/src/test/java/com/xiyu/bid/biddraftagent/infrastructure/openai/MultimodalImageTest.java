package com.xiyu.bid.biddraftagent.infrastructure.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultimodalImageTest {

    @Test
    void dataUrlAssemblesMimeTypeAndBase64() {
        MultimodalImage image = new MultimodalImage("image/png", "aGk=");
        assertThat(image.dataUrl()).isEqualTo("data:image/png;base64,aGk=");
    }

    @Test
    void dataUrlKeepsBase64Untouched() {
        MultimodalImage image = new MultimodalImage("image/jpeg", "AAAA/+B=");
        assertThat(image.dataUrl()).isEqualTo("data:image/jpeg;base64,AAAA/+B=");
    }
}
