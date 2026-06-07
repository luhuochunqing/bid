package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.application.TenderBreakdownReadiness;
import com.xiyu.bid.biddraftagent.application.TenderIntakeConfigurationReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAiTenderIntakeConfigurationReadiness implements TenderIntakeConfigurationReadiness {

    private final OpenAiBidAgentConfigurationResolver configurationResolver;

    @Override
    public TenderBreakdownReadiness current() {
        return configurationResolver.hasTenderIntakeConfiguration()
                ? TenderBreakdownReadiness.configured()
                : TenderBreakdownReadiness.missingDeepSeekKey();
    }
}
