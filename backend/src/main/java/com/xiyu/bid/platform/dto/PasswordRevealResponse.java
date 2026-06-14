package com.xiyu.bid.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response payload for revealing a platform account password.
 *
 * <p>Security (H12): the password is wrapped with an explicit reveal
 * window ({@code expiresAt}) and an audit correlation id ({@code auditId})
 * so the client can show a "visible for 5 minutes" indicator and the
 * server can correlate a later access-log entry. The {@code auditId}
 * should be logged on every reveal and is also stamped in the
 * {@code Cache-Control: no-store} response header (set in
 * {@code PlatformAccountController#getPassword}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRevealResponse {

    /** Plaintext password value. Treat as short-lived secret. */
    private String password;

    /** When the client-side reveal window expires. */
    private LocalDateTime expiresAt;

    /** Audit correlation id for this reveal event. */
    private String auditId;
}
