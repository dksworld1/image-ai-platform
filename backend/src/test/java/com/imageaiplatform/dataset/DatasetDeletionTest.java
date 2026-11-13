package com.imageaiplatform.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.imageaiplatform.auth.service.JwtTokenService;
import com.imageaiplatform.dataset.service.DatasetService;
import jakarta.servlet.Filter;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:dataset-delete-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional("appTransactionManager")
class DatasetDeletionTest {
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter filter;
    @Autowired @Qualifier("appDataSource") private DataSource dataSource;
    @Autowired private JwtTokenService tokens;
    @Autowired private DatasetService service;
    private MockMvc mvc;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('deleter', 'Deletion')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('deleter', 'dataset:item:delete')");
        for (String user : new String[] {"owner", "manager", "creator", "other", "outsider", "no-permission"}) {
            jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES (?, ?, 'unused')", user, "login-" + user);
            if (!user.equals("no-permission")) {
                jdbc.update("INSERT INTO auth_user_role (user_key, role_code) VALUES (?, 'deleter')", user);
            }
        }
        jdbc.update("INSERT INTO workspace (workspace_id, name, owner_user_key) VALUES ('a', '공간 A', 'owner'), ('b', '공간 B', 'outsider')");
        for (String user : new String[] {"owner", "manager", "creator", "other", "no-permission"}) {
            jdbc.update("INSERT INTO workspace_member (workspace_id, user_key, role_code, status, joined_at) VALUES ('a', ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)",
                    user, user.equals("owner") || user.equals("manager") ? "ADMIN" : "MEMBER");
        }
        jdbc.update("INSERT INTO workspace_member (workspace_id, user_key, role_code, status, joined_at) VALUES ('b', 'outsider', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO dataset (dataset_id, name, created_by, workspace_id) VALUES ('target', '삭제 대상', 'creator', 'a'), ('keep', '다른 공간 독립 데이터', 'creator', 'b')");
    }

    @Test
    void allowsActiveCreatorAndLocalOperatorsWithoutDeletingIndependentDatasets() throws Exception {
        jdbc.update("INSERT INTO dataset_image (image_id, dataset_id, original_filename, storage_path, media_type, size_bytes) VALUES ('image', 'target', 'a.png', 'a.png', 'image/png', 1)");
        mvc.perform(delete("/api/datasets/target").header("Authorization", bearer("creator"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image WHERE dataset_id='target'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT created_by FROM dataset_image_history WHERE image_id='image' AND action='DELETE'", String.class)).isEqualTo("creator");
        for (String operator : new String[] {"owner", "manager"}) {
            jdbc.update("INSERT INTO dataset (dataset_id, name, created_by, workspace_id) VALUES (?, '다른 생성자 데이터', 'creator', 'a')", operator);
            mvc.perform(delete("/api/datasets/" + operator).header("Authorization", bearer(operator))).andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForList("SELECT dataset_id FROM dataset", String.class)).containsExactly("keep");
        mvc.perform(delete("/api/datasets/target").header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
    }

    @Test
    void deniesNonCreatorsNonMembersInactiveMembershipsAndMissingFunctionPermission() throws Exception {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.delete("target")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        mvc.perform(delete("/api/datasets/target")).andExpect(status().isUnauthorized());
        for (String denied : new String[] {"other", "outsider", "no-permission"}) {
            mvc.perform(delete("/api/datasets/target").header("Authorization", bearer(denied))).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE dataset SET created_by='no-permission' WHERE dataset_id='target'");
        mvc.perform(delete("/api/datasets/target").header("Authorization", bearer("no-permission"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE dataset SET created_by='creator' WHERE dataset_id='target'");
        for (String inactive : new String[] {"LEFT", "REMOVED"}) {
            jdbc.update("UPDATE workspace_member SET status=? WHERE workspace_id='a'", inactive);
            for (String actor : new String[] {"creator", "owner", "manager"}) {
                mvc.perform(delete("/api/datasets/target").header("Authorization", bearer(actor))).andExpect(status().isForbidden());
            }
        }
        jdbc.update("UPDATE workspace_member SET status='PENDING', joined_at=NULL, requested_at=CURRENT_TIMESTAMP WHERE workspace_id='a'");
        mvc.perform(delete("/api/datasets/target").header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE workspace_member SET status='ACTIVE', joined_at=CURRENT_TIMESTAMP WHERE workspace_id='a'");
        jdbc.update("UPDATE workspace SET status='ARCHIVED' WHERE workspace_id='a'");
        mvc.perform(delete("/api/datasets/target").header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE workspace SET status='ACTIVE' WHERE workspace_id='a'");
        jdbc.update("UPDATE auth_user SET enabled=FALSE WHERE user_key='creator'");
        mvc.perform(delete("/api/datasets/target").header("Authorization", bearer("creator"))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset", Integer.class)).isEqualTo(2);
    }

    private String bearer(String userKey) {
        return "Bearer " + tokens.issue(userKey).value();
    }
}
