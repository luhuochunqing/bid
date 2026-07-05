package com.xiyu.bid.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfValidatorTest {

    @Test
    void shouldAcceptHttpsUrl() {
        assertThatCode(() -> SsrfValidator.validate("https://openrouter.ai/api/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptHttpUrl() {
        assertThatCode(() -> SsrfValidator.validate("http://localhost:11434/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptInternalNetworkIp() {
        assertThatCode(() -> SsrfValidator.validate("http://10.0.0.5:8080/v1/chat/completions"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfValidator.validate("http://192.168.1.10:8080/v1/chat/completions"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfValidator.validate("http://172.16.5.5:8080/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptLoopbackIp() {
        assertThatCode(() -> SsrfValidator.validate("http://127.0.0.1:11434/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectCloudMetadataAddress() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许指向该地址");
    }

    @Test
    void shouldRejectLinkLocalRange() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://169.254.0.1/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroNetwork() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://0.0.0.0/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectClassEReserved() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://240.0.0.1/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBroadcastAddress() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://255.255.255.255/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUrlWithUserInfo() {
        assertThatThrownBy(() -> SsrfValidator.validate("https://user:pass@openrouter.ai/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许包含 userinfo");
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThatThrownBy(() -> SsrfValidator.validate("ftp://example.com/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 http 或 https");
    }

    @Test
    void shouldRejectInvalidUrl() {
        assertThatThrownBy(() -> SsrfValidator.validate("not a url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式无效");
    }

    @Test
    void shouldRejectNullUrl() {
        assertThatThrownBy(() -> SsrfValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThatThrownBy(() -> SsrfValidator.validate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        assertThatThrownBy(() -> SsrfValidator.validate("https:///v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    void shouldRejectUnspecifiedIpv6() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://[::]/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许指向该地址");
    }

    @Test
    void shouldRejectLinkLocalIpv6() {
        assertThatThrownBy(() -> SsrfValidator.validate("http://[fe80::1]/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectLinkLocalIpv6Fe81() {
        // fe81:: 也属于 fe80::/10 link-local 范围
        assertThatThrownBy(() -> SsrfValidator.validate("http://[fe81::1]/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectLinkLocalIpv6Febf() {
        // febf:: 是 fe80::/10 范围上界，仍应拒绝
        assertThatThrownBy(() -> SsrfValidator.validate("http://[febf::1]/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptIpv6Fec0() {
        // fec0:: 紧邻 fe80::/10 范围外，应放行
        assertThatCode(() -> SsrfValidator.validate("http://[fec0::1]/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptLoopbackIpv6() {
        assertThatCode(() -> SsrfValidator.validate("http://[::1]:11434/v1/chat/completions"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectIpv4MappedCloudMetadata() {
        // IPv4-mapped IPv6: ::ffff:169.254.169.254 应该被识别为云元数据并拒绝
        assertThatThrownBy(() -> SsrfValidator.validate("http://[::ffff:169.254.169.254]/v1/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许指向该地址");
    }
}
