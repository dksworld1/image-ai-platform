package com.imageaiplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.service.AuthService;
import com.imageaiplatform.auth.service.AuthorizationService;
import com.imageaiplatform.auth.service.JwtTokenService;
import jakarta.servlet.Filter;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:user-identity-contract;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional("appTransactionManager")
class UserIdentityTest {
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter filter;
    @Autowired @Qualifier("appDataSource") private DataSource source;
    @Autowired private AuthService auth;
    @Autowired private JwtTokenService tokens;
    @Autowired private AuthorizationService authorization;
    @Autowired private AuthUserDao users;
    @Autowired private PasswordEncoder passwords;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(source);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        jdbc.update("INSERT INTO auth_user (user_key, user_id, username, password_hash) VALUES (?, ?, ?, ?)",
                "internal-a", "alice-login", "홍길동", passwords.encode("password"));
    }

    @Test
    void logsInOnlyByUserIdAndReturnsSeparateIdentityFields() throws Exception {
        mvc.perform(post("/api/auth/token").contentType("application/json")
                        .content("{\"userId\":\"alice-login\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userKey").value("internal-a"))
                .andExpect(jsonPath("$.userId").value("alice-login"))
                .andExpect(jsonPath("$.username").value("홍길동"));
        assertThat(tokens.parseSubject(auth.login("alice-login", "password").token().value())).isEqualTo("internal-a");
        assertThatThrownBy(() -> auth.login("홍길동", "password")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> auth.login("internal-a", "password")).isInstanceOf(BadCredentialsException.class);
        mvc.perform(post("/api/auth/token").contentType("application/json")
                        .content("{\"username\":\"alice-login\",\"password\":\"password\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keepsRolesTokensAndAuditKeysAfterLoginIdAndRealNameChange() throws Exception {
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('reader', 'Reader')");
        jdbc.update("INSERT INTO auth_user_role (user_key, role_code) VALUES ('internal-a', 'reader')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('reader', 'dataset:item:read')");
        String bearer = "Bearer " + auth.login("alice-login", "password").token().value();
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json").content("{\"name\":\"before\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdBy").value("internal-a"));
        jdbc.update("UPDATE auth_user SET user_id='renamed-login', username='김철수', updated_at=CURRENT_TIMESTAMP, updated_by='internal-a' WHERE user_key='internal-a'");
        authorization.requirePermission("internal-a", "dataset:item:read");
        assertThat(users.findByUserId("alice-login")).isNull();
        assertThat(users.findByUserId("renamed-login").userKey()).isEqualTo("internal-a");
        assertThat(users.findByUserKey("internal-a").username()).isEqualTo("김철수");
        assertThat(tokens.parseSubject(auth.login("renamed-login", "password").token().value())).isEqualTo("internal-a");
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json").content("{\"name\":\"after\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdBy").value("internal-a"));
        assertThat(jdbc.queryForList("SELECT DISTINCT created_by FROM dataset", String.class)).containsExactly("internal-a");
        assertThat(jdbc.queryForList("SELECT DISTINCT updated_by FROM dataset", String.class)).containsExactly("internal-a");
    }

    @Test
    void allowsDuplicateOrUnknownRealNamesButRequiresUniqueLoginIdsAndNonSystemKeys() throws Exception {
        jdbc.update("INSERT INTO auth_user (user_key, user_id, username, password_hash) VALUES ('internal-b', 'bob-login', '홍길동', 'unused')");
        jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('internal-c', 'carol-login', ?)",
                passwords.encode("password"));
        assertThat(users.findByUserKey("internal-c").username()).isNull();
        mvc.perform(post("/api/auth/token").contentType("application/json")
                        .content("{\"userId\":\"carol-login\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").hasJsonPath())
                .andExpect(jsonPath("$.username").isEmpty());
        for (String sql : new String[] {
                "INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('internal-d', 'alice-login', 'unused')",
                "INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('internal-d', NULL, 'unused')",
                "INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('0', 'system-login', 'unused')"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
