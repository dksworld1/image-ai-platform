package com.imageaiplatform.dataset.dao.impl;

import com.imageaiplatform.dataset.dao.DatasetVersionDao;
import com.imageaiplatform.dataset.domain.DatasetVersion;
import com.imageaiplatform.dataset.domain.DatasetVersionEntry;
import com.imageaiplatform.dataset.domain.DatasetVersionLocation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDatasetVersionDao implements DatasetVersionDao {
    private final SqlSession session;
    public MyBatisDatasetVersionDao(@Qualifier("appSqlSessionTemplate") SqlSession session) { this.session = session; }
    @Override public void insert(DatasetVersion version) { session.insert("datasetVersion.insert", version); }
    @Override public Integer nextNumber(String datasetId) { return session.selectOne("datasetVersion.nextNumber", datasetId); }
    @Override public Boolean exists(String datasetId) { return session.selectOne("datasetVersion.exists", datasetId); }
    @Override public List<DatasetVersion> list(String datasetId) { return session.selectList("datasetVersion.list", datasetId); }
    @Override public DatasetVersion find(String datasetId, String versionId) { return session.selectOne("datasetVersion.find", Map.of("datasetId", datasetId, "versionId", versionId)); }
    @Override public void snapshotImages(String datasetId, String versionId, OffsetDateTime now, String actor) { session.insert("datasetVersion.snapshotImages", Map.of("datasetId", datasetId, "versionId", versionId, "now", now, "actor", actor)); }
    @Override public void snapshotFiles(String datasetId, String versionId, OffsetDateTime now, String actor) { session.insert("datasetVersion.snapshotFiles", Map.of("datasetId", datasetId, "versionId", versionId, "now", now, "actor", actor)); }
    @Override public List<DatasetVersionEntry> entries(String versionId) { return session.selectList("datasetVersion.entries", versionId); }
    @Override public DatasetVersionLocation imageLocation(String versionId, String entryId) { return session.selectOne("datasetVersion.imageLocation", Map.of("versionId", versionId, "entryId", entryId)); }
    @Override public DatasetVersionLocation fileLocation(String versionId, String entryId) { return session.selectOne("datasetVersion.fileLocation", Map.of("versionId", versionId, "entryId", entryId)); }
    @Override public Integer restoreImage(String datasetId, DatasetVersionEntry entry, OffsetDateTime now, String actor) {
        return session.update("datasetVersion.restoreImage", Map.of("datasetId", datasetId, "entry", entry, "now", now, "actor", actor));
    }
    @Override public Integer restoreFile(String datasetId, DatasetVersionEntry entry, OffsetDateTime now, String actor) {
        return session.update("datasetVersion.restoreFile", Map.of("datasetId", datasetId, "entry", entry, "now", now, "actor", actor));
    }
    @Override public Integer restoreMetadata(DatasetVersion version, OffsetDateTime now, String actor) {
        return session.update("datasetVersion.restoreMetadata", Map.of("version", version, "now", now, "actor", actor));
    }
}
