package com.richreach.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TermsProperties.class, LoginLockProperties.class})
public class AuthConfig {
}
