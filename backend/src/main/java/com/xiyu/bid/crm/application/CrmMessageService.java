package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.domain.CrmMessageRouter;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CrmMessageService {

    private static final Logger LOG = LoggerFactory.getLogger(CrmMessageService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmMessageService(CrmHttpClient httpClient, CrmAuthService authService,
                             CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public CrmResponseHandler.CrmApiResponse sendMessages(List<Map<String, Object>> messages) {
        String token = authService.getValidToken();
        int maxBatch = properties.getMessageBatchMaxSize();
        String baseUrl = properties.getEffectiveMessageBaseUrl();
        String path = properties.getMessage().getSendPath();

        if (messages.size() <= maxBatch) {
            return httpClient.post(baseUrl, path, token,
                    Map.of("messages", messages));
        }

        var batches = CrmMessageRouter.splitBatches(messages, maxBatch);
        List<Map<String, Object>> allResults = new ArrayList<>();
        for (var batch : batches) {
            CrmResponseHandler.CrmApiResponse response = httpClient.post(
                    baseUrl, path, token, Map.of("messages", batch));
            if (response.data() != null && response.data().has("results")) {
                var results = response.data().get("results");
                if (results.isArray()) {
                    results.forEach(node -> allResults.add(Map.of("result", node)));
                }
            }
        }
        LOG.info("Sent {} messages in {} batches", messages.size(), batches.size());
        return CrmResponseHandler.CrmApiResponse.parseError(
                "Batched; see individual results");
    }
}
