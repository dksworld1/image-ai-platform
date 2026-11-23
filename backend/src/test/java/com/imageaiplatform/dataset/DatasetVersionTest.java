package com.imageaiplatform.dataset;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.imageaiplatform.auth.service.JwtTokenService;
import com.imageaiplatform.dataset.service.DatasetVersionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest(properties={
    "datasource.app.url=jdbc:h2:mem:dataset-version-test;DB_CLOSE_DELAY=-1",
    "datasource.audit.url=jdbc:h2:mem:dataset-version-audit;DB_CLOSE_DELAY=-1",
    "datasource.catalog.url=jdbc:h2:mem:dataset-version-catalog;DB_CLOSE_DELAY=-1",
    "datasource.archive.url=jdbc:h2:mem:dataset-version-archive;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class DatasetVersionTest {
    static final Path ROOT=Path.of("target","dataset-version-"+UUID.randomUUID()).toAbsolutePath();
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.images.directory",()->ROOT.resolve("images").toString());
        registry.add("app.storage.files.directory",()->ROOT.resolve("files").toString());
    }
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @Autowired @Qualifier("appDataSource") DataSource source;
    @Autowired @Qualifier("appTransactionManager") PlatformTransactionManager transactions;
    @Autowired JwtTokenService tokens;
    @Autowired DatasetVersionService service;
    JdbcTemplate jdbc;MockMvc mvc;byte[] png;
    final ObjectMapper json=new ObjectMapper();
    String bearer(String user){return "Bearer "+tokens.issue(user).value();}
    @BeforeEach void setup() throws Exception {
        jdbc=new JdbcTemplate(source);mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        for(String table:new String[]{"dataset_version_image","dataset_version_file","dataset_version","dataset_image_history","dataset_image","dataset_file_history","dataset_file","dataset","workspace_member","workspace","auth_user_role","auth_role_permission","auth_role","auth_user"})jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO auth_role(role_code,display_name) VALUES('manager','Manager'),('file-reader','File reader')");
        for(String code:new String[]{"dataset:version:create","dataset:version:read","dataset:version:restore","dataset:image:upload","dataset:image:update","dataset:image:delete","dataset:image:read","dataset:file:upload","dataset:file:update","dataset:file:delete","dataset:file:read","dataset:item:delete"})jdbc.update("INSERT INTO auth_role_permission(role_code,permission_code) VALUES('manager',?)",code);
        jdbc.update("INSERT INTO auth_role_permission(role_code,permission_code) VALUES('file-reader','dataset:file:read')");
        for(String user:new String[]{"creator","member","outsider","no-permission"}){
            jdbc.update("INSERT INTO auth_user(user_key,user_id,username,password_hash) VALUES(?,?,?,'unused')",user,"login-"+user,"이름-"+user);
            jdbc.update("INSERT INTO auth_user_role(user_key,role_code) VALUES(?,?)",user,user.equals("no-permission")?"file-reader":"manager");
        }
        jdbc.update("INSERT INTO workspace(workspace_id,name,owner_user_key) VALUES('a','Space A','creator'),('b','Space B','outsider')");
        for(String user:new String[]{"creator","member","no-permission"})jdbc.update("INSERT INTO workspace_member(workspace_id,user_key,role_code,status,joined_at) VALUES('a',?,'MEMBER','ACTIVE',CURRENT_TIMESTAMP)",user);
        jdbc.update("INSERT INTO workspace_member(workspace_id,user_key,role_code,status,joined_at) VALUES('b','outsider','ADMIN','ACTIVE',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO dataset(dataset_id,name,description,workspace_id,created_by) VALUES('d','원본 이름','원본 설명','a','creator'),('empty','Empty',NULL,'a','creator'),('foreign','Foreign',NULL,'b','outsider')");
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);png=output.toByteArray();
    }
    JsonNode parse(String value)throws Exception{return json.readTree(value);}
    JsonNode upload(String kind,String name,byte[] bytes,boolean replace,String description)throws Exception{
        return parse(mvc.perform(multipart("/api/datasets/d/"+kind).file(new MockMultipartFile("file",name,kind.equals("images")?"image/png":"application/octet-stream",bytes))
                .param("replaceExisting",String.valueOf(replace)).param("description",description).header("Authorization",bearer("creator"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode version(String dataset)throws Exception{
        return parse(mvc.perform(post("/api/datasets/{id}/versions",dataset).header("Authorization",bearer("creator")).contentType("application/json").content("{\"changeNote\":\"첫 스냅샷\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void preservesFullSnapshotsAndDownloadsReplacedOrDeletedFileVersions()throws Exception{
        String image=upload("images","a.png",png,false,"이전 이미지 설명").get("imageId").asText();
        byte[] oldBytes="old data".getBytes();String file=upload("files","data.csv",oldBytes,false,"이전 파일 설명").get("fileId").asText();
        long before;try(var paths=Files.walk(ROOT)){before=paths.filter(Files::isRegularFile).count();}
        JsonNode v1=version("d");String first=v1.get("versionId").asText();assertThat(v1.get("imageCount").asInt()).isEqualTo(1);assertThat(v1.get("fileCount").asInt()).isEqualTo(1);
        try(var paths=Files.walk(ROOT)){assertThat(paths.filter(Files::isRegularFile).count()).isEqualTo(before);}
        mvc.perform(patch("/api/datasets/d/images/descriptions").header("Authorization",bearer("creator")).contentType("application/json").content("{\"items\":[{\"imageId\":\""+image+"\",\"description\":\"바뀐 설명\"}]}" )).andExpect(status().isOk());
        mvc.perform(delete("/api/datasets/d/images/{id}",image).header("Authorization",bearer("creator"))).andExpect(status().isNoContent());
        upload("files","data.csv","new data".getBytes(),true,"새 파일 설명");
        jdbc.update("UPDATE dataset SET name='새 이름',description='새 설명' WHERE dataset_id='d'");
        JsonNode v2=version("d");assertThat(v2.get("versionNumber").asInt()).isEqualTo(2);assertThat(v2.get("imageCount").asInt()).isZero();assertThat(v2.get("fileCount").asInt()).isEqualTo(1);
        mvc.perform(get("/api/datasets/d/versions/{id}",first).header("Authorization",bearer("creator"))).andExpect(jsonPath("$.name").value("원본 이름")).andExpect(jsonPath("$.description").value("원본 설명"));
        mvc.perform(get("/api/datasets/d/versions/{id}/entries",first).header("Authorization",bearer("creator")))
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[?(@.kind=='images')].description").value(org.hamcrest.Matchers.contains("이전 이미지 설명")))
                .andExpect(jsonPath("$[?(@.kind=='files')].versionCount").value(org.hamcrest.Matchers.contains(1)));
        mvc.perform(get("/api/datasets/d/images/{id}/content",image).header("Authorization",bearer("creator"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/datasets/d/versions/{version}/images/{entry}/content",first,image).header("Authorization",bearer("creator"))).andExpect(content().bytes(png));
        mvc.perform(get("/api/datasets/d/versions/{version}/files/{entry}/content",first,file).header("Authorization",bearer("creator"))).andExpect(content().bytes(oldBytes)).andExpect(content().contentType("application/octet-stream"));
        String second=v2.get("versionId").asText();
        mvc.perform(get("/api/datasets/d/versions/{version}/files/{entry}/content",second,file).header("Authorization",bearer("creator"))).andExpect(status().isNotFound());
    }
    @Test void protectsVersionedDatasetsAndSourceRowsAndDoesNotExposeVersionMutation()throws Exception{
        String image=upload("images","a.png",png,false,"").get("imageId").asText();String version=version("d").get("versionId").asText();
        int history=jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history",Integer.class);
        mvc.perform(delete("/api/datasets/d").header("Authorization",bearer("creator"))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image_history",Integer.class)).isEqualTo(history);
        assertThatThrownBy(()->jdbc.update("DELETE FROM dataset_image WHERE image_id=?",image)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM dataset WHERE dataset_id='d'")).isInstanceOf(DataIntegrityViolationException.class);
        mvc.perform(delete("/api/datasets/d/versions/{id}",version).header("Authorization",bearer("creator"))).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/datasets/empty").header("Authorization",bearer("creator"))).andExpect(status().isNoContent());
    }
    @Test void requiresVersionPermissionsManagementScopeAndActiveMembership()throws Exception{
        String version=version("d").get("versionId").asText();
        mvc.perform(get("/api/datasets/d/versions")).andExpect(status().isUnauthorized());
        for(String user:new String[]{"outsider","no-permission"}){
            mvc.perform(get("/api/datasets/d/versions").header("Authorization",bearer(user))).andExpect(status().isForbidden());
            mvc.perform(post("/api/datasets/d/versions").header("Authorization",bearer(user)).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/datasets/d/versions").header("Authorization",bearer("member"))).andExpect(status().isOk());
        mvc.perform(post("/api/datasets/d/versions").header("Authorization",bearer("member")).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get("/api/datasets/version-targets").header("Authorization",bearer("creator"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/datasets/empty/versions/{id}",version).header("Authorization",bearer("creator"))).andExpect(status().isNotFound());
        jdbc.update("UPDATE workspace_member SET status='LEFT' WHERE user_key='creator'");
        mvc.perform(get("/api/datasets/d/versions").header("Authorization",bearer("creator"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE workspace_member SET status='ACTIVE' WHERE user_key='creator'");jdbc.update("UPDATE workspace SET status='ARCHIVED' WHERE workspace_id='a'");
        mvc.perform(get("/api/datasets/d/versions").header("Authorization",bearer("creator"))).andExpect(status().isForbidden());
    }
    <T>T asCreator(Callable<T> task)throws Exception{
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("creator","",List.of()));
        try{return task.call();}finally{SecurityContextHolder.clearContext();}
    }
    @Test void createsEmptySnapshotsAndRollsBackAllSnapshotRows()throws Exception{
        JsonNode empty=version("empty");assertThat(empty.get("imageCount").asInt()).isZero();assertThat(empty.get("fileCount").asInt()).isZero();
        upload("images","a.png",png,false,"");upload("files","a.csv",new byte[]{1},false,"");
        asCreator(()->{
            assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{service.create("d","rollback");throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);return null;
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_version WHERE dataset_id='d'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_version_image",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_version_file",Integer.class)).isZero();
        mvc.perform(post("/api/datasets/d/versions").header("Authorization",bearer("creator")).contentType("application/json").content(json.writeValueAsString(java.util.Map.of("changeNote","x".repeat(2001))))).andExpect(status().isBadRequest());
    }
    @Test void serializesConcurrentVersionNumbers()throws Exception{
        upload("files","a.csv",new byte[]{1},false,"");
        try(var executor=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            Callable<Integer> task=()->asCreator(()->{start.await(5,TimeUnit.SECONDS);return service.create("d","concurrent").versionNumber();});
            var first=executor.submit(task);var second=executor.submit(task);start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(1,2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_version_file",Integer.class)).isEqualTo(2);
        }
    }

    @Test void editsFromV5AndCreatesV10WithoutChangingPastSnapshotsOrCopyingBytes()throws Exception{
        String image=upload("images","a.png",png,false,"v5 이미지 설명").get("imageId").asText();
        String file=upload("files","a.csv","v5 data".getBytes(),false,"v5 파일 설명").get("fileId").asText();
        JsonNode base=null;for(int i=1;i<=5;i++)base=version("d");String v5=base.get("versionId").asText();
        upload("images","a.png",png,true,"현재 이미지");String newer=upload("files","a.csv","v9 data".getBytes(),true,"현재 파일").get("fileId").asText();
        upload("files","extra.csv",new byte[]{9},false,"기준 밖 파일");
        jdbc.update("UPDATE dataset SET name='현재 이름',description='현재 설명' WHERE dataset_id='d'");
        JsonNode last=null;for(int i=6;i<=9;i++)last=version("d");String v9=last.get("versionId").asText();
        long before;try(var paths=Files.walk(ROOT)){before=paths.filter(Files::isRegularFile).count();}
        mvc.perform(post("/api/datasets/d/versions/{id}/restore",v5).header("Authorization",bearer("creator"))).andExpect(status().isOk()).andExpect(jsonPath("$.versionNumber").value(5));
        try(var paths=Files.walk(ROOT)){assertThat(paths.filter(Files::isRegularFile).count()).isEqualTo(before);}
        assertThat(jdbc.queryForObject("SELECT MAX(version_number) FROM dataset_version WHERE dataset_id='d'",Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT name FROM dataset WHERE dataset_id='d'",String.class)).isEqualTo("원본 이름");
        mvc.perform(get("/api/datasets/d/images").header("Authorization",bearer("creator"))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].imageId").value(image)).andExpect(jsonPath("$[0].description").value("v5 이미지 설명"));
        mvc.perform(get("/api/datasets/d/files").header("Authorization",bearer("creator"))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].fileId").value(file));
        mvc.perform(get("/api/datasets/d/files/{id}/content",file).header("Authorization",bearer("creator"))).andExpect(content().bytes("v5 data".getBytes()));
        upload("images","a.png",png,true,"v10 이미지");
        mvc.perform(patch("/api/datasets/d/files/descriptions").header("Authorization",bearer("creator")).contentType("application/json")
                .content("{\"items\":[{\"fileId\":\""+file+"\",\"description\":\"v10 파일 설명\"}]}" )).andExpect(status().isOk());
        JsonNode v10=version("d");assertThat(v10.get("versionNumber").asInt()).isEqualTo(10);assertThat(v10.get("fileCount").asInt()).isEqualTo(1);
        mvc.perform(get("/api/datasets/d/versions/{id}/entries",v5).header("Authorization",bearer("creator"))).andExpect(jsonPath("$[?(@.kind=='files')].description").value(org.hamcrest.Matchers.contains("v5 파일 설명")));
        mvc.perform(get("/api/datasets/d/versions/{version}/files/{entry}/content",v9,newer).header("Authorization",bearer("creator"))).andExpect(content().bytes("v9 data".getBytes()));
        mvc.perform(get("/api/datasets/d/versions/{id}/entries",v10.get("versionId").asText()).header("Authorization",bearer("creator"))).andExpect(jsonPath("$[?(@.kind=='files')].description").value(org.hamcrest.Matchers.contains("v10 파일 설명")));
    }
    @Test void restorationOfEmptySnapshotClearsOnlyTheWorkingCopy()throws Exception{
        String base=version("d").get("versionId").asText();
        String image=upload("images","a.png",png,false,"").get("imageId").asText();upload("files","a.csv",new byte[]{1},false,"");
        String latest=version("d").get("versionId").asText();
        mvc.perform(post("/api/datasets/d/versions/{id}/restore",base).header("Authorization",bearer("creator"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_image WHERE is_deleted=FALSE",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file WHERE is_deleted=FALSE",Integer.class)).isZero();
        mvc.perform(get("/api/datasets/d/versions/{id}/entries",latest).header("Authorization",bearer("creator"))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/datasets/d/versions/{version}/images/{entry}/content",latest,image).header("Authorization",bearer("creator"))).andExpect(content().bytes(png));
    }
    @Test void restorationRequiresExplicitPermissionReadAndManagementScope()throws Exception{
        String version=version("d").get("versionId").asText();
        mvc.perform(post("/api/datasets/d/versions/{id}/restore",version)).andExpect(status().isUnauthorized());
        for(String user:new String[]{"outsider","member","no-permission"})mvc.perform(post("/api/datasets/d/versions/{id}/restore",version).header("Authorization",bearer(user))).andExpect(status().isForbidden());
        mvc.perform(post("/api/datasets/empty/versions/{id}/restore",version).header("Authorization",bearer("creator"))).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM auth_role_permission WHERE permission_code='dataset:version:restore'");
        mvc.perform(post("/api/datasets/d/versions/{id}/restore",version).header("Authorization",bearer("creator"))).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO auth_role_permission(role_code,permission_code) VALUES('manager','dataset:version:restore')");
        jdbc.update("DELETE FROM auth_role_permission WHERE permission_code='dataset:version:read'");
        mvc.perform(post("/api/datasets/d/versions/{id}/restore",version).header("Authorization",bearer("creator"))).andExpect(status().isForbidden());
    }
    @Test void failedRestorationKeepsMetadataFilesAndHistoryUnchanged()throws Exception{
        String old=upload("files","a.csv",new byte[]{1},false,"기준 설명").get("fileId").asText();String base=version("d").get("versionId").asText();
        String current=upload("files","a.csv",new byte[]{2},true,"현재 설명").get("fileId").asText();
        jdbc.update("UPDATE dataset SET name='현재 이름' WHERE dataset_id='d'");
        int history=jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file_history",Integer.class);
        asCreator(()->{
            assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{service.restore("d",base);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);return null;
        });
        assertThat(jdbc.queryForObject("SELECT file_id FROM dataset_file WHERE dataset_id='d' AND is_deleted=FALSE",String.class)).isEqualTo(current);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file_history",Integer.class)).isEqualTo(history);
        Path oldPath=ROOT.resolve("files").resolve(jdbc.queryForObject("SELECT storage_path FROM dataset_file WHERE file_id=?",String.class,old));Files.delete(oldPath);
        mvc.perform(post("/api/datasets/d/versions/{id}/restore",base).header("Authorization",bearer("creator"))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT name FROM dataset WHERE dataset_id='d'",String.class)).isEqualTo("현재 이름");
        assertThat(jdbc.queryForObject("SELECT file_id FROM dataset_file WHERE is_deleted=FALSE",String.class)).isEqualTo(current);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dataset_file_history",Integer.class)).isEqualTo(history);
    }
}
