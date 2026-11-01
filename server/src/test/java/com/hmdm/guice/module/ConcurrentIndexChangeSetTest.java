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

package com.hmdm.guice.module;

import liquibase.change.Change;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * <p>Tests the Nclusion change sets that build an index on a live table.</p>
 *
 * <p>Each change set builds its index <code>CONCURRENTLY</code>, so it must run outside a transaction. Each one
 * drops the index first, because an interrupted concurrent build leaves an <code>INVALID</code> index that
 * <code>IF NOT EXISTS</code> alone would keep. The test parses each change log the way the application does, so it
 * also proves that the runtime can load it.</p>
 */
@RunWith(Parameterized.class)
public class ConcurrentIndexChangeSetTest {

    /**
     * <p>Gets the index change sets to check.</p>
     *
     * @return the test data, as change log, change set identifier, index name and expected create statement.
     */
    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> indexChangeSets() {
        return Arrays.asList(new Object[][]{
                // AlloyDB index advisor recommendations for core server tables.
                {DeployedChangeLog.SERVER, "09.09.26-12:00", "devices_oldnumber_idx",
                        "create index concurrently if not exists devices_oldnumber_idx on devices (oldnumber)"},
                {DeployedChangeLog.SERVER, "09.09.26-12:01", "configurationapplications_applicationversionid_idx",
                        "create index concurrently if not exists configurationapplications_applicationversionid_idx"
                                + " on configurationapplications (applicationversionid)"},
                // The device log search joins on deviceId and orders by createTime DESC.
                {DeployedChangeLog.DEVICELOG_POSTGRES, "plugin-devicelog-09.09.2026-12:00",
                        "plugin_devicelog_log_deviceid_createtime_idx",
                        "create index concurrently if not exists plugin_devicelog_log_deviceid_createtime_idx"
                                + " on plugin_devicelog_log (deviceid, createtime desc)"},
                // The daily purge filters on (createTime)::numeric, because its cutoff comes from EXTRACT(EPOCH ...).
                {DeployedChangeLog.DEVICELOG_POSTGRES, "plugin-devicelog-28.09.2026-12:00",
                        "plugin_devicelog_log_createtime_idx",
                        "create index concurrently if not exists plugin_devicelog_log_createtime_idx"
                                + " on plugin_devicelog_log ((createtime::numeric))"},
        });
    }

    /**
     * <p>The change log that holds the change set.</p>
     */
    @Parameterized.Parameter
    public DeployedChangeLog changeLog;

    /**
     * <p>The identifier of the change set.</p>
     */
    @Parameterized.Parameter(1)
    public String changeSetId;

    /**
     * <p>The name of the index that the change set builds.</p>
     */
    @Parameterized.Parameter(2)
    public String indexName;

    /**
     * <p>The create statement that the change set must contain, in lower case with single spaces.</p>
     */
    @Parameterized.Parameter(3)
    public String expectedCreateSql;

    /**
     * <p>Proves that the change set keeps its identity, runs outside a transaction, and drops the index before it
     * creates it.</p>
     */
    @Test
    public void testIndexChangeSetIsSafeToRetry() throws Exception {
        String id = this.changeSetId;
        ChangeSet changeSet = this.changeLog.findChangeSet(id);
        assertNotNull("Change set " + id + " is missing from " + this.changeLog.resourcePath(), changeSet);

        assertEquals("Change set " + id + " must keep the author \"nclusion\". Liquibase identifies a change set by"
                + " identifier, author and path, so a new author applies it again to every deployed database.",
                "nclusion", changeSet.getAuthor());
        assertFalse("Change set " + id + " must set runInTransaction=\"false\", because CREATE INDEX CONCURRENTLY"
                + " cannot run in a transaction.", changeSet.isRunInTransaction());
        assertTrue("Change set " + id + " must run in the common context.",
                changeSet.getContexts().getContexts().contains("common"));

        String sql = normalizedSql(changeSet);
        String expectedDropSql = "drop index concurrently if exists " + this.indexName;
        assertTrue("Change set " + id + " must contain \"" + expectedDropSql + "\". SQL: " + sql,
                sql.contains(expectedDropSql));
        assertTrue("Change set " + id + " must contain \"" + this.expectedCreateSql + "\". SQL: " + sql,
                sql.contains(this.expectedCreateSql));
        assertTrue("Change set " + id + " must drop the index before it creates it. SQL: " + sql,
                sql.indexOf(expectedDropSql) < sql.indexOf(this.expectedCreateSql));
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
}
