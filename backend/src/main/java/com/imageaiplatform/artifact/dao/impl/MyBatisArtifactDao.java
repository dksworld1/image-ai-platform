package com.imageaiplatform.artifact.dao.impl;

import com.imageaiplatform.artifact.dao.ArtifactDao;
import com.imageaiplatform.artifact.domain.Artifact;
import com.imageaiplatform.artifact.domain.ArtifactFile;
import com.imageaiplatform.artifact.domain.ArtifactSet;
import com.imageaiplatform.artifact.domain.ArtifactSetVersion;
import com.imageaiplatform.artifact.domain.ArtifactSetMember;
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
    @Override public void insertSet(ArtifactSet value) { session.insert("artifact.insertSet", value); }
    @Override public ArtifactSet set(String id) { return session.selectOne("artifact.set", id); }
    @Override public ArtifactSet lockSet(String id) { return session.selectOne("artifact.lockSet", id); }
    @Override public List<ArtifactSet> sets(String workspaceId) { return session.selectList("artifact.sets", workspaceId); }
    @Override public void insertVersion(ArtifactSetVersion value) { session.insert("artifact.insertVersion", value); }
    @Override public ArtifactSetVersion version(String id) { return session.selectOne("artifact.version", id); }
    @Override public ArtifactSetVersion lockVersion(String id) { return session.selectOne("artifact.lockVersion", id); }
    @Override public List<ArtifactSetVersion> versions(String id) { return session.selectList("artifact.versions", id); }
    @Override public Integer nextVersionNumber(String id) { return session.selectOne("artifact.nextVersionNumber", id); }
    @Override public List<ArtifactSetMember> members(String id) { return session.selectList("artifact.members", id); }
    @Override public void deleteMembers(String id) { session.delete("artifact.deleteMembers", id); }
    @Override public void insertMember(ArtifactSetMember value) { session.insert("artifact.insertMember", value); }
    @Override public void confirmVersion(String id, Long count, String checksum, String actor) { session.update("artifact.confirmVersion", Map.of("id", id, "count", count, "checksum", checksum, "actor", actor)); }
}
