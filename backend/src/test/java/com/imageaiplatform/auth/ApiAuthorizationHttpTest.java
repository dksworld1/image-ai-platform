package com.imageaiplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.imageaiplatform.auth.config.ApiAuthorizationInterceptor;
import com.imageaiplatform.auth.controller.PublicApi;
import com.imageaiplatform.auth.controller.RequirePermission;
import com.imageaiplatform.auth.domain.AuthUser;
import com.imageaiplatform.auth.service.AuthorizationService;
import com.imageaiplatform.auth.service.JwtTokenService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.RequestDispatcher;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:api-authorization;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Sql(scripts = "classpath:fixtures/auth-user.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
        config = @SqlConfig(dataSource = "appDataSource", transactionManager = "appTransactionManager"))
@Import(ApiAuthorizationHttpTest.TestEndpoints.class)
@Transactional("appTransactionManager")
class ApiAuthorizationHttpTest {
    private static final String SECRET = "test-only-signing-key-not-for-real-use-32-bytes";
    private static final String ORIGIN = "http://localhost:5173";

    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired @Qualifier("appDataSource") private DataSource dataSource;
    @Autowired private JwtTokenService tokens;
    @Autowired private TestEndpoints endpoints;

    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private String bearer;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO auth_user (username, password_hash) VALUES ('api-user', 'unused')");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('API_READER', 'Test reader')");
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:read', 'Read')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('API_READER', 'dataset:item:read')");
        bearer = "Bearer " + tokens.issue("api-user").value();
        endpoints.executions.set(0);
    }

    @Test
    void publicApisRemainAvailableAndLoginChecksCredentials() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/test/public")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/token").contentType("application/json")
                        .content("{\"username\":\"missing\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationOnlyAllowsActiveAccountWithoutRoles() throws Exception {
        mvc.perform(get("/api/test/auth")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test/auth").header("Authorization", bearer)).andExpect(status().isOk());
        jdbc.update("UPDATE auth_user SET enabled = FALSE WHERE username = 'api-user'");
        mvc.perform(get("/api/test/auth").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM auth_user WHERE username = 'api-user'");
        mvc.perform(get("/api/test/auth").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        assertThat(endpoints.executions.get()).isEqualTo(1);
    }

    @Test
    void permissionRequiresAuthenticationThenExplicitGrantAndReflectsRevocation() throws Exception {
        mvc.perform(get("/api/test/permission")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test/permission").header("Authorization", bearer)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO auth_user_role (username, role_code) VALUES ('api-user', 'API_READER')");
        mvc.perform(get("/api/test/permission").header("Authorization", bearer)).andExpect(status().isOk());
        jdbc.update("DELETE FROM auth_user_role WHERE username = 'api-user'");
        mvc.perform(get("/api/test/permission").header("Authorization", bearer)).andExpect(status().isForbidden());
        assertThat(endpoints.executions.get()).isEqualTo(1);
    }

    @Test
    void invalidExpiredAndUnknownAccountTokensAreUnauthorized() throws Exception {
        // 만료 시점을 고정해 시간 대기 없이 검증합니다.
        String expired = io.jsonwebtoken.Jwts.builder().subject("api-user")
                .expiration(new java.util.Date(0))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        for (String token : new String[] {"invalid", expired, tokens.issue("missing-account").value(), tokens.issue(" ").value()}) {
            mvc.perform(get("/api/test/auth").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(endpoints.executions.get()).isZero();
    }

    @Test
    void missingPolicyWrongMethodAndUnmappedHandlersDoNotExecuteWork() throws Exception {
        mvc.perform(post("/api/test/permission").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/test/missing").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/not-registered").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/error").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(options("/api/test/auth").header("Authorization", bearer)).andExpect(status().isForbidden());
        assertThat(endpoints.executions.get()).isZero();
    }

    @Test
    void corsPreflightIsHandledBeforeAuthenticationAndRealRequestsStillNeedPermission() throws Exception {
        mvc.perform(options("/api/test/permission").header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        mvc.perform(options("/api/test/permission").header("Origin", "https://unlisted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/test/permission").header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        mvc.perform(get("/api/test/permission").header("Origin", ORIGIN).header("Authorization", bearer))
                .andExpect(status().isForbidden()).andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        assertThat(endpoints.executions.get()).isZero();
    }

    @Test
    void headPreservesGetPolicyAndAsyncRedispatchRechecksAccount() throws Exception {
        mvc.perform(head("/api/test/auth")).andExpect(status().isUnauthorized());
        mvc.perform(head("/api/test/auth").header("Authorization", bearer)).andExpect(status().isOk());
        var allowed = mvc.perform(get("/api/test/async").header("Authorization", bearer))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(allowed)).andExpect(status().isOk()).andExpect(content().string("async"));
        var result = mvc.perform(get("/api/test/async").header("Authorization", bearer))
                .andExpect(request().asyncStarted()).andReturn();
        jdbc.update("UPDATE auth_user SET enabled = FALSE WHERE username = 'api-user'");
        mvc.perform(asyncDispatch(result)).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyInternalErrorDispatchCanRenderErrorResponse() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void databaseFailureReturnsServerErrorWithoutExecutingController() throws Exception {
        var service = new AuthorizationService(
                username -> new AuthUser(username, "unused", true),
                username -> { throw new DataRetrievalFailureException("Internal database details"); });
        var standalone = MockMvcBuilders.standaloneSetup(endpoints)
                .addInterceptors(new ApiAuthorizationInterceptor(service)).build();
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("api-user", null, Set.of()));
        SecurityContextHolder.setContext(securityContext);
        try {
            standalone.perform(get("/api/test/permission"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().json("{\"error\":\"INTERNAL_SERVER_ERROR\"}"));
            assertThat(endpoints.executions.get()).isZero();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @RestController
    @TestComponent
    static class TestEndpoints {
        final AtomicInteger executions = new AtomicInteger();

        @GetMapping("/api/test/public") @PublicApi
        public String publicApi() { return "public"; }

        @GetMapping("/api/test/auth") @RequirePermission
        public String authenticated() { executions.incrementAndGet(); return "ok"; }

        @GetMapping("/api/test/permission") @RequirePermission("dataset:item:read")
        public String permission() { executions.incrementAndGet(); return "ok"; }

        @PostMapping("/api/test/permission")
        public String wrongMethod() { executions.incrementAndGet(); return "wrong"; }

        @GetMapping("/api/test/missing")
        public String missing() { executions.incrementAndGet(); return "missing"; }

        @GetMapping("/api/test/async") @RequirePermission
        public Callable<String> async() { return () -> "async"; }
    }
}
