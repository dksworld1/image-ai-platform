package com.imageaiplatform.artifact.dao.impl;

import com.imageaiplatform.artifact.dao.ArtifactDao;
import com.imageaiplatform.artifact.domain.Artifact;
import com.imageaiplatform.artifact.domain.ArtifactFile;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisArtifactDao implements ArtifactDao {
    private final SqlSession session;
    public MyBatisArtifactDao(@Qualifier("appSqlSessionTemplate") SqlSession session) { this.session = session; }
    @Override public void insertArtifact(Artifact value) { session.insert("artifact.insertArtifact", value); }
    @Override public Artifact artifact(String id) { return session.selectOne("artifact.artifact", id); }
    @Override public Artifact lockArtifact(String id) { return session.selectOne("artifact.lockArtifact", id); }
    @Override public List<Artifact> artifacts(String workspaceId) { return session.selectList("artifact.artifacts", workspaceId); }
    @Override public void insertFile(ArtifactFile value) { session.insert("artifact.insertFile", value); }
    @Override public List<ArtifactFile> files(String id) { return session.selectList("artifact.files", id); }
    @Override public ArtifactFile file(String artifactId, String fileId) { return session.selectOne("artifact.file", Map.of("artifactId", artifactId, "fileId", fileId)); }
    @Override public void artifactStatus(String id, String status, String actor) { session.update("artifact.artifactStatus", Map.of("id", id, "status", status, "actor", actor)); }
}
