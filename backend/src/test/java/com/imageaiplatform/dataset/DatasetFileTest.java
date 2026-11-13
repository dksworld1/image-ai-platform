package com.imageaiplatform.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.imageaiplatform.auth.service.JwtTokenService;
import jakarta.servlet.Filter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:data-file-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class DatasetFileTest {
    private static final Path STORAGE = Path.of("target", "data-file-" + UUID.randomUUID()).toAbsolutePath();
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.files.directory", () -> STORAGE.toString());
        registry.add("app.storage.files.max-file-size", () -> "4KB");
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
        for (String table : new String[] {"dataset_file_history", "dataset_file", "dataset_image_history", "dataset_image", "dataset", "workspace_member", "workspace", "auth_user_role", "auth_role_permission", "auth_role", "auth_user"}) {
            jdbc.update("DELETE FROM " + table);
        }
        Files.createDirectories(STORAGE);
        try (var files = Files.list(STORAGE)) {
            for (Path file : files.toList()) Files.delete(file);
        }
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('uploader', 'Uploader')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:image:upload'), ('uploader', 'dataset:image:update'), ('uploader', 'dataset:image:read'), ('uploader', 'dataset:item:read'), ('uploader', 'dataset:item:delete')");
        for (String permission : new String[] {"read", "upload", "update", "delete"}) jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', ?)", "dataset:file:" + permission);
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
    void storesDownloadsVersionsEditsAndDeletesFilesWithIndependentPermissions() throws Exception {
        byte[] data = "id,value\n1,hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = mvc.perform(multipart("/api/datasets/d1/files").file(file("자료.csv", data)).param("description", "원본 설명")
                        .header("Authorization", bearer("creator")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("원본 설명"))
                .andExpect(jsonPath("$.versionCount").value(1)).andReturn().getResponse();
        var first = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        String firstId = first.get("fileId").asText();
        String firstTime = first.get("firstRegisteredAt").asText();
        mvc.perform(multipart("/api/datasets/d1/files").file(file("자료.csv", data)).header("Authorization", bearer("creator"))).andExpect(status().isConflict());
        mvc.perform(multipart("/api/datasets/d2/files").file(file("자료.csv", data)).header("Authorization", bearer("owner"))).andExpect(status().isCreated());
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader' AND (permission_code LIKE 'dataset:image:%' OR permission_code='dataset:item:read')");
        mvc.perform(get("/api/datasets/file-targets").header("Authorization", bearer("other"))).andExpect(status().isOk());
        mvc.perform(get("/api/datasets/d1/images").header("Authorization", bearer("other"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/datasets/d1/files/" + firstId + "/content").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(content().bytes(data)).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        mvc.perform(multipart("/api/datasets/d1/files").file(file("자료.csv", "new".getBytes())).param("replaceExisting", "true")
                        .header("Authorization", bearer("manager")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(2)).andExpect(jsonPath("$.firstRegisteredAt").value(firstTime));
        String currentId = jdbc.queryForObject("SELECT file_id FROM dataset_file WHERE dataset_id='d1' AND is_deleted=FALSE", String.class);
        mvc.perform(get("/api/datasets/d1/files/" + firstId + "/content").header("Authorization", bearer("other"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/datasets/d1/files/descriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"fileId\":\"" + currentId + "\",\"description\":\"편집\"}]}").header("Authorization", bearer("manager")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("편집")).andExpect(jsonPath("$[0].versionCount").value(2));
        mvc.perform(delete("/api/datasets/d1/files/" + currentId).header("Authorization", bearer("creator"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/datasets/d1/files").header("Authorization", bearer("other"))).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file WHERE dataset_id='d1'", Integer.class)).isEqualTo(2);
        assertThat(fileCount()).isEqualTo(3);
        mvc.perform(delete("/api/datasets/d2").header("Authorization", bearer("owner"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file_history WHERE dataset_id='d2' AND action='DELETE'", Integer.class)).isEqualTo(1);
        assertThat(fileCount()).isEqualTo(3);
    }

    @Test
    void enforcesFileScopesAndSeparateActionsAndAcceptsImagesAsBinary() throws Exception {
        byte[] data = new byte[] {1, 2, 3};
        mvc.perform(multipart("/api/datasets/d1/files").file(file("data.bin", data))).andExpect(status().isUnauthorized());
        for (String user : new String[] {"other", "outsider", "no-permission"}) {
            mvc.perform(multipart("/api/datasets/d1/files").file(file("data.bin", data)).header("Authorization", bearer(user))).andExpect(status().isForbidden());
        }
        for (MockMultipartFile accepted : new MockMultipartFile[] {file("image.PNG", png), file("disguised.bin", png), file("image.svg", "<svg/>".getBytes()), file("README", png), file(".hidden", png), file("archive.tar.gz", png), file("long." + "x".repeat(100), png)}) {
            var response = mvc.perform(multipart("/api/datasets/d1/files").file(accepted).header("Authorization", bearer("creator")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.mediaType").value("application/octet-stream")).andReturn();
            String fileId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("fileId").asText();
            String name = accepted.getOriginalFilename();
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 ? name.substring(dot) : "";
            String storagePath = jdbc.queryForObject("SELECT storage_path FROM dataset_file WHERE file_id=?", String.class, fileId);
            assertThat(storagePath).isEqualTo(fileId + extension);
            mvc.perform(get("/api/datasets/d1/files/" + fileId + "/content").header("Authorization", bearer("creator")))
                    .andExpect(status().isOk()).andExpect(content().bytes(accepted.getBytes()));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image", Integer.class)).isZero();
        assertThat(fileCount()).isEqualTo(7);
        for (MockMultipartFile rejected : new MockMultipartFile[] {file("large.bin", new byte[4097]), file("empty.bin", new byte[0])}) {
            mvc.perform(multipart("/api/datasets/d1/files").file(rejected).header("Authorization", bearer("creator"))).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/datasets/d1/files").file(file("data.bin", data)).header("Authorization", bearer("creator"))).andExpect(status().isCreated());
        String id = jdbc.queryForObject("SELECT file_id FROM dataset_file WHERE original_filename='data.bin'", String.class);
        mvc.perform(get("/api/datasets/d1/files/" + id + "/content").header("Authorization", bearer("outsider"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/datasets/d2/files/" + id + "/content").header("Authorization", bearer("other"))).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader' AND permission_code IN ('dataset:file:upload', 'dataset:file:read')");
        mvc.perform(get("/api/datasets/file-targets").header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/datasets/d1/files").file(file("new.bin", data)).header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/datasets/d1/files").file(file("data.bin", data)).param("replaceExisting", "true").header("Authorization", bearer("creator"))).andExpect(status().isCreated());
        mvc.perform(multipart("/api/datasets/d1/files").file(file("new.bin", data)).param("replaceExisting", "true").header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
    }

    @Test
    void preservesFolderPathsAndReplacesOnlyTheSameRelativePath() throws Exception {
        for (String path : new String[] {"자료/하나/data.csv", "자료/둘/data.csv", "자료/" + "폴더/".repeat(90) + "data.csv", "자료.v1/README"}) {
            mvc.perform(multipart("/api/datasets/d1/files").file(file("data.csv", png)).param("relativePath", path)
                    .header("Authorization", bearer("creator"))).andExpect(status().isCreated()).andExpect(jsonPath("$.originalFilename").value(path));
        }
        mvc.perform(multipart("/api/datasets/d1/files").file(file("data.csv", png)).param("relativePath", "자료/하나/data.csv")
                .header("Authorization", bearer("creator"))).andExpect(status().isConflict());
        mvc.perform(multipart("/api/datasets/d1/files").file(file("data.csv", png)).param("relativePath", "자료\\하나\\data.csv").param("replaceExisting", "true")
                .header("Authorization", bearer("creator"))).andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(2))
                .andExpect(jsonPath("$.originalFilename").value("자료/하나/data.csv"));
        assertThat(jdbc.queryForObject("SELECT version_count FROM dataset_file WHERE original_filename='자료/둘/data.csv'", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dataset_file (file_id,dataset_id,original_filename,storage_path,media_type,size_bytes) VALUES ('duplicate','d1','자료/둘/data.csv','unused','application/octet-stream',1)"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file_history WHERE original_filename='자료/하나/data.csv'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT storage_path FROM dataset_file WHERE original_filename='자료.v1/README'", String.class)).doesNotContain(".").doesNotContain("/");
        for (String path : new String[] {"../data.csv", "/자료/data.csv", "C:/자료/data.csv", "자료//data.csv", "자료/./data.csv", "자료/", "자료/" + "a".repeat(256), "a/".repeat(512) + "x"}) {
            mvc.perform(multipart("/api/datasets/d1/files").file(file("data.csv", png)).param("relativePath", path)
                    .header("Authorization", bearer("creator"))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void rollsBackReplacementAndBulkDescriptionChangesWithoutRemovingOriginalFiles() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/files").file(file("data.bin", new byte[] {1})).header("Authorization", bearer("creator"))).andExpect(status().isCreated());
        var original = jdbc.queryForMap("SELECT * FROM dataset_file");
        jdbc.execute("ALTER TABLE dataset_file_history ADD CONSTRAINT reject_file_update_test CHECK (action <> 'UPDATE')");
        try {
            assertThatThrownBy(() -> mvc.perform(multipart("/api/datasets/d1/files").file(file("data.bin", new byte[] {2})).param("replaceExisting", "true")
                    .header("Authorization", bearer("creator")))).hasRootCauseInstanceOf(java.sql.SQLException.class);
            assertThat(jdbc.queryForMap("SELECT * FROM dataset_file")).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file_history", Integer.class)).isEqualTo(1);
            assertThat(fileCount()).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE dataset_file_history DROP CONSTRAINT reject_file_update_test");
        }
        mvc.perform(patch("/api/datasets/d1/files/descriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"fileId\":\"" + original.get("FILE_ID") + "\",\"description\":\"취소\"},{\"fileId\":\"missing\"}]}")
                        .header("Authorization", bearer("creator"))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForMap("SELECT * FROM dataset_file")).isEqualTo(original);
    }

    private MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("file", name, "application/octet-stream", bytes); }
    private String bearer(String user) { return "Bearer " + tokens.issue(user).value(); }
    private long fileCount() throws Exception { try (var files = Files.list(STORAGE)) { return files.count(); } }
}
