package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class ScoringModelPolicyTest {
    @Test void changingModelChangesIdentityButCredentialsAndSignedQueryDoNot() {
        var a=config("key-a","http://a:secret@provider.invalid/v1?signature=secret","model-a");
        var b=config("key-b","http://b:different@provider.invalid/v1?signature=other","model-a");
        assertThat(ScoringModelPolicy.fingerprint(a)).isEqualTo(ScoringModelPolicy.fingerprint(b));
        assertThat(ScoringModelPolicy.fingerprint(a)).isNotEqualTo(ScoringModelPolicy.fingerprint(config("key-a","http://provider.invalid/v1","model-b")));
    }
    private OpenAiBidAgentRequestConfig config(String key,String url,String model) {
        return new OpenAiBidAgentRequestConfig(key,url,model,Duration.ofSeconds(30),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }
}
