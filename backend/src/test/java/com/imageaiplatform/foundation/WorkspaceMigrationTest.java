package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkspaceMigrationTest {
    @Test
    void upgradesExistingDatasetsWithoutAssigningASpaceAndEnforcesWorkspaceRelations() {
        var old = Flyway.configure()
                .dataSource("jdbc:h2:mem:workspace-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").target("9").load();
        old.migrate();
        var source = old.getConfiguration().getDataSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('owner-key', 'owner-login', 'test-only')");
        jdbc.update("INSERT INTO dataset (dataset_id, name, created_by) VALUES ('legacy', '기존', 'owner-key')");
        var original = jdbc.queryForMap("SELECT * FROM dataset WHERE dataset_id='legacy'");

        var current = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").target("10").load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        var upgraded = jdbc.queryForMap("SELECT * FROM dataset WHERE dataset_id='legacy'");
        assertThat(upgraded).containsAllEntriesOf(original);
        assertThat(upgraded.get("WORKSPACE_ID")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_member", Integer.class)).isZero();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO workspace (workspace_id, name, owner_user_key) VALUES ('space', '공간', 'missing')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO workspace (workspace_id, name, owner_user_key) VALUES ('space', '공간', 'owner-key')");
        jdbc.update("INSERT INTO workspace_member (workspace_id, user_key, role_code, status, joined_at) "
                + "VALUES ('space', 'owner-key', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP)");
        jdbc.update("UPDATE dataset SET workspace_id='space' WHERE dataset_id='legacy'");
        for (String invalid : new String[] {
                "UPDATE dataset SET workspace_id='missing' WHERE dataset_id='legacy'",
                "DELETE FROM workspace WHERE workspace_id='space'",
                "INSERT INTO workspace_member (workspace_id, user_key, requested_at) VALUES ('space', 'owner-key', CURRENT_TIMESTAMP)",
                "UPDATE workspace_member SET role_code='OWNER'",
                "UPDATE workspace_member SET joined_at=NULL",
                "UPDATE workspace_member SET reviewed_by='owner-key'",
                "UPDATE workspace_member SET status='PENDING', joined_at=NULL, requested_at=NULL"
        }) {
            assertThatThrownBy(() -> jdbc.update(invalid)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(current.migrate().migrationsExecuted).isZero();
        var latest = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").target("13").load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(jdbc.queryForMap("SELECT * FROM dataset WHERE dataset_id='legacy'")).isEqualTo(original);
        var linked = jdbc.queryForMap("SELECT * FROM dataset_workspace WHERE dataset_id='legacy'");
        assertThat(linked.get("WORKSPACE_ID")).isEqualTo("space");
        for (String column : new String[] {"CREATED_AT", "UPDATED_AT", "CREATED_BY", "UPDATED_BY"}) {
            assertThat(linked.get(column)).isEqualTo(original.get(column));
        }
        assertThat(latest.migrate().migrationsExecuted).isZero();
        jdbc.update("INSERT INTO workspace (workspace_id, name, owner_user_key) VALUES ('space-b', '두 번째 공간', 'owner-key')");
        jdbc.update("INSERT INTO dataset_workspace (dataset_id, workspace_id) VALUES ('legacy', 'space-b')");
        jdbc.update("INSERT INTO dataset (dataset_id, name) VALUES ('unassigned', '미귀속')");
        var independent = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").load();
        assertThat(independent.migrate().migrationsExecuted).isEqualTo(3);
        var split = jdbc.queryForList("SELECT * FROM dataset WHERE workspace_id IS NOT NULL ORDER BY workspace_id");
        assertThat(split).hasSize(2);
        assertThat(split.get(0).get("DATASET_ID")).isEqualTo("legacy");
        assertThat(split.get(1).get("DATASET_ID")).isNotEqualTo("legacy");
        assertThat(split.get(1).get("WORKSPACE_ID")).isEqualTo("space-b");
        for (var copy : split) {
            for (String column : new String[] {"NAME", "DESCRIPTION", "CREATED_AT", "UPDATED_AT", "CREATED_BY", "UPDATED_BY"}) {
                assertThat(copy.get(column)).isEqualTo(original.get(column));
            }
        }
        assertThat(jdbc.queryForObject("SELECT workspace_id FROM dataset WHERE dataset_id='unassigned'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='DATASET_WORKSPACE'", Integer.class)).isZero();
        assertThat(independent.migrate().migrationsExecuted).isZero();
        var unassigned = jdbc.queryForMap("SELECT * FROM dataset WHERE dataset_id='unassigned'");
        jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('local-admin', 'admin', 'test-only')");
        var local = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common", "classpath:db/seed/app/local").load();
        local.migrate();
        var moved = jdbc.queryForMap("SELECT * FROM dataset WHERE dataset_id='unassigned'");
        assertThat(moved.get("WORKSPACE_ID")).isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThat(moved.get("CREATED_AT")).isEqualTo(unassigned.get("CREATED_AT"));
        assertThat(moved.get("CREATED_BY")).isEqualTo(unassigned.get("CREATED_BY"));
        assertThat(moved.get("UPDATED_BY")).isEqualTo("0");
        assertThat(jdbc.queryForList("SELECT * FROM dataset WHERE workspace_id IN ('space', 'space-b') ORDER BY workspace_id")).isEqualTo(split);
        assertThat(local.migrate().migrationsExecuted).isZero();
    }
}
