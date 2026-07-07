package de.focusshift.zeiterfassung.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import static org.springframework.security.config.http.SessionCreationPolicy.NEVER;

@Configuration
class SecurityApiConfiguration {

    private final String apiKey;

    SecurityApiConfiguration(@Value("${zeiterfassung.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(final HttpSecurity http) {
        return http
            .securityMatcher("/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(requests ->
                requests
                    .requestMatchers("/api/**").permitAll()
                    .anyRequest().authenticated()
            ).sessionManagement(
                sessionManagement -> sessionManagement.sessionCreationPolicy(NEVER)
            )
            .addFilterBefore(new ApiKeyFilter(apiKey), BasicAuthenticationFilter.class)
            .build();
    }
}
