/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.liquibase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * The server changelog must carry the AlloyDB index-advisor indexes on
 * devices.oldNumber and configurationApplications.applicationVersionId,
 * each built CONCURRENTLY behind a leading DROP that clears the INVALID
 * index an interrupted build leaves behind. Parsing through Liquibase's
 * own XML parser proves the changelog stays loadable by the runtime, not
 * merely well-formed.
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

    private static void assertIndexChangeSet(String id, String indexName, String expectedCreateSql)
            throws Exception {
        DatabaseChangeLog log = parseChangeLog();
        ChangeSet changeSet = findById(log, id);
        assertNotNull("changeSet " + id + " is missing from db.changelog.xml", changeSet);
        assertEquals("changeSet " + id + " must keep author \"nclusion\": Liquibase identifies a"
                + " changeset by (id, author, path), so an author change re-executes it on every"
                + " deployed database",
                "nclusion", changeSet.getAuthor());
        assertFalse("changeSet " + id + " must set runInTransaction=\"false\" so CREATE INDEX"
                + " CONCURRENTLY can run outside a transaction",
                changeSet.isRunInTransaction());
        assertTrue("changeSet " + id + " must run in the common context like its siblings",
                changeSet.getContexts().getContexts().contains("common"));
        String sql = normalizedSql(changeSet);
        String expectedDropSql = "drop index concurrently if exists " + indexName;
        assertTrue("changeSet " + id + " SQL must contain \"" + expectedDropSql
                + "\" but was: " + sql,
                sql.contains(expectedDropSql));
        assertTrue("changeSet " + id + " SQL must contain \"" + expectedCreateSql
                + "\" but was: " + sql,
                sql.contains(expectedCreateSql));
        assertTrue("changeSet " + id + " must DROP before CREATE so a retry clears the INVALID"
                + " index an interrupted CONCURRENTLY build leaves behind, which IF NOT EXISTS"
                + " alone would silently keep; SQL was: " + sql,
                sql.indexOf(expectedDropSql) < sql.indexOf(expectedCreateSql));
    }

    @Test
    public void devicesOldNumberIndex() throws Exception {
        assertIndexChangeSet("09.09.26-12:00", "devices_oldnumber_idx",
                "create index concurrently if not exists devices_oldnumber_idx on devices (oldnumber)");
    }

    @Test
    public void configurationApplicationsApplicationVersionIdIndex() throws Exception {
        assertIndexChangeSet("09.09.26-12:01", "configurationapplications_applicationversionid_idx",
                "create index concurrently if not exists configurationapplications_applicationversionid_idx"
                        + " on configurationapplications (applicationversionid)");
    }
}
