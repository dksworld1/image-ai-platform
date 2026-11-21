package com.imageaiplatform.artifact.dao;

import com.imageaiplatform.artifact.domain.Artifact;
import com.imageaiplatform.artifact.domain.ArtifactFile;
import com.imageaiplatform.artifact.domain.ArtifactSet;
import com.imageaiplatform.artifact.domain.ArtifactSetVersion;
import com.imageaiplatform.artifact.domain.ArtifactSetMember;
import java.util.List;

public interface ArtifactDao {
    void insertArtifact(Artifact value);
    Artifact artifact(String id);
    Artifact lockArtifact(String id);
    List<Artifact> artifacts(String workspaceId);
    void insertFile(ArtifactFile value);
    List<ArtifactFile> files(String id);
    ArtifactFile file(String artifactId, String fileId);
    void artifactStatus(String id, String status, String actor);
    void insertSet(ArtifactSet value);
    ArtifactSet set(String id);
    ArtifactSet lockSet(String id);
    List<ArtifactSet> sets(String workspaceId);
    void insertVersion(ArtifactSetVersion value);
    ArtifactSetVersion version(String id);
    ArtifactSetVersion lockVersion(String id);
    List<ArtifactSetVersion> versions(String id);
    Integer nextVersionNumber(String id);
    List<ArtifactSetMember> members(String id);
    void deleteMembers(String id);
    void insertMember(ArtifactSetMember value);
    void confirmVersion(String id, Long count, String checksum, String actor);
}
