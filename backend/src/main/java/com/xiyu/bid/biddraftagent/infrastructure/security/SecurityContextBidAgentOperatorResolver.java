package com.xiyu.bid.biddraftagent.infrastructure.security;

import com.xiyu.bid.biddraftagent.application.BidAgentOperator;
import com.xiyu.bid.biddraftagent.application.BidAgentOperatorResolver;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityContextBidAgentOperatorResolver implements BidAgentOperatorResolver {

    private static final String DEFAULT_OPERATOR = "AI 标书 Agent";

    private final UserRepository userRepository;

    @Override
    public BidAgentOperator currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new BidAgentOperator(null, DEFAULT_OPERATOR);
        }
        return userRepository.findByUsername(authentication.getName())
                .map(this::toOperator)
                .orElse(new BidAgentOperator(null, authentication.getName()));
    }

    private BidAgentOperator toOperator(User user) {
        String displayName = user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName();
        return new BidAgentOperator(user.getId(), displayName);
    }
}
