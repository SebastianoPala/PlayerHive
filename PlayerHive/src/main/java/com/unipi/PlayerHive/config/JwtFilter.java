package com.unipi.PlayerHive.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.unipi.PlayerHive.model.user.User;
import com.unipi.PlayerHive.model.user.UserPrincipal;
import com.unipi.PlayerHive.service.MyUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that intercepts incoming HTTP requests to extract and validate the JWT token.
 * It ensures this process runs once per request.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {
    private final ApplicationContext context;

    /**
     * Constructs the JwtFilter with the application context.
     *
     * @param context The Spring application context.
     */
    @Autowired
    public JwtFilter(ApplicationContext context){
        this.context = context;
    }

    /**
     * Extracts the JWT from the Authorization header, validates it, and sets the authenticated user
     * in the Spring Security context if valid.
     *
     * @param request     The incoming HTTP request.
     * @param response    The HTTP response.
     * @param filterChain The filter chain to continue the request processing.
     * @throws ServletException If an error occurs during filtering.
     * @throws IOException      If an I/O error occurs during filtering.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String userId = null;

        if(authHeader != null && authHeader.startsWith("Bearer")){
            token = authHeader.substring(7);
            userId = JwtUtils.extractUserId(token);
        }

        if(userId != null && JwtUtils.validateToken(token)){
            User user = context.getBean(MyUserDetailsService.class).loadUserById(userId);
            UserPrincipal userPrincipal = new UserPrincipal(user);
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }

}