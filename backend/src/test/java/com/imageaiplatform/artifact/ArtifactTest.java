package com.imageaiplatform.artifact;

import static org.assertj.core.api.Assertions.*;
import com.imageaiplatform.artifact.service.ArtifactService;
import com.imageaiplatform.artifact.domain.ArtifactConflictException;
import com.imageaiplatform.artifact.domain.ArtifactFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "datasource.app.url=jdbc:h2:mem:artifact-test;DB_CLOSE_DELAY=-1",
    "datasource.audit.url=jdbc:h2:mem:artifact-audit;DB_CLOSE_DELAY=-1",
    "datasource.catalog.url=jdbc:h2:mem:artifact-catalog;DB_CLOSE_DELAY=-1",
    "datasource.archive.url=jdbc:h2:mem:artifact-archive;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class ArtifactTest {
    static final Path ROOT = Path.of("target", "artifact-" + UUID.randomUUID()).toAbsolutePath();
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.artifacts.roots.local", () -> ROOT.toString());
        registry.add("app.storage.artifacts.max-file-size", () -> "1KB");
    }
    @Autowired @Qualifier("appDataSource") DataSource source;
    @Autowired @Qualifier("appTransactionManager") PlatformTransactionManager transactions;
    @Autowired ArtifactService service;
    JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        jdbc = new JdbcTemplate(source);
        for (String table : new String[]{"artifact_file", "artifact", "workspace_member", "workspace", "auth_user_role", "auth_role_permission", "auth_role", "auth_user"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO auth_role(role_code, display_name) VALUES ('artifact-manager', 'Artifact manager')");
        for (String action : new String[]{"create", "read", "update"}) jdbc.update("INSERT INTO auth_role_permission(role_code, permission_code) VALUES ('artifact-manager', ?)", "pipeline:artifact:"+action);
        for (String user : new String[]{"member", "outsider", "no-permission"}) {
            jdbc.update("INSERT INTO auth_user(user_key, user_id, password_hash) VALUES (?, ?, 'unused')", user, user);
            if (!user.equals("no-permission")) jdbc.update("INSERT INTO auth_user_role(user_key, role_code) VALUES (?, 'artifact-manager')", user);
        }
        jdbc.update("INSERT INTO workspace(workspace_id, name, owner_user_key) VALUES ('w', 'Workspace', 'member'), ('w2', 'Second workspace', 'member')");
        for (String workspace : new String[]{"w", "w2"}) for (String user : new String[]{"member", "no-permission"}) jdbc.update("INSERT INTO workspace_member(workspace_id, user_key, role_code, status, joined_at) VALUES (?, ?, 'MEMBER', 'ACTIVE', CURRENT_TIMESTAMP)", workspace, user);
        if (Files.exists(ROOT)) try (var paths = Files.walk(ROOT)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    String artifact(String workspace) throws Exception {
        var artifact = asMember(() -> service.createArtifact(workspace, "MODEL", "weights", "1", null, null, null));
        assertThat(artifact.protectedFlag()).isFalse();
        return artifact.artifactId();
    }

    ArtifactFile upload(String id, String name, byte[] bytes) throws Exception {
        return asMember(() -> service.addFile(id, name, null, new ByteArrayInputStream(bytes), bytes.length));
    }

    @Test void storesMultipleFilesChecksumsAndMakesAvailableContentImmutable() throws Exception {
        String id = artifact("w");
        assertThatThrownBy(() -> asMember(() -> service.completeArtifact(id))).isInstanceOf(ArtifactConflictException.class);

        byte[] bytes = {0, 1, 2, 3};
        ArtifactFile file = upload(id, "weights.bin", bytes);
        assertThat(file.checksum()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(file.rootKey()).isEqualTo("local");
        assertThatThrownBy(() -> asMember(() -> service.content(id, file.fileId()))).isInstanceOf(ArtifactConflictException.class);

        ArtifactFile config = upload(id, "config.json", new byte[0]);
        assertThatThrownBy(() -> upload(id, "weights.bin", bytes)).isInstanceOf(DuplicateKeyException.class);
        var complete = asMember(() -> service.completeArtifact(id));
        assertThat(complete.status()).isEqualTo("AVAILABLE");
        assertThat(asMember(() -> service.completeArtifact(id))).isEqualTo(complete);
        assertThat(asMember(() -> service.files(id))).containsExactlyInAnyOrder(file, config);
        try (InputStream input = asMember(() -> service.content(id, file.fileId())).contents()) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
        assertThatThrownBy(() -> upload(id, "later.bin", bytes)).isInstanceOf(ArtifactConflictException.class);
        assertThatThrownBy(() -> asMember(() -> service.failArtifact(id))).isInstanceOf(ArtifactConflictException.class);
    }

    @Test void requiresIndependentPermissionsAndCurrentMembership() throws Exception {
        String id = artifact("w");
        for (String user : List.of("outsider", "no-permission")) {
            assertThatThrownBy(() -> asUser(user, () -> service.artifact(id))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> asUser(user, () -> service.completeArtifact(id))).isInstanceOf(AccessDeniedException.class);
        }
        jdbc.update("DELETE FROM auth_role_permission WHERE permission_code='pipeline:artifact:read'");
        assertThatThrownBy(() -> asMember(() -> service.artifact(id))).isInstanceOf(AccessDeniedException.class);
        jdbc.update("INSERT INTO auth_role_permission(role_code, permission_code) VALUES ('artifact-manager', 'pipeline:artifact:read')");
        assertThat(asMember(() -> service.artifact(id)).artifactId()).isEqualTo(id);
        jdbc.update("UPDATE workspace_member SET status='LEFT' WHERE user_key='member'");
        assertThatThrownBy(() -> asMember(() -> service.artifact(id))).isInstanceOf(AccessDeniedException.class);
    }

    @Test void checksRealFilesBeforeAvailabilityAndLeavesFailuresUnusable() throws Exception {
        String id = artifact("w");
        ArtifactFile file = upload(id, "result.zip", new byte[]{1});
        Path actual = ROOT.resolve(file.relativePath());
        Files.write(actual, new byte[]{2});
        assertThatThrownBy(() -> asMember(() -> service.completeArtifact(id))).isInstanceOf(ArtifactConflictException.class);
        Files.delete(actual);
        assertThatThrownBy(() -> asMember(() -> service.completeArtifact(id))).isInstanceOf(ArtifactConflictException.class);
        assertThat(asMember(() -> service.failArtifact(id)).status()).isEqualTo("FAILED");
        assertThatThrownBy(() -> asMember(() -> service.completeArtifact(id))).isInstanceOf(ArtifactConflictException.class);
    }

    <T> T asMember(Callable<T> task) throws Exception {
        return asUser("member", task);
    }

    <T> T asUser(String user, Callable<T> task) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "", List.of()));
        try {
            return task.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test void validatesSizePathsAndCleansUpRolledBackWrites() throws Exception {
        String id=artifact("w");
        for (String name : List.of("../escape", "/absolute", "a//b")) {
            assertThatThrownBy(() -> upload(id, name, new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> upload(id, "large", new byte[1025])).isInstanceOf(IllegalArgumentException.class);
        asMember(()->{
            assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{service.addFile(id,"rollback",null,new ByteArrayInputStream(new byte[]{1}),1); throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
            InputStream broken=new InputStream(){@Override public int read() throws IOException {throw new IOException("broken");}};
            assertThatThrownBy(()->service.addFile(id,"broken",null,broken,1)).isInstanceOf(UncheckedIOException.class);
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM artifact_file",Integer.class)).isZero();
        try(var files=Files.walk(ROOT)){assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();}
    }
    @Test void concurrentUploadAndCompletionRemainConsistent() throws Exception {
        String id=artifact("w"); upload(id,"first",new byte[]{1});
        try(var executor=Executors.newFixedThreadPool(2)) {
            var race=new CountDownLatch(1);
            var upload=executor.submit(()->asMember(()->{race.await(5,TimeUnit.SECONDS);try {service.addFile(id,"second",null,new ByteArrayInputStream(new byte[]{2}),1);return true;}catch(ArtifactConflictException expected){return false;}}));
            var complete=executor.submit(()->asMember(()->{race.await(5,TimeUnit.SECONDS);return service.completeArtifact(id);}));race.countDown();
            boolean added=upload.get(10,TimeUnit.SECONDS);assertThat(complete.get(10,TimeUnit.SECONDS).status()).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM artifact_file WHERE artifact_id=?",Integer.class,id)).isEqualTo(added?2:1);
        }
    }
}
