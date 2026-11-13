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
        for (String table : new String[] {"dataset_image_history", "dataset_image", "dataset", "workspace_member", "workspace", "auth_user_role", "auth_role_permission", "auth_role", "auth_user"}) {
            jdbc.update("DELETE FROM " + table);
        }
        Files.createDirectories(STORAGE);
        try (var files = Files.list(STORAGE)) {
            for (Path file : files.toList()) Files.delete(file);
        }
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('uploader', 'Uploader')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:image:upload'), ('uploader', 'dataset:image:update'), ('uploader', 'dataset:image:read'), ('uploader', 'dataset:item:read'), ('uploader', 'dataset:item:delete')");
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
    void storesUploadDescriptionsAndAtomicallyUpdatesSelectedMetadataWithoutNewVersions() throws Exception {
        for (String name : new String[] {"one.png", "two.png", "keep.png"}) {
            mvc.perform(multipart("/api/datasets/d1/images").file(file(name, png)).param("description", "원래 " + name)
                            .header("Authorization", bearer("creator")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("원래 " + name));
        }
        mvc.perform(multipart("/api/datasets/d1/images").file(file("one.png", png)).param("replaceExisting", "true")
                        .param("description", "교체 설명").header("Authorization", bearer("manager")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("교체 설명"))
                .andExpect(jsonPath("$.versionCount").value(2));
        var first = jdbc.queryForMap("SELECT * FROM dataset_image WHERE original_filename='one.png' AND is_deleted=FALSE");
        String secondId = jdbc.queryForObject("SELECT image_id FROM dataset_image WHERE original_filename='two.png'", String.class);
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader' AND permission_code <> 'dataset:image:update'");
        String body = "{\"items\":[{\"imageId\":\"" + first.get("IMAGE_ID") + "\",\"description\":\"편집한 설명\"},{\"imageId\":\"" + secondId + "\",\"description\":null}]}";
        mvc.perform(patch("/api/datasets/d1/images/descriptions").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", bearer("creator")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].description").value("편집한 설명"))
                .andExpect(jsonPath("$[0].versionCount").value(2)).andExpect(jsonPath("$[0].updatedBy").value("creator"))
                .andExpect(jsonPath("$[1].description").isEmpty());
        var edited = jdbc.queryForMap("SELECT * FROM dataset_image WHERE image_id=?", first.get("IMAGE_ID"));
        for (String field : new String[] {"CREATED_AT", "CREATED_BY", "VERSION_COUNT", "STORAGE_PATH", "THUMBNAIL_PATH"}) {
            assertThat(edited.get(field)).isEqualTo(first.get(field));
        }
        assertThat(jdbc.queryForObject("SELECT description FROM dataset_image WHERE original_filename='keep.png'", String.class)).isEqualTo("원래 keep.png");
        assertThat(jdbc.queryForObject("SELECT description FROM dataset_image_history WHERE image_id=? AND created_by='creator' AND action='UPDATE'", String.class, first.get("IMAGE_ID"))).isEqualTo("편집한 설명");
        assertThat(fileCount()).isEqualTo(8);
    }

    @Test
    void rejectsInvalidDescriptionBatchesAndRollsBackEveryRowWhenOneHistoryInsertFails() throws Exception {
        for (String name : new String[] {"one.png", "two.png"}) {
            mvc.perform(multipart("/api/datasets/d1/images").file(file(name, png)).param("description", "원래 설명")
                            .header("Authorization", bearer("creator"))).andExpect(status().isCreated());
        }
        var original = jdbc.queryForList("SELECT * FROM dataset_image ORDER BY image_id");
        String first = (String) original.get(0).get("IMAGE_ID");
        String second = (String) original.get(1).get("IMAGE_ID");
        String body = "{\"items\":[{\"imageId\":\"" + first + "\",\"description\":\"수정\"}]}";
        mvc.perform(patch("/api/datasets/d1/images/descriptions").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for (String user : new String[] {"other", "outsider", "no-permission"}) {
            mvc.perform(patch("/api/datasets/d1/images/descriptions").contentType(MediaType.APPLICATION_JSON).content(body)
                            .header("Authorization", bearer(user))).andExpect(status().isForbidden());
        }
        for (String invalid : new String[] {"{\"items\":[]}",
                "{\"items\":[{\"imageId\":\"" + first + "\",\"description\":\"" + "x".repeat(2001) + "\"}]}",
                "{\"items\":[{\"imageId\":\"" + first + "\"},{\"imageId\":\"" + first + "\"}]}"}) {
            mvc.perform(patch("/api/datasets/d1/images/descriptions").contentType(MediaType.APPLICATION_JSON).content(invalid)
                            .header("Authorization", bearer("creator"))).andExpect(status().isBadRequest());
        }
        String missing = "{\"items\":[{\"imageId\":\"" + first + "\",\"description\":\"취소됨\"},{\"imageId\":\"missing\",\"description\":\"없음\"}]}";
        mvc.perform(patch("/api/datasets/d1/images/descriptions").contentType(MediaType.APPLICATION_JSON).content(missing)
                        .header("Authorization", bearer("creator"))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("SELECT * FROM dataset_image ORDER BY image_id")).isEqualTo(original);
        jdbc.execute("ALTER TABLE dataset_image_history ADD CONSTRAINT reject_description_test CHECK (description IS NULL OR description <> '거부')");
        try {
            String failed = "{\"items\":[{\"imageId\":\"" + first + "\",\"description\":\"취소됨\"},{\"imageId\":\"" + second + "\",\"description\":\"거부\"}]}";
            assertThatThrownBy(() -> mvc.perform(patch("/api/datasets/d1/images/descriptions").contentType(MediaType.APPLICATION_JSON)
                    .content(failed).header("Authorization", bearer("creator")))).hasRootCauseInstanceOf(java.sql.SQLException.class);
            assertThat(jdbc.queryForList("SELECT * FROM dataset_image ORDER BY image_id")).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history", Integer.class)).isEqualTo(2);
            assertThat(fileCount()).isEqualTo(4);
        } finally {
            jdbc.execute("ALTER TABLE dataset_image_history DROP CONSTRAINT reject_description_test");
        }
    }

    @Test
    void keepsVersionOneRegistrationTimeAcrossReplacementsAndSeparatesSameNameLineages() throws Exception {
        var created = mvc.perform(multipart("/api/datasets/d1/images").file(file("최초.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var initial = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getContentAsString());
        assertThat(initial.get("firstRegisteredAt").asText()).isEqualTo(initial.get("createdAt").asText());
        String firstTimestamp = "2020-01-01T00:00:00Z";
        var firstTime = java.time.OffsetDateTime.parse(firstTimestamp);
        jdbc.update("UPDATE dataset_image SET created_at=? WHERE original_filename='최초.png'", firstTime);
        jdbc.update("INSERT INTO dataset_image (image_id, dataset_id, original_filename, storage_path, media_type, size_bytes, is_deleted, created_at) VALUES ('unrelated-old', 'd1', '최초.png', 'old.png', 'image/png', 1, TRUE, TIMESTAMP WITH TIME ZONE '1999-01-01 00:00:00+00:00'), ('other-dataset', 'd2', '최초.png', 'other.png', 'image/png', 1, FALSE, TIMESTAMP WITH TIME ZONE '2010-01-01 00:00:00+00:00')");
        for (int version = 2; version <= 3; version++) {
            mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true").file(file("최초.png", png))
                            .header("Authorization", bearer("creator")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(version))
                    .andExpect(jsonPath("$.firstRegisteredAt").value(firstTimestamp))
                    .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.not(firstTimestamp)));
        }
        mvc.perform(get("/api/datasets/d1/images").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].firstRegisteredAt").value(firstTimestamp));
    }

    @Test
    void deletesImagesLogicallyWithDeletePermissionAndRetainsFilesAndAudit() throws Exception {
        for (String name : new String[] {"관리자.png", "오너.png", "생성자.png"}) {
            mvc.perform(multipart("/api/datasets/d1/images").file(file(name, png)).header("Authorization", bearer("creator")))
                    .andExpect(status().isCreated());
        }
        var original = jdbc.queryForMap("SELECT * FROM dataset_image WHERE original_filename='관리자.png'");
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader'");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:image:delete')");
        String[] users = {"manager", "owner", "creator"};
        String[] filenames = {"관리자.png", "오너.png", "생성자.png"};
        for (int i = 0; i < users.length; i++) {
            String imageId = jdbc.queryForObject("SELECT image_id FROM dataset_image WHERE original_filename=?", String.class, filenames[i]);
            mvc.perform(delete("/api/datasets/d1/images/" + imageId).header("Authorization", bearer(users[i])))
                    .andExpect(status().isNoContent());
        }
        var removed = jdbc.queryForMap("SELECT * FROM dataset_image WHERE image_id=?", original.get("IMAGE_ID"));
        assertThat(removed.get("IS_DELETED")).isEqualTo(Boolean.TRUE);
        assertThat(removed.get("CREATED_AT")).isEqualTo(original.get("CREATED_AT"));
        assertThat(removed.get("CREATED_BY")).isEqualTo("creator");
        assertThat(removed.get("UPDATED_BY")).isEqualTo("manager");
        assertThat(removed.get("VERSION_COUNT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history WHERE action='DELETE'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT created_by FROM dataset_image_history WHERE image_id=? AND action='DELETE'", String.class, original.get("IMAGE_ID"))).isEqualTo("manager");
        assertThat(fileCount()).isEqualTo(6);
        assertThat(Files.readAllBytes(STORAGE.resolve((String) original.get("STORAGE_PATH")))).isEqualTo(png);
        mvc.perform(delete("/api/datasets/d1/images/" + original.get("IMAGE_ID")).header("Authorization", bearer("manager")))
                .andExpect(status().isNotFound());
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:image:read'), ('uploader', 'dataset:item:read')");
        mvc.perform(get("/api/datasets/d1/images").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/datasets/d1").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageCount").value(0));
        for (String suffix : new String[] {"thumbnail", "content"}) {
            mvc.perform(get("/api/datasets/d1/images/" + original.get("IMAGE_ID") + "/" + suffix).header("Authorization", bearer("other")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void deniesImageDeletionOutsideScopeAndRollsBackWhenHistoryFails() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/images").file(file("보존.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated());
        var original = jdbc.queryForMap("SELECT * FROM dataset_image");
        String path = "/api/datasets/d1/images/" + original.get("IMAGE_ID");
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mvc.perform(delete(path).header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:image:delete')");
        for (String user : new String[] {"other", "outsider", "no-permission"}) {
            mvc.perform(delete(path).header("Authorization", bearer(user))).andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/datasets/d2/images/" + original.get("IMAGE_ID")).header("Authorization", bearer("manager")))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE workspace_member SET status='LEFT' WHERE workspace_id='a' AND user_key='creator'");
        mvc.perform(delete(path).header("Authorization", bearer("creator"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE workspace SET status='ARCHIVED' WHERE workspace_id='a'");
        mvc.perform(delete(path).header("Authorization", bearer("manager"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE workspace SET status='ACTIVE' WHERE workspace_id='a'");
        jdbc.execute("ALTER TABLE dataset_image_history ADD CONSTRAINT reject_delete_history_test CHECK (action <> 'DELETE')");
        try {
            assertThatThrownBy(() -> mvc.perform(delete(path).header("Authorization", bearer("manager"))))
                    .hasRootCauseInstanceOf(java.sql.SQLException.class);
            assertThat(jdbc.queryForMap("SELECT * FROM dataset_image")).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history", Integer.class)).isEqualTo(1);
            assertThat(fileCount()).isEqualTo(2);
        } finally {
            jdbc.execute("ALTER TABLE dataset_image_history DROP CONSTRAINT reject_delete_history_test");
        }
    }

    @Test
    void imageReadIsIndependentOfDatasetReadIncludingTargetsAndContent() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/images").file(file("독립.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated());
        String imageId = jdbc.queryForObject("SELECT image_id FROM dataset_image", String.class);
        jdbc.update("INSERT INTO dataset (dataset_id, name, workspace_id) VALUES ('outside', '다른 공간', 'b'), ('unassigned', '미귀속', NULL)");
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader' AND permission_code='dataset:item:read'");
        mvc.perform(get("/api/datasets").header("Authorization", bearer("other"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/datasets/image-targets").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].workspaceId").value("a"));
        for (String path : new String[] {"/api/datasets/d1/images", "/api/datasets/d1/images/" + imageId + "/content", "/api/datasets/d1/images/" + imageId + "/thumbnail"}) {
            mvc.perform(get(path).header("Authorization", bearer("other"))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader' AND permission_code='dataset:image:read'");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:item:read')");
        mvc.perform(get("/api/datasets").header("Authorization", bearer("other"))).andExpect(status().isOk());
        for (String path : new String[] {"/api/datasets/image-targets", "/api/datasets/d1/images", "/api/datasets/d1/images/" + imageId + "/content", "/api/datasets/d1/images/" + imageId + "/thumbnail"}) {
            mvc.perform(get(path).header("Authorization", bearer("other"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void separatesUploadAndUpdateAndRequiresUploadForNewReplacementNames() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/images").file(file("기존.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated());
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader' AND permission_code IN ('dataset:item:read', 'dataset:item:delete', 'dataset:image:upload')");
        mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true").file(file("기존.png", png))
                        .header("Authorization", bearer("manager")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(2));
        for (String mode : new String[] {"false", "true"}) {
            mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", mode).file(file("신규.png", png))
                            .header("Authorization", bearer("manager")))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image", Integer.class)).isEqualTo(2);
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='uploader' AND permission_code='dataset:image:update'");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('uploader', 'dataset:image:upload')");
        mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true").file(file("기존.png", png))
                        .header("Authorization", bearer("manager"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/datasets/d1/images").file(file("신규.png", png)).header("Authorization", bearer("manager")))
                .andExpect(status().isCreated());
    }

    @Test
    void serializesConcurrentReplacementsWithoutLosingVersionNumbers() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/images").file(file("동시.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            String authorization = bearer("manager");
            for (int i = 0; i < 2; i++) tasks.add(executor.submit(() -> {
                start.await();
                return mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true")
                        .file(file("동시.png", png)).header("Authorization", authorization)).andReturn().getResponse().getStatus();
            }));
            start.countDown();
            for (var task : tasks) assertThat(task.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(jdbc.queryForList("SELECT version_count FROM dataset_image ORDER BY version_count", Integer.class)).containsExactly(1, 2, 3);
        assertThat(jdbc.queryForObject("SELECT version_count FROM dataset_image WHERE is_deleted=FALSE", Integer.class)).isEqualTo(3);
    }

    @Test
    void replacesMatchingNamesPreservesFilesAndTracesVersionsAndDatasetDeletion() throws Exception {
        jdbc.update("UPDATE auth_user SET username='등록 담당자' WHERE user_key='creator'");
        jdbc.update("UPDATE auth_user SET username='수정 담당자' WHERE user_key='manager'");
        mvc.perform(multipart("/api/datasets/d1/images").file(file("교체.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(1))
                .andExpect(jsonPath("$.creatorUserId").value("login-creator"))
                .andExpect(jsonPath("$.creatorUsername").value("등록 담당자"));
        var first = jdbc.queryForMap("SELECT * FROM dataset_image");
        var replacementOutput = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", replacementOutput);
        byte[] replacement = replacementOutput.toByteArray();
        for (int version = 2; version <= 3; version++) {
            mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true").file(file("교체.png", replacement))
                            .header("Authorization", bearer("manager")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(version))
                    .andExpect(jsonPath("$.updatedBy").value("manager"))
                    .andExpect(jsonPath("$.updaterUsername").value("수정 담당자"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image WHERE is_deleted=FALSE", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT created_by FROM dataset_image WHERE image_id=?", String.class, first.get("IMAGE_ID"))).isEqualTo("creator");
        assertThat(jdbc.queryForObject("SELECT updated_by FROM dataset_image WHERE image_id=?", String.class, first.get("IMAGE_ID"))).isEqualTo("manager");
        assertThat(Files.readAllBytes(STORAGE.resolve((String) first.get("STORAGE_PATH")))).isEqualTo(png);
        assertThat(Files.exists(STORAGE.resolve((String) first.get("THUMBNAIL_PATH")))).isTrue();
        assertThat(fileCount()).isEqualTo(6);
        String currentImageId = jdbc.queryForObject("SELECT image_id FROM dataset_image WHERE is_deleted=FALSE", String.class);
        mvc.perform(get("/api/datasets/d1/images/" + currentImageId + "/content").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(content().bytes(replacement));
        mvc.perform(get("/api/datasets/d1/images").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].versionCount").value(3)).andExpect(jsonPath("$[0].updatedAt").isString());
        mvc.perform(get("/api/datasets/d1/images/" + first.get("IMAGE_ID") + "/thumbnail").header("Authorization", bearer("creator")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/datasets/d1/images/" + first.get("IMAGE_ID") + "/content").header("Authorization", bearer("creator")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/datasets/d1").header("Authorization", bearer("creator")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageCount").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history WHERE action='CREATE' AND created_by='creator'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history WHERE action='UPDATE' AND created_by='manager'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history WHERE action='DELETE'", Integer.class)).isEqualTo(2);
        mvc.perform(multipart("/api/datasets/d1/images").file(file("교체.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isConflict());
        mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true").file(file("신규.png", png))
                        .header("Authorization", bearer("owner")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(1));
        mvc.perform(delete("/api/datasets/d1").header("Authorization", bearer("owner"))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history WHERE action='DELETE' AND created_by='owner'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history WHERE image_created_by='creator'", Integer.class)).isGreaterThan(0);
        assertThat(fileCount()).isEqualTo(8);
    }

    @Test
    void rejectsUnauthorizedReplacementAndRollsBackOldStatusAndHistoryOnFailure() throws Exception {
        mvc.perform(multipart("/api/datasets/d1/images").file(file("보존.png", png)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated());
        var original = jdbc.queryForMap("SELECT * FROM dataset_image");
        for (String user : new String[] {"other", "outsider", "no-permission"}) {
            mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true").file(file("보존.png", png))
                            .header("Authorization", bearer(user)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true").file(file("보존.png", new byte[] {1}))
                        .header("Authorization", bearer("creator"))).andExpect(status().isBadRequest());
        jdbc.execute("ALTER TABLE dataset_image_history ADD CONSTRAINT reject_history_test CHECK (action <> 'UPDATE')");
        try {
            assertThatThrownBy(() -> mvc.perform(multipart("/api/datasets/d1/images").param("replaceExisting", "true")
                    .file(file("보존.png", png)).header("Authorization", bearer("creator"))))
                    .hasRootCauseInstanceOf(java.sql.SQLException.class);
            assertThat(jdbc.queryForMap("SELECT * FROM dataset_image")).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history", Integer.class)).isEqualTo(1);
            assertThat(fileCount()).isEqualTo(2);
        } finally {
            jdbc.execute("ALTER TABLE dataset_image_history DROP CONSTRAINT reject_history_test");
        }
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
        var smallThumbnail = ImageIO.read(STORAGE.resolve((String) row.get("THUMBNAIL_PATH")).toFile());
        assertThat(smallThumbnail.getWidth()).isEqualTo(2);
        assertThat(smallThumbnail.getHeight()).isEqualTo(2);
        String thumbnailUrl = "/api/datasets/d1/images/" + row.get("IMAGE_ID") + "/thumbnail";
        mvc.perform(get(thumbnailUrl).header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        String originalUrl = "/api/datasets/d1/images/" + row.get("IMAGE_ID") + "/content";
        mvc.perform(get(originalUrl).header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(content().bytes(png));
        mvc.perform(get(originalUrl)).andExpect(status().isUnauthorized());
        mvc.perform(get(originalUrl).header("Authorization", bearer("outsider"))).andExpect(status().isForbidden());
        mvc.perform(get(originalUrl).header("Authorization", bearer("no-permission"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/datasets/d2/images/" + row.get("IMAGE_ID") + "/content").header("Authorization", bearer("other")))
                .andExpect(status().isNotFound());
        mvc.perform(get(thumbnailUrl)).andExpect(status().isUnauthorized());
        mvc.perform(get(thumbnailUrl).header("Authorization", bearer("outsider"))).andExpect(status().isForbidden());
        mvc.perform(get(thumbnailUrl).header("Authorization", bearer("no-permission"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/datasets/d2/images/" + row.get("IMAGE_ID") + "/thumbnail").header("Authorization", bearer("other")))
                .andExpect(status().isNotFound());
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
        assertThat(fileCount()).isEqualTo(6);
        jdbc.update("DELETE FROM dataset WHERE dataset_id='d1'");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image WHERE dataset_id='d1'", Integer.class)).isZero();
    }

    @Test
    void generatesProportionalThumbnailAndLeavesLegacyImagesWithoutThumbnailUnavailable() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB), "png", output);
        byte[] original = output.toByteArray();
        mvc.perform(multipart("/api/datasets/d1/images").file(file("큰 이미지.png", original)).header("Authorization", bearer("creator")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.thumbnailUrl").isString());
        var stored = jdbc.queryForMap("SELECT * FROM dataset_image");
        assertThat(Files.readAllBytes(STORAGE.resolve((String) stored.get("STORAGE_PATH")))).isEqualTo(original);
        var response = mvc.perform(get("/api/datasets/d1/images/" + stored.get("IMAGE_ID") + "/thumbnail").header("Authorization", bearer("creator")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        var thumbnail = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
        assertThat(thumbnail.getWidth()).isEqualTo(256);
        assertThat(thumbnail.getHeight()).isEqualTo(128);
        assertThat(thumbnail.getColorModel().hasAlpha()).isTrue();
        mvc.perform(get("/api/datasets/d1/images/" + stored.get("IMAGE_ID") + "/content").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(content().bytes(original));
        jdbc.update("INSERT INTO dataset_image (image_id, dataset_id, original_filename, storage_path, media_type, size_bytes) VALUES ('legacy', 'd1', '과거.png', 'legacy.png', 'image/png', 1)");
        mvc.perform(get("/api/datasets/d1/images/legacy/thumbnail").header("Authorization", bearer("creator"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/datasets/d1/images").header("Authorization", bearer("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].thumbnailUrl").isEmpty());
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

    @Test
    void preservesFolderPathsAndReplacesOnlyTheSameRelativePath() throws Exception {
        for (String path : new String[] {"사진/하나/image.png", "사진/둘/image.png", "사진/" + "폴더/".repeat(90) + "image.png"}) {
            mvc.perform(multipart("/api/datasets/d1/images").file(file("image.png", png)).param("relativePath", path)
                    .header("Authorization", bearer("creator"))).andExpect(status().isCreated()).andExpect(jsonPath("$.originalFilename").value(path));
        }
        mvc.perform(multipart("/api/datasets/d1/images").file(file("image.png", png)).param("relativePath", "사진/하나/image.png")
                .header("Authorization", bearer("creator"))).andExpect(status().isConflict());
        mvc.perform(multipart("/api/datasets/d1/images").file(file("image.png", png)).param("relativePath", "사진/하나/image.png").param("replaceExisting", "true")
                .header("Authorization", bearer("creator"))).andExpect(status().isCreated()).andExpect(jsonPath("$.versionCount").value(2));
        assertThat(jdbc.queryForObject("SELECT version_count FROM dataset_image WHERE original_filename='사진/둘/image.png'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history WHERE original_filename='사진/하나/image.png'", Integer.class)).isEqualTo(3);
        var row = jdbc.queryForMap("SELECT * FROM dataset_image WHERE original_filename='사진/하나/image.png' AND is_deleted=FALSE");
        assertThat(Files.readAllBytes(STORAGE.resolve((String) row.get("STORAGE_PATH")))).isEqualTo(png);
        assertThat(Files.exists(STORAGE.resolve((String) row.get("THUMBNAIL_PATH")))).isTrue();
        mvc.perform(multipart("/api/datasets/d1/images").file(file("image.png", png)).param("relativePath", "../image.png")
                .header("Authorization", bearer("creator"))).andExpect(status().isBadRequest());
    }
    private MockMultipartFile file(String name, byte[] contents) {
        return new MockMultipartFile("file", name, "application/octet-stream", contents);
    }

    private String bearer(String userKey) { return "Bearer " + tokens.issue(userKey).value(); }

    private long fileCount() throws Exception {
        try (var files = Files.list(STORAGE)) { return files.count(); }
    }
}
