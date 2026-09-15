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

package com.hmdm.plugins.devicelog.postgres;

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
 * plugin_devicelog_log's base table definition creates only the primary
 * key, so without a secondary index the HMDM panel's device-log search
 * seq-scans the table: the read path joins plugin_devicelog_log to devices
 * on deviceId and orders by createTime DESC. The plugin changelog must
 * carry a (deviceId, createTime DESC) index built CONCURRENTLY behind a
 * leading DROP that clears the INVALID index an interrupted build leaves
 * behind. Parsing through Liquibase's own XML parser proves the changelog
 * stays loadable by the runtime, not merely well-formed.
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
        assertEquals("changeSet " + CHANGESET_ID + " must keep author \"nclusion\": Liquibase"
                + " identifies a changeset by (id, author, path), so an author change re-executes"
                + " it on every deployed database",
                "nclusion", changeSet.getAuthor());
        assertFalse("changeSet " + CHANGESET_ID + " must set runInTransaction=\"false\" so"
                + " CREATE INDEX CONCURRENTLY can run outside a transaction",
                changeSet.isRunInTransaction());
        assertTrue("changeSet " + CHANGESET_ID + " must run in the common context like its siblings",
                changeSet.getContexts().getContexts().contains("common"));
        String sql = normalizedSql(changeSet);
        String expectedDropSql =
                "drop index concurrently if exists plugin_devicelog_log_deviceid_createtime_idx";
        String expectedCreateSql = "create index concurrently if not exists"
                + " plugin_devicelog_log_deviceid_createtime_idx"
                + " on plugin_devicelog_log (deviceid, createtime desc)";
        assertTrue("changeSet " + CHANGESET_ID + " SQL must contain \"" + expectedDropSql
                + "\" but was: " + sql,
                sql.contains(expectedDropSql));
        assertTrue("changeSet " + CHANGESET_ID + " SQL must contain \"" + expectedCreateSql
                + "\" but was: " + sql,
                sql.contains(expectedCreateSql));
        assertTrue("changeSet " + CHANGESET_ID + " must DROP before CREATE so a retry clears the"
                + " INVALID index an interrupted CONCURRENTLY build leaves behind, which"
                + " IF NOT EXISTS alone would silently keep; SQL was: " + sql,
                sql.indexOf(expectedDropSql) < sql.indexOf(expectedCreateSql));
    }
}
