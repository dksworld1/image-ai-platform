package com.imageaiplatform.artifact;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.imageaiplatform.artifact.service.ArtifactService;
import com.imageaiplatform.artifact.domain.ArtifactConflictException;
import com.imageaiplatform.auth.service.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

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
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @Autowired @Qualifier("appDataSource") DataSource source;
    @Autowired @Qualifier("appTransactionManager") PlatformTransactionManager transactions;
    @Autowired JwtTokenService tokens;
    @Autowired ArtifactService service;
    JdbcTemplate jdbc; MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        jdbc = new JdbcTemplate(source);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        for (String table : new String[]{"artifact_set_member", "artifact_set_version", "artifact_set", "artifact_file", "artifact", "workspace_member", "workspace", "auth_user_role", "auth_role_permission", "auth_role", "auth_user"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO auth_role(role_code, display_name) VALUES ('artifact-manager', 'Artifact manager')");
        for (String resource : new String[]{"artifact", "artifactset"}) for (String action : new String[]{"create", "read", "update"}) jdbc.update("INSERT INTO auth_role_permission(role_code, permission_code) VALUES ('artifact-manager', ?)", "pipeline:"+resource+":"+action);
        for (String user : new String[]{"member", "outsider", "no-permission"}) {
            jdbc.update("INSERT INTO auth_user(user_key, user_id, password_hash) VALUES (?, ?, 'unused')", user, user);
            if (!user.equals("no-permission")) jdbc.update("INSERT INTO auth_user_role(user_key, role_code) VALUES (?, 'artifact-manager')", user);
        }
        jdbc.update("INSERT INTO workspace(workspace_id, name, owner_user_key) VALUES ('w', 'Workspace', 'member'), ('w2', 'Second workspace', 'member')");
        for (String workspace : new String[]{"w", "w2"}) for (String user : new String[]{"member", "no-permission"}) jdbc.update("INSERT INTO workspace_member(workspace_id, user_key, role_code, status, joined_at) VALUES (?, ?, 'MEMBER', 'ACTIVE', CURRENT_TIMESTAMP)", workspace, user);
        if (Files.exists(ROOT)) try (var paths = Files.walk(ROOT)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    String bearer(String user) { return "Bearer " + tokens.issue(user).value(); }
    JsonNode createArtifact(String workspace) throws Exception {
        return body(mvc.perform(post("/api/artifacts").header("Authorization", bearer("member")).contentType("application/json")
                .content("{\"workspaceId\":\""+workspace+"\",\"dataType\":\"MODEL\",\"schemaName\":\"weights\",\"schemaVersion\":\"1\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.protectedFlag").value(false)).andReturn().getResponse().getContentAsString());
    }
    JsonNode body(String text) throws Exception { return json.readTree(text); }
    String artifact(String workspace) throws Exception { return createArtifact(workspace).get("artifactId").asText(); }
    JsonNode upload(String id, String name, byte[] bytes) throws Exception {
        return body(mvc.perform(multipart("/api/artifacts/{id}/files", id).file(new MockMultipartFile("file", name, "application/octet-stream", bytes)).header("Authorization", bearer("member")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    String available(String workspace) throws Exception {
        String id=artifact(workspace); upload(id,"output.zip",new byte[]{1,2,3});
        mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer("member"))).andExpect(status().isOk()); return id;
    }
    String set() throws Exception {
        return body(mvc.perform(post("/api/artifact-sets").header("Authorization",bearer("member")).contentType("application/json")
                .content("{\"workspaceId\":\"w\",\"name\":\"학습셋\",\"purpose\":\"TRAINING\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("setId").asText();
    }
    JsonNode version(String id) throws Exception {
        return body(mvc.perform(post("/api/artifact-sets/{id}/versions",id).header("Authorization",bearer("member"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    String member(String id,long ordinal,String key) { return "{\"artifactId\":\""+id+"\",\"ordinal\":"+ordinal+",\"sourceKey\":\""+key+"\"}"; }
    void members(String set,String version,String items,int expectedStatus) throws Exception {
        mvc.perform(put("/api/artifact-sets/{id}/versions/{version}/members",set,version).header("Authorization",bearer("member")).contentType("application/json").content("{\"members\":["+items+"]}"))
                .andExpect(status().is(expectedStatus));
    }
    @Test void storesMultipleFilesChecksumsAndMakesAvailableContentImmutable() throws Exception {
        String id=artifact("w");
        mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer("member"))).andExpect(status().isConflict());
        byte[] bytes=new byte[]{0,1,2,3}; JsonNode file=upload(id,"weights.bin",bytes);
        assertThat(file.get("checksum").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(file.get("rootKey").asText()).isEqualTo("local");
        String fileId=file.get("fileId").asText();
        mvc.perform(get("/api/artifacts/{id}/files/{file}/content",id,fileId).header("Authorization",bearer("member"))).andExpect(status().isConflict());
        upload(id,"config.json",new byte[0]);
        mvc.perform(multipart("/api/artifacts/{id}/files",id).file(new MockMultipartFile("file","weights.bin","application/octet-stream",bytes)).header("Authorization",bearer("member"))).andExpect(status().isConflict());
        mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer("member"))).andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer("member"))).andExpect(status().isOk());
        mvc.perform(get("/api/artifacts/{id}/files/{file}/content",id,fileId).header("Authorization",bearer("member"))).andExpect(content().bytes(bytes)).andExpect(content().contentType("application/octet-stream"));
        mvc.perform(multipart("/api/artifacts/{id}/files",id).file(new MockMultipartFile("file","later.bin","application/octet-stream",bytes)).header("Authorization",bearer("member"))).andExpect(status().isConflict());
        mvc.perform(post("/api/artifacts/{id}/fail",id).header("Authorization",bearer("member"))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM artifact_file",Integer.class)).isEqualTo(2);
    }
    @Test void versionsReuseArtifactsPreserveOrderAndConfirmEmptySets() throws Exception {
        String a=available("w"), b=available("w"), set=set(), v1=version(set).get("versionId").asText();
        members(set,v1,member(a,20,"image-a")+","+member(b,10,"image-b"),200);
        var confirmed=body(mvc.perform(post("/api/artifact-sets/{id}/versions/{version}/confirm",set,v1).header("Authorization",bearer("member")))
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.itemCount").value(2)).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/artifact-sets/{id}/versions/{version}/members",set,v1).header("Authorization",bearer("member"))).andExpect(jsonPath("$[0].artifactId").value(b));
        mvc.perform(post("/api/artifact-sets/{id}/versions/{version}/confirm",set,v1).header("Authorization",bearer("member"))).andExpect(jsonPath("$.compositionChecksum").value(confirmed.get("compositionChecksum").asText()));
        members(set,v1,"",409);
        JsonNode second=version(set); assertThat(second.get("versionNumber").asInt()).isEqualTo(2);
        members(set,second.get("versionId").asText(),member(a,0,"image-a"),200);
        String empty=version(set).get("versionId").asText();
        mvc.perform(post("/api/artifact-sets/{id}/versions/{version}/confirm",set,empty).header("Authorization",bearer("member"))).andExpect(jsonPath("$.itemCount").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM artifact_file",Integer.class)).isEqualTo(2);
    }
    @Test void rejectsDuplicateUnavailableAndCrossWorkspaceMembersAtomically() throws Exception {
        String a=available("w"), foreign=available("w2"), writing=artifact("w"), set=set(), version=version(set).get("versionId").asText();
        members(set,version,member(a,0,"a"),200);
        members(set,version,member(a,0,"a")+","+member(a,1,"duplicate"),400);
        members(set,version,member(a,0,"a")+","+member(foreign,0,"duplicate-position"),400);
        members(set,version,member(writing,0,"unavailable"),409);
        members(set,version,member(foreign,0,"foreign"),403);
        mvc.perform(get("/api/artifact-sets/{id}/versions/{version}/members",set,version).header("Authorization",bearer("member"))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].artifactId").value(a));
        String other=set();
        mvc.perform(get("/api/artifact-sets/{id}/versions/{version}",other,version).header("Authorization",bearer("member"))).andExpect(status().isForbidden());
    }
    @Test void requiresIndependentPermissionsAndCurrentMembership() throws Exception {
        String id=available("w"), set=set(), version=version(set).get("versionId").asText();
        mvc.perform(get("/api/artifacts/{id}",id)).andExpect(status().isUnauthorized());
        for(String user:new String[]{"outsider","no-permission"}) {
            mvc.perform(get("/api/artifacts/{id}",id).header("Authorization",bearer(user))).andExpect(status().isForbidden());
            mvc.perform(get("/api/artifact-sets/{id}/versions/{version}",set,version).header("Authorization",bearer(user))).andExpect(status().isForbidden());
            mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer(user))).andExpect(status().isForbidden());
        }
        jdbc.update("DELETE FROM auth_role_permission WHERE permission_code='pipeline:artifact:read'");
        mvc.perform(get("/api/artifacts/{id}",id).header("Authorization",bearer("member"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/artifact-sets/{id}",set).header("Authorization",bearer("member"))).andExpect(status().isOk());
        members(set,version,member(id,0,"a"),403);
        jdbc.update("UPDATE workspace_member SET status='LEFT' WHERE user_key='member'");
        mvc.perform(get("/api/artifact-sets/{id}",set).header("Authorization",bearer("member"))).andExpect(status().isForbidden());
    }
    @Test void checksRealFilesBeforeAvailabilityAndLeavesFailuresUnusable() throws Exception {
        String id=artifact("w"); JsonNode file=upload(id,"result.zip",new byte[]{1});
        Path actual=ROOT.resolve(file.get("relativePath").asText()); Files.write(actual,new byte[]{2});
        mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer("member"))).andExpect(status().isConflict());
        Files.delete(actual);
        mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer("member"))).andExpect(status().isConflict());
        mvc.perform(post("/api/artifacts/{id}/fail",id).header("Authorization",bearer("member"))).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(post("/api/artifacts/{id}/complete",id).header("Authorization",bearer("member"))).andExpect(status().isConflict());
    }
    <T> T asMember(Callable<T> task) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("member","",List.of()));
        try {return task.call();} finally {SecurityContextHolder.clearContext();}
    }
    @Test void validatesSizePathsAndCleansUpRolledBackWrites() throws Exception {
        String id=artifact("w");
        for(String name:new String[]{"../escape","/absolute","a//b"}) mvc.perform(multipart("/api/artifacts/{id}/files",id).file(new MockMultipartFile("file","safe","application/octet-stream",new byte[]{1})).param("internalName",name).header("Authorization",bearer("member"))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/artifacts/{id}/files",id).file(new MockMultipartFile("file","large","application/octet-stream",new byte[1025])).header("Authorization",bearer("member"))).andExpect(status().isBadRequest());
        asMember(()->{
            assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{service.addFile(id,"rollback",null,new ByteArrayInputStream(new byte[]{1}),1); throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
            InputStream broken=new InputStream(){@Override public int read() throws IOException {throw new IOException("broken");}};
            assertThatThrownBy(()->service.addFile(id,"broken",null,broken,1)).isInstanceOf(UncheckedIOException.class);
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM artifact_file",Integer.class)).isZero();
        try(var files=Files.walk(ROOT)){assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();}
    }
    @Test void concurrentVersionAllocationAndCompletionRemainConsistent() throws Exception {
        String set=set(), id=artifact("w"); upload(id,"first",new byte[]{1});
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> allocate=()->asMember(()->{start.await(5,TimeUnit.SECONDS);return service.createVersion(set).versionNumber();});
            var one=executor.submit(allocate);var two=executor.submit(allocate);start.countDown();
            assertThat(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(1,2);
            var race=new CountDownLatch(1);
            var upload=executor.submit(()->asMember(()->{race.await(5,TimeUnit.SECONDS);try {service.addFile(id,"second",null,new ByteArrayInputStream(new byte[]{2}),1);return true;}catch(ArtifactConflictException expected){return false;}}));
            var complete=executor.submit(()->asMember(()->{race.await(5,TimeUnit.SECONDS);return service.completeArtifact(id);}));race.countDown();
            boolean added=upload.get(10,TimeUnit.SECONDS);assertThat(complete.get(10,TimeUnit.SECONDS).status()).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM artifact_file WHERE artifact_id=?",Integer.class,id)).isEqualTo(added?2:1);
        }
    }
}
