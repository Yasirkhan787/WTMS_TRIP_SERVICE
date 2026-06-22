package com.yasirkhan.trip.configurations;

import com.yasirkhan.trip.exceptions.UnauthorizedException;
import com.yasirkhan.trip.models.UserPrincipal;
import com.yasirkhan.trip.services.implementations.DownstreamJwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
//import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

@Component
public class AuthorizationFilter extends OncePerRequestFilter {

    @Value("${app.security.internal-secret}")
    private String GATEWAY_SECRET = "";

    private final DownstreamJwtService jwtService;
    //private final StringRedisTemplate redisTemplate;
    private final HandlerExceptionResolver exceptionResolver;

    public AuthorizationFilter(DownstreamJwtService jwtService,
                               //StringRedisTemplate redisTemplate,
                               @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        //this.redisTemplate = redisTemplate;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String incomingSecret = request.getHeader("X-Gateway-Secret");

            if (incomingSecret == null || !incomingSecret.equals(GATEWAY_SECRET)) {
                throw new UnauthorizedException("Direct access blocked: Request must come through API Gateway");
            }

            // Extract Token
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new UnauthorizedException("Missing Authorization Token");
            }

            String token = authHeader.substring(7);

            // Mathematical Validation (Extract claims using Public Key)
            String username = jwtService.extractUsername(token);
            String role = jwtService.extractRole(token);
            String userId = jwtService.extractUserId(token);
            Integer tokenVersion = jwtService.extractTokenVersion(token);

            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("role", role);

            // 4Redis Revocation Check
            String redisKey = "user:" + userId + ":tokenVersion";
            // String currentRedisVersion = redisTemplate.opsForValue().get(redisKey);

            /* if (currentRedisVersion == null || !currentRedisVersion.equals(String.valueOf(tokenVersion))) {
                throw new UnauthorizedException("Session expired or revoked. Please log in again.");
            }
            */

            // Authenticate in Spring Context
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String authorityRole = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authorityRole));

                UserPrincipal customPrincipal = new UserPrincipal(userId, username, role);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(customPrincipal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // Bridge any JWT or Auth errors to your GlobalExceptionHandler!
            exceptionResolver.resolveException(request, response, null, e);
        }
    }
}