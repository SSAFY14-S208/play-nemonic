package com.nemonicworld.common.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.admin.repository.AdminUserRepository;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.logging.StructuredEventLogger;
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
    private static final String ADMIN_LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String ADMIN_API_PATH = "/api/v1/admins";
    private static final String ADMIN_API_PREFIX = "/api/v1/admins/";
    private static final String ADMIN_INQUIRY_API_PATH = "/api/v1/admin/inquiries";
    private static final String ADMIN_INQUIRY_API_PREFIX = "/api/v1/admin/inquiries/";
    private static final String ADMIN_COMMUNITY_MEMO_API_PATH = "/api/v1/admin/community/memos";
    private static final String ADMIN_COMMUNITY_MEMO_API_PREFIX = "/api/v1/admin/community/memos/";
    private static final String GMS_PROMPT_API_PATH = "/api/v1/backoffice/gms/prompts";
    private static final String GMS_PROMPT_API_PREFIX = "/api/v1/backoffice/gms/prompts/";
    private static final String SYSTEM_PARAMETER_API_PATH = "/api/v1/backoffice/system-parameters";
    private static final String SYSTEM_PARAMETER_API_PREFIX = "/api/v1/backoffice/system-parameters/";
    private static final String BACKOFFICE_RELAY_ROOM_API_PATH = "/api/v1/backoffice/relay-rooms";
    private static final String BACKOFFICE_RELAY_ROOM_API_PREFIX = "/api/v1/backoffice/relay-rooms/";
    private static final String BACKOFFICE_FLIPBOOK_ROOM_API_PATH = "/api/v1/backoffice/flipbook-rooms";
    private static final String BACKOFFICE_FLIPBOOK_ROOM_API_PREFIX = "/api/v1/backoffice/flipbook-rooms/";
    private static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다.";

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminTokenStore adminTokenStore;
    private final AdminUserRepository adminUserRepository;
    private final ObjectMapper objectMapper;

    public AdminJwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, AdminTokenStore adminTokenStore,
        AdminUserRepository adminUserRepository, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminTokenStore = adminTokenStore;
        this.adminUserRepository = adminUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = resolveRequestPath(request);

        return !ADMIN_LOGOUT_PATH.equals(servletPath) && !ADMIN_API_PATH.equals(servletPath)
            && !servletPath.startsWith(ADMIN_API_PREFIX) && !ADMIN_INQUIRY_API_PATH.equals(servletPath)
            && !servletPath.startsWith(ADMIN_INQUIRY_API_PREFIX) && !ADMIN_COMMUNITY_MEMO_API_PATH.equals(servletPath)
            && !servletPath.startsWith(ADMIN_COMMUNITY_MEMO_API_PREFIX) && !GMS_PROMPT_API_PATH.equals(servletPath)
            && !servletPath.startsWith(GMS_PROMPT_API_PREFIX) && !SYSTEM_PARAMETER_API_PATH.equals(servletPath)
            && !servletPath.startsWith(SYSTEM_PARAMETER_API_PREFIX)
            && !BACKOFFICE_RELAY_ROOM_API_PATH.equals(servletPath)
            && !servletPath.startsWith(BACKOFFICE_RELAY_ROOM_API_PREFIX)
            && !BACKOFFICE_FLIPBOOK_ROOM_API_PATH.equals(servletPath)
            && !servletPath.startsWith(BACKOFFICE_FLIPBOOK_ROOM_API_PREFIX);
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
            if (adminTokenStore.isAccessTokenRevoked(claims)) {
                throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
            }
            AdminUser adminUser = adminUserRepository.findActiveById(claims.adminId())
                .orElseThrow(() -> new UnauthorizedException(UNAUTHORIZED_MESSAGE));
            AdminPrincipal principal = AdminPrincipal.from(adminUser);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_%s".formatted(principal.role().name()))));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            StructuredEventLogger.auditWarn("admin_token_invalid", "admin token invalid", resolveTraceId(request),
                StructuredEventLogger.metadata("path", resolveRequestPath(request), "method", request.getMethod(),
                    "reason_code", e.getClass().getSimpleName()));
            writeUnauthorizedResponse(response);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Request-Id");
        }

        return traceId;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(UNAUTHORIZED_MESSAGE, null));
    }
}
