package com.xiyu.bid.demo.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class DemoModeService {

    private final Environment environment;

    public DemoModeService(Environment env) {
        this.environment = env;
    }

    public boolean isEnabled() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "e2e".equalsIgnoreCase(profile));
    }
}
