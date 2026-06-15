package com.unipi.PlayerHive.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Main configuration class for Spring Security.
 * Defines endpoint authorizations, disables stateful sessions, configures CORS, and registers the JWT filter.
 */
@Configuration
@EnableWebSecurity
public class SecuritySpringBoot {

    private final JwtFilter jwtFilter;

    /**
     * Constructs the security configuration with the custom JWT filter.
     *
     * @param jwtFilter The custom filter for JWT processing.
     */
    @Autowired
    public SecuritySpringBoot(JwtFilter jwtFilter){
        this.jwtFilter = jwtFilter;
    }

    /**
     * Configures the security filter chain, defining which endpoints are public and which require specific roles.
     *
     * @param http The HttpSecurity object to configure.
     * @return The configured SecurityFilterChain.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // for swagger
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/games/getRecommendations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/games/getHiddenGems").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/games/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/user/My*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/user/friendRequests").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/user/friendRecommendations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/user/gamingTwins").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/user/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()) // any logged-in user gets through, admin-only routes are already locked above
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Exposes the AuthenticationManager as a Bean.
     *
     * @param config The AuthenticationConfiguration.
     * @return The configured AuthenticationManager.
     * @throws Exception If an error occurs retrieving the manager.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Defines the password encoder to be used for hashing and verifying passwords.
     *
     * @return A BCryptPasswordEncoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Configures the CORS settings for the application.
     *
     * @return The configured CorsConfigurationSource.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}