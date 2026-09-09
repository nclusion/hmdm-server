package com.hmdm.liquibase;

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
 * CHW-332: the server changelog must carry the AlloyDB index-advisor indexes
 * on devices.oldNumber and configurationApplications.applicationVersionId.
 * Parsing through Liquibase's own XML parser proves the changelog stays
 * loadable by the runtime, not merely well-formed.
 */
public class DbChangelogIndexTests {

    private static DatabaseChangeLog parseChangeLog() throws Exception {
        return new XMLChangeLogSAXParser().parse(
                "liquibase/db.changelog.xml",
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

    private static void assertIndexChangeSet(String id, String expectedIndexSql) throws Exception {
        DatabaseChangeLog log = parseChangeLog();
        ChangeSet changeSet = findById(log, id);
        assertNotNull("changeSet " + id + " is missing from db.changelog.xml", changeSet);
        assertTrue("changeSet " + id + " must set runInTransaction=\"false\" so CREATE INDEX"
                + " CONCURRENTLY can run outside a transaction",
                !changeSet.isRunInTransaction());
        assertTrue("changeSet " + id + " must run in the common context like its siblings",
                changeSet.getContexts().getContexts().contains("common"));
        String sql = normalizedSql(changeSet);
        assertTrue("changeSet " + id + " SQL must contain \"" + expectedIndexSql
                + "\" but was: " + sql,
                sql.contains(expectedIndexSql));
    }

    @Test
    public void devicesOldNumberIndex() throws Exception {
        assertIndexChangeSet("09.09.26-12:00",
                "create index concurrently if not exists devices_oldnumber_idx on devices (oldnumber)");
    }

    @Test
    public void configurationApplicationsApplicationVersionIdIndex() throws Exception {
        assertIndexChangeSet("09.09.26-12:01",
                "create index concurrently if not exists configurationapplications_applicationversionid_idx"
                        + " on configurationapplications (applicationversionid)");
    }
}
