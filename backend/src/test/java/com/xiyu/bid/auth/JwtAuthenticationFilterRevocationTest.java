package com.xiyu.bid.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterRevocationTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, tokenRevocationService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipAuthenticationForRevokedToken() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenReturn("alice");
        when(jwtUtil.validateToken(token, "alice")).thenReturn(true);
        when(jwtUtil.extractJti(token)).thenReturn(Optional.of("jti-revoked"));
        when(tokenRevocationService.isRevoked("jti-revoked")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateWhenJtiNotRevoked() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenReturn("alice");
        when(jwtUtil.validateToken(token, "alice")).thenReturn(true);
        when(jwtUtil.extractJti(token)).thenReturn(Optional.of("jti-live"));
        when(tokenRevocationService.isRevoked("jti-live")).thenReturn(false);
        UserDetails userDetails = new User("alice", "x", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldKeepApiKeyAuthenticationWhenJwtCookieAlsoExists() throws Exception {
        String token = "valid.jwt.token";
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "api-key:3",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL_API"))
        ));
        lenient().when(request.getCookies()).thenReturn(new Cookie[] { new Cookie("access_token", token) });
        lenient().when(jwtUtil.extractUsername(token)).thenReturn("06234");
        lenient().when(jwtUtil.validateToken(token, "06234")).thenReturn(true);
        lenient().when(jwtUtil.extractJti(token)).thenReturn(Optional.of("jti-live"));
        lenient().when(tokenRevocationService.isRevoked("jti-live")).thenReturn(false);
        UserDetails userDetails = new User("06234", "x", Collections.emptyList());
        lenient().when(userDetailsService.loadUserByUsername("06234")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("api-key:3");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_EXTERNAL_API");
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateLegacyTokenWithoutJti() throws Exception {
        String token = "legacy.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.extractUsername(token)).thenReturn("alice");
        when(jwtUtil.validateToken(token, "alice")).thenReturn(true);
        when(jwtUtil.extractJti(token)).thenReturn(Optional.empty());
        UserDetails userDetails = new User("alice", "x", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(tokenRevocationService, never()).isRevoked(any());
    }
}
