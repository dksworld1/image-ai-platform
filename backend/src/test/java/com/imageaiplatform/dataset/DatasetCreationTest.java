package com.imageaiplatform.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.imageaiplatform.auth.service.JwtTokenService;
import com.imageaiplatform.dataset.service.DatasetService;
import jakarta.servlet.Filter;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:dataset-create-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional("appTransactionManager")
class DatasetCreationTest {
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired @Qualifier("appDataSource") private DataSource dataSource;
    @Autowired private JwtTokenService tokens;
    @Autowired private DatasetService datasetService;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private String bearer;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('dataset-user', 'login-dataset-user', 'unused')");
        bearer = "Bearer " + tokens.issue("dataset-user").value();
    }

    @Test
    void createsWithServerIdentityAndOneTimestampIgnoringClientAuditValues() throws Exception {
        var before = OffsetDateTime.now().minusSeconds(1).toInstant();
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json")
                        .content("""
                                {"name":"  학습 데이터  ","description":"  설명  ",
                                 "datasetId":"client-id","createdAt":"2000-01-01T00:00:00Z",
                                 "updatedAt":"2001-01-01T00:00:00Z","createdBy":"0","updatedBy":"other"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("학습 데이터"))
                .andExpect(jsonPath("$.description").value("설명"))
                .andExpect(jsonPath("$.createdBy").value("dataset-user"))
                .andExpect(jsonPath("$.updatedBy").value("dataset-user"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());
        var row = jdbc.queryForMap("SELECT * FROM dataset");
        assertThat(UUID.fromString((String) row.get("DATASET_ID"))).isNotNull();
        assertThat(row.get("CREATED_BY")).isEqualTo("dataset-user");
        assertThat(row.get("UPDATED_BY")).isEqualTo("dataset-user");
        assertThat(row.get("CREATED_AT")).isEqualTo(row.get("UPDATED_AT"));
        var created = (OffsetDateTime) row.get("CREATED_AT");
        assertThat(created.toInstant()).isBetween(before, OffsetDateTime.now().plusSeconds(1).toInstant());
        assertThat(created.getNano() % 1000).isZero();
    }

    @Test
    void acceptsOptionalDescriptionAndDuplicateNamesWithDifferentIds() throws Exception {
        for (String content : new String[] {"{\"name\":\"same\"}", "{\"name\":\"same\",\"description\":\"   \"}"}) {
            mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json").content(content))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.description").isEmpty());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT dataset_id) FROM dataset", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset WHERE description IS NULL", Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsInvalidInputWithoutSaving() throws Exception {
        for (String content : new String[] {"{}", "{\"name\":null}", "{\"name\":\"   \"}",
                "{\"name\":\"" + "a".repeat(201) + "\"}",
                "{\"name\":\"valid\",\"description\":\"" + "a".repeat(2001) + "\"}"}) {
            mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json").content(content))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset", Integer.class)).isZero();
    }

    @Test
    void rejectsMissingInvalidDisabledAndDeletedAuthentication() throws Exception {
        mvc.perform(post("/api/datasets").contentType("application/json").content("{\"name\":\"valid\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/datasets").header("Authorization", "Bearer invalid")
                        .contentType("application/json").content("{\"name\":\"valid\"}"))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE auth_user SET enabled=FALSE, updated_at=CURRENT_TIMESTAMP, updated_by='0' WHERE user_key='dataset-user'");
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json").content("{\"name\":\"valid\"}"))
                .andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM auth_user WHERE user_key='dataset-user'");
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json").content("{\"name\":\"valid\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset", Integer.class)).isZero();
    }

    @Test
    void protectsDirectServiceCallsAndRecordsTheAuthenticatedActor() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> datasetService.create("name", null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("dataset-user", null, java.util.Set.of()));
        SecurityContextHolder.setContext(securityContext);
        try {
            var result = datasetService.create("direct", null);
            var stored = jdbc.queryForMap("SELECT * FROM dataset WHERE dataset_id=?", result.datasetId());
            assertThat(result.createdAt()).isEqualTo(result.updatedAt()).isEqualTo(stored.get("CREATED_AT"));
            assertThat(result.createdBy()).isEqualTo("dataset-user");
            assertThatThrownBy(() -> datasetService.create(" ", null)).isInstanceOf(IllegalArgumentException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset", Integer.class)).isEqualTo(1);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
