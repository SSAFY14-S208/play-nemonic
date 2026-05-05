package com.nemonicworld.common.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.auth.entity.AdminUser;
import com.nemonicworld.auth.repository.AdminUserRepository;
import com.nemonicworld.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_ME_PATH = "/api/v1/auth/admin/me";
    private static final String ADMIN_LOGOUT_PATH = "/api/v1/auth/admin/logout";
    private static final String ADMIN_API_PREFIX = "/api/v1/admin/";

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

    public AdminJwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, AdminUserRepository adminUserRepository,
        ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminUserRepository = adminUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = resolveRequestPath(request);

        return !ADMIN_ME_PATH.equals(servletPath) && !ADMIN_LOGOUT_PATH.equals(servletPath)
            && !servletPath.startsWith(ADMIN_API_PREFIX);
    }

    private String resolveRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }

        return requestUri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorizationHeader.substring(BEARER_PREFIX.length());
            AdminTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
            AdminUser adminUser = adminUserRepository.findActiveById(claims.adminId())
                .orElseThrow(() -> new IllegalArgumentException("Admin account is not active."));
            AdminPrincipal principal = AdminPrincipal.from(adminUser);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_%s".formatted(principal.role().name()))));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(response);
        }
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("인증이 필요합니다.", null));
    }
}
