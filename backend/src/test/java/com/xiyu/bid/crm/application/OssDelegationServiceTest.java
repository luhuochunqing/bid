package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OssDelegationServiceTest {

    @Mock
    private CrmHttpClient crmHttpClient;

    @Mock
    private CrmProperties crmProperties;

    private OssDelegationService service;

    @BeforeEach
    void setUp() {
        CrmProperties.CrmAuthPaths authPaths = new CrmProperties.CrmAuthPaths();
        authPaths.setOauthLoginPath("/oauth/login");
        authPaths.setLogoutPath("/oauth/logout");
        when(crmProperties.getAuth()).thenReturn(authPaths);
        when(crmProperties.getEffectiveAuthBaseUrl()).thenReturn("https://base-oss.ehsy.com");

        service = new OssDelegationService(crmHttpClient, crmProperties);
    }

    @Test
    void authenticate_shouldReturnTrueWhenOssRespondsSuccess() {
        User user = User.builder().username("08152").build();
        when(crmHttpClient.postForm(eq("https://base-oss.ehsy.com"), eq("/oauth/login"), any(LinkedMultiValueMap.class)))
                .thenReturn(new CrmResponseHandler.CrmApiResponse(0, "", null, true));

        boolean result = service.authenticate(user, "secret");

        assertThat(result).isTrue();
    }
}
