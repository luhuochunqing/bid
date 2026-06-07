// Input: e2e profile readiness request
// Output: deterministic configured readiness so tests never depend on external AI keys
// Pos: biddraftagent/infrastructure/e2e - test-profile adapter, no production activation
package com.xiyu.bid.biddraftagent.infrastructure.e2e;

import com.xiyu.bid.biddraftagent.application.TenderBreakdownReadiness;
import com.xiyu.bid.biddraftagent.application.TenderIntakeConfigurationReadiness;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Primary
@Profile("e2e")
public class E2eTenderIntakeConfigurationReadiness implements TenderIntakeConfigurationReadiness {

    @Override
    public TenderBreakdownReadiness current() {
        return TenderBreakdownReadiness.configured();
    }
}
