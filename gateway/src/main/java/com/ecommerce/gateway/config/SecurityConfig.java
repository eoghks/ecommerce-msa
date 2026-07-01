package com.ecommerce.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway Spring Security 설정.
 *
 * JWT 검증은 JwtAuthenticationFilter(GlobalFilter)에서 직접 처리하므로
 * Spring Security의 자동 인증/인가 기능은 모두 비활성화.
 * Spring Security를 추가한 이유: ReactiveSecurityContextHolder 사용을 위해서만 필요.
 * (actuator show-details: when-authorized + roles: ADMIN 권한 체크)
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()  // 인증/인가는 JwtAuthenticationFilter에서 처리
                )
                .build();
    }
}
