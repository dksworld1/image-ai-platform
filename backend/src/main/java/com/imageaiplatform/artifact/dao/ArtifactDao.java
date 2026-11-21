package com.imageaiplatform.artifact.dao;

import com.imageaiplatform.artifact.domain.Artifact;
import com.imageaiplatform.artifact.domain.ArtifactFile;
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
}
