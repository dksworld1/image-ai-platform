package com.imageaiplatform.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.imageaiplatform.auth.service.JwtTokenService;
import jakarta.servlet.Filter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:image-upload-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class DatasetImageUploadTest {
    private static final Path STORAGE = Path.of("target", "image-upload-" + UUID.randomUUID()).toAbsolutePath();
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.images.directory", () -> STORAGE.toString());
    }
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired @Qualifier("appDataSource") private DataSource dataSource;
    @Autowired private JwtTokenService tokens;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private byte[] png;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[] {"dataset_image", "dataset", "workspace_member", "workspace", "auth_user_role", "auth_role_permission", "auth_role", "auth_user"}) {
            jdbc.update("DELETE FROM " + table);
        }
        Files.createDirectories(STORAGE);
        try (var files = Files.list(STORAGE)) {
            for (Path file : files.toList()) Files.delete(file);
        }
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('uploader', 'Uploader')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:image:upload'), ('uploader', 'dataset:item:read')");
        for (String user : new String[] {"owner", "manager", "creator", "other", "outsider", "no-permission"}) {
            jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES (?, ?, 'unused')", user, "login-" + user);
            if (!user.equals("no-permission")) jdbc.update("INSERT INTO auth_user_role (user_key, role_code) VALUES (?, 'uploader')", user);
        }
        jdbc.update("INSERT INTO workspace (workspace_id, name, owner_user_key) VALUES ('a', '공간 A', 'owner'), ('b', '공간 B', 'outsider')");
        for (String user : new String[] {"owner", "manager", "creator", "other", "no-permission"}) {
            jdbc.update("INSERT INTO workspace_member (workspace_id, user_key, role_code, status, joined_at) VALUES ('a', ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)",
                    user, user.equals("owner") || user.equals("manager") ? "ADMIN" : "MEMBER");
        }
        jdbc.update("INSERT INTO dataset (dataset_id, name, workspace_id, created_by) VALUES ('d1', '데이터 1', 'a', 'creator'), ('d2', '데이터 2', 'a', 'creator'), ('d3', '권한 없음', 'a', 'no-permission')");
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        png = output.toByteArray();
    }

    @Test
    void storesOriginalFilenameAndBytesRejectsDatasetDuplicatesAndAllowsOtherDatasets() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/images").file(file("C:\\fakepath\\원본.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.originalFilename").value("원본.png"))
                .andExpect(jsonPath("$.mediaType").value("image/png")).andExpect(jsonPath("$.createdBy").value("creator"));
        var row = jdbc.queryForMap("SELECT * FROM dataset_image");
        assertThat(row.get("CREATED_AT")).isEqualTo(row.get("UPDATED_AT"));
        assertThat(row.get("CREATED_BY")).isEqualTo(row.get("UPDATED_BY"));
        assertThat(Files.readAllBytes(STORAGE.resolve((String) row.get("STORAGE_PATH")))).isEqualTo(png);
        mvc.perform(multipart("/api/datasets/d1/images").file(file("원본.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isConflict());
        mvc.perform(multipart("/api/datasets/d2/images").file(file("원본.png", png)).header("Authorization", bearer("owner")))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/datasets/d1/images").file(file("운영자.png", png)).header("Authorization", bearer("manager")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/datasets/d1/images").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dataset_image (image_id, dataset_id, original_filename, storage_path, media_type, size_bytes) VALUES ('duplicate', 'd1', '원본.png', 'unused', 'image/png', 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(fileCount()).isEqualTo(3);
        jdbc.update("DELETE FROM dataset WHERE dataset_id='d1'");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image WHERE dataset_id='d1'", Integer.class)).isZero();
    }

    @Test
    void rejectsInvalidContentAndUnauthorizedUploadsWithoutStoringFiles() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/images").file(file("a.png", png))).andExpect(status().isUnauthorized());
        for (String user : new String[] {"other", "outsider"}) {
            mvc.perform(multipart("/api/datasets/d1/images").file(file("a.png", png)).header("Authorization", bearer(user)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/datasets/d3/images").file(file("a.png", png)).header("Authorization", bearer("no-permission")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/datasets/d1/images").file(file("가짜.png", new byte[] {1, 2, 3})).header("Authorization", bearer("creator")))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE workspace_member SET status='LEFT' WHERE user_key='creator'");
        mvc.perform(multipart("/api/datasets/d1/images").file(file("a.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image", Integer.class)).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    void removesWrittenFileWhenDatabaseInsertRollsBack() throws Exception {
        jdbc.execute("ALTER TABLE dataset_image ADD CONSTRAINT reject_upload_test CHECK (original_filename <> '거부.png')");
        try {
            assertThatThrownBy(() -> mvc.perform(multipart("/api/datasets/d1/images").file(file("거부.png", png))
                    .header("Authorization", bearer("creator")))).hasRootCauseInstanceOf(java.sql.SQLException.class);
            assertThat(fileCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE dataset_image DROP CONSTRAINT reject_upload_test");
        }
    }

    private MockMultipartFile file(String name, byte[] contents) {
        return new MockMultipartFile("file", name, "application/octet-stream", contents);
    }

    private String bearer(String userKey) { return "Bearer " + tokens.issue(userKey).value(); }

    private long fileCount() throws Exception {
        try (var files = Files.list(STORAGE)) { return files.count(); }
    }
}
