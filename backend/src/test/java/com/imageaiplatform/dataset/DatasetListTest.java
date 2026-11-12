package com.imageaiplatform.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.imageaiplatform.auth.service.JwtTokenService;
import jakarta.servlet.Filter;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:dataset-workspace-list;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional("appTransactionManager")
class DatasetListTest {
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter filter;
    @Autowired @Qualifier("appDataSource") private DataSource dataSource;
    @Autowired private JwtTokenService tokens;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private String bearer;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('viewer', 'viewer-login', 'test'), ('other', 'other-login', 'test')");
        jdbc.update("UPDATE auth_user SET username='등록 담당자' WHERE user_key='other'");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('dataset-reader', 'Reader')");
        jdbc.update("INSERT INTO auth_user_role (user_key, role_code) VALUES ('viewer', 'dataset-reader')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('dataset-reader', 'dataset:item:read'), ('dataset-reader', 'dataset:item:create')");
        jdbc.update("INSERT INTO workspace (workspace_id, name, owner_user_key) VALUES ('a', '공간 A', 'other'), ('b', '공간 B', 'other'), ('hidden', '비공개 공간', 'other'), ('archived', '보관 공간', 'other')");
        jdbc.update("UPDATE workspace SET status='ARCHIVED' WHERE workspace_id='archived'");
        jdbc.update("INSERT INTO workspace_member (workspace_id, user_key, status, joined_at) VALUES ('a', 'viewer', 'ACTIVE', CURRENT_TIMESTAMP), ('b', 'viewer', 'ACTIVE', CURRENT_TIMESTAMP), ('archived', 'viewer', 'ACTIVE', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO workspace_member (workspace_id, user_key, requested_at) VALUES ('hidden', 'viewer', CURRENT_TIMESTAMP)");
        bearer = "Bearer " + tokens.issue("viewer").value();
    }

    @Test
    void detailRequiresReadPermissionAndCurrentActiveMembership() throws Exception {
        jdbc.update("INSERT INTO dataset (dataset_id, name, description, created_by, workspace_id) VALUES ('detail-a', '상세 데이터', '상세 설명', 'other', 'a')");
        mvc.perform(get("/api/datasets/detail-a")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/datasets/detail-a").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datasetId").value("detail-a"))
                .andExpect(jsonPath("$.description").value("상세 설명"))
                .andExpect(jsonPath("$.workspaceName").value("공간 A"))
                .andExpect(jsonPath("$.creatorUserId").value("other-login"))
                .andExpect(jsonPath("$.creatorUsername").value("등록 담당자"));
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='dataset-reader' AND permission_code='dataset:item:read'");
        mvc.perform(get("/api/datasets/detail-a").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void detailRejectsNonMembersInactiveMembershipsArchivedSpacesAndUnknownIds() throws Exception {
        jdbc.update("INSERT INTO dataset (dataset_id, name, created_by, workspace_id) VALUES ('detail-a', '상세', 'viewer', 'a'), ('hidden-detail', '숨김', 'viewer', 'hidden'), ('archived-detail', '보관', 'viewer', 'archived'), ('no-space', '미귀속', 'viewer', NULL)");
        for (String id : new String[] {"hidden-detail", "archived-detail", "no-space", "missing"}) {
            mvc.perform(get("/api/datasets/" + id).param("userKey", "other").header("Authorization", bearer))
                    .andExpect(status().isForbidden());
        }
        for (String state : new String[] {"PENDING", "LEFT", "REMOVED"}) {
            jdbc.update("UPDATE workspace_member SET status=?, requested_at=CURRENT_TIMESTAMP, joined_at=CASE WHEN ?='PENDING' THEN NULL ELSE CURRENT_TIMESTAMP END WHERE user_key='viewer' AND workspace_id='a'", state, state);
            mvc.perform(get("/api/datasets/detail-a").header("Authorization", bearer)).andExpect(status().isForbidden());
        }
        jdbc.update("DELETE FROM workspace_member WHERE user_key='viewer' AND workspace_id='a'");
        mvc.perform(get("/api/datasets/detail-a").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void createsIndependentDatasetsPerSpaceAndListsOnlyCurrentMemberships() throws Exception {
        mvc.perform(get("/api/workspaces").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json")
                        .content("{\"name\":\"공유 데이터\",\"workspaceIds\":[\"a\",\"b\",\"a\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].workspaceId").value("a")).andExpect(jsonPath("$[1].workspaceId").value("b"));
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT dataset_id) FROM dataset", Integer.class)).isEqualTo(2);
        var copies = jdbc.queryForList("SELECT created_at, updated_at, created_by, updated_by FROM dataset");
        assertThat(copies.get(0)).isEqualTo(copies.get(1));
        jdbc.update("UPDATE dataset SET name='공간 A에서 수정' WHERE workspace_id='a'");
        assertThat(jdbc.queryForObject("SELECT name FROM dataset WHERE workspace_id='b'", String.class)).isEqualTo("공유 데이터");
        jdbc.update("INSERT INTO dataset (dataset_id, name, created_at, created_by, workspace_id) VALUES ('old', '다른 생성자의 데이터', TIMESTAMP WITH TIME ZONE '2020-01-01 00:00:00Z', 'other', 'a'), ('unassigned', '미귀속', CURRENT_TIMESTAMP, 'viewer', NULL), ('hidden-data', '숨김', CURRENT_TIMESTAMP, 'viewer', 'hidden'), ('archived-data', '보관', CURRENT_TIMESTAMP, 'viewer', 'archived')");
        mvc.perform(get("/api/datasets").param("userKey", "other").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].workspaceId").isString())
                .andExpect(jsonPath("$[0].creatorUserId").value("viewer-login"))
                .andExpect(jsonPath("$[0].creatorUsername").isEmpty())
                .andExpect(jsonPath("$[2].datasetId").value("old"))
                .andExpect(jsonPath("$[2].creatorUserId").value("other-login"))
                .andExpect(jsonPath("$[2].creatorUsername").value("등록 담당자"));
        jdbc.update("UPDATE dataset SET created_by='deleted-user-key' WHERE dataset_id='old'");
        mvc.perform(get("/api/datasets").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].creatorUserId").isEmpty()).andExpect(jsonPath("$[2].creatorUsername").isEmpty());
        jdbc.update("UPDATE workspace_member SET status='LEFT' WHERE user_key='viewer' AND workspace_id='a'");
        mvc.perform(get("/api/datasets").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workspaceId").value("b"));
        jdbc.update("UPDATE workspace_member SET status='REMOVED' WHERE user_key='viewer' AND workspace_id='b'");
        mvc.perform(get("/api/datasets").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void requiresExplicitPermissionsAndEverySelectedMembershipWithoutPartialCreation() throws Exception {
        mvc.perform(get("/api/datasets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspaces")).andExpect(status().isUnauthorized());
        for (String spaces : new String[] {"[\"a\",\"hidden\"]", "[\"a\",\"archived\"]", "[\"missing\"]"}) {
            mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json")
                            .content("{\"name\":\"거부\",\"workspaceIds\":" + spaces + "}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json")
                        .content("{\"name\":\"공간 없음\",\"workspaceIds\":[]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset", Integer.class)).isZero();
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='dataset-reader'");
        mvc.perform(get("/api/datasets").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/datasets").header("Authorization", bearer).contentType("application/json")
                        .content("{\"name\":\"권한 없음\",\"workspaceIds\":[\"a\"]}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE auth_user SET enabled=FALSE WHERE user_key='viewer'");
        mvc.perform(get("/api/datasets").header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }
}
