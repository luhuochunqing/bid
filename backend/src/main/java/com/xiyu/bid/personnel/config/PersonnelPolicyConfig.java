package com.xiyu.bid.personnel.config;

import com.xiyu.bid.personnel.domain.service.CertificateExpiryPolicy;
import com.xiyu.bid.personnel.domain.service.PersonnelChangeDetector;
import com.xiyu.bid.personnel.domain.service.PersonnelValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class PersonnelPolicyConfig {
    @Bean
    public CertificateExpiryPolicy certificateExpiryPolicy() { return new CertificateExpiryPolicy(); }
    @Bean
    public PersonnelValidator personnelValidator() { return new PersonnelValidator(); }
    @Bean
    public PersonnelChangeDetector personnelChangeDetector() { return new PersonnelChangeDetector(); }
}
