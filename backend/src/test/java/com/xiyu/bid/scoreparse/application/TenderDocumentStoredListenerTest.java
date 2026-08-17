package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.TenderDocumentStoredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderDocumentStoredListenerTest {

    @Mock
    private ScoreParseAppService scoreParseAppService;

    @InjectMocks
    private TenderDocumentStoredListener listener;

    @Test
    void skipsTriggerWhenAutoGateClosed() {
        when(scoreParseAppService.allowAutoParse(9L)).thenReturn(false);

        listener.onTenderDocumentStored(new TenderDocumentStoredEvent(9L, 3L, "doc-insight://t/1"));

        verify(scoreParseAppService, never()).triggerParseFromEvent(9L);
    }

    @Test
    void triggersWhenAutoGateOpen() {
        when(scoreParseAppService.allowAutoParse(9L)).thenReturn(true);

        listener.onTenderDocumentStored(new TenderDocumentStoredEvent(9L, 3L, "doc-insight://t/1"));

        verify(scoreParseAppService).triggerParseFromEvent(9L);
    }
}
