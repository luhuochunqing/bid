package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTenderDocumentStorageSpringContextTest {

    @Test
    void shouldCreateStorageBeanWithConfiguredConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(LocalTenderDocumentStorage.class);
            context.refresh();

            assertThat(context.getBean(TenderDocumentStorage.class))
                    .isInstanceOf(LocalTenderDocumentStorage.class);
        }
    }
}
