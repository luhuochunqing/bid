// Input: configured abandon role list and authenticated principal
// Output: authorization decision for tender status update transitions (incl. ABANDONED)
// Pos: backend/.../batch/security - role-based authorizer for tender status batch endpoint
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.batch.security;

import com.xiyu.bid.batch.dto.BatchTenderStatusUpdateRequest;
import com.xiyu.bid.entity.Tender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component("tenderAbandonAuthorizer")
public class TenderAbandonAuthorizer {

    private final List<String> abandonRoles;

    public TenderAbandonAuthorizer(
            @Value("${xiyu.tender.abandon-roles:ADMIN,MANAGER}") String abandonRolesCsv) {
        this.abandonRoles = Arrays.stream(abandonRolesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .toList();
    }

    public boolean canUpdate(Authentication authentication, BatchTenderStatusUpdateRequest request) {
        if (authentication == null || request == null) {
            return false;
        }
        boolean targetIsAbandon = request.getStatus() != null
                && Tender.Status.ABANDONED.name().equalsIgnoreCase(request.getStatus().trim());
        if (!targetIsAbandon) {
            return hasAnyRole(authentication, "ADMIN", "MANAGER");
        }
        return abandonRoles.stream().anyMatch(role -> hasRole(authentication, role));
    }

    private boolean hasAnyRole(Authentication auth, String... roles) {
        for (String role : roles) {
            if (hasRole(auth, role)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRole(Authentication auth, String role) {
        String prefixed = "ROLE_" + role.toUpperCase(Locale.ROOT);
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (prefixed.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public List<String> getAbandonRoles() {
        return abandonRoles;
    }
}
