package com.hmdm.plugins.devicelog.postgres;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import liquibase.change.Change;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Test;

/**
 * CHW-332: plugin_devicelog_log has no secondary indexes, so the HMDM panel's
 * device-log search seq-scans the table on every page view. The plugin
 * changelog must carry a (deviceId, createTime DESC) index built
 * CONCURRENTLY. Parsing through Liquibase's own XML parser proves the
 * changelog stays loadable by the runtime, not merely well-formed.
 */
public class DeviceLogChangelogIndexTests {

    private static final String CHANGESET_ID = "plugin-devicelog-09.09.2026-12:00";

    private static DatabaseChangeLog parseChangeLog() throws Exception {
        return new XMLChangeLogSAXParser().parse(
                "liquibase/devicelog.postgres.changelog.xml",
                new ChangeLogParameters(),
                new ClassLoaderResourceAccessor());
    }

    private static ChangeSet findById(DatabaseChangeLog log, String id) {
        for (ChangeSet changeSet : log.getChangeSets()) {
            if (id.equals(changeSet.getId())) {
                return changeSet;
            }
        }
        return null;
    }

    private static String normalizedSql(ChangeSet changeSet) {
        StringBuilder sql = new StringBuilder();
        for (Change change : changeSet.getChanges()) {
            if (change instanceof RawSQLChange) {
                sql.append(((RawSQLChange) change).getSql()).append(' ');
            }
        }
        return sql.toString().toLowerCase().replaceAll("\\s+", " ").trim();
    }

    @Test
    public void deviceLogSearchIndex() throws Exception {
        DatabaseChangeLog log = parseChangeLog();
        ChangeSet changeSet = findById(log, CHANGESET_ID);
        assertNotNull("changeSet " + CHANGESET_ID
                + " is missing from devicelog.postgres.changelog.xml", changeSet);
        assertTrue("changeSet " + CHANGESET_ID + " must set runInTransaction=\"false\" so"
                + " CREATE INDEX CONCURRENTLY can run outside a transaction",
                !changeSet.isRunInTransaction());
        assertTrue("changeSet " + CHANGESET_ID + " must run in the common context like its siblings",
                changeSet.getContexts().getContexts().contains("common"));
        String sql = normalizedSql(changeSet);
        assertTrue("changeSet " + CHANGESET_ID + " SQL must create the search-path index"
                + " but was: " + sql,
                sql.contains("create index concurrently if not exists"
                        + " plugin_devicelog_log_deviceid_createtime_idx"
                        + " on plugin_devicelog_log (deviceid, createtime desc)"));
    }
}
