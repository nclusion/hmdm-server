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

import com.hmdm.notification.guice.module.NotificationLiquibaseModule;
import com.hmdm.plugin.guice.module.PluginLiquibaseModule;
import com.hmdm.plugins.audit.guice.module.AuditLiquibaseModule;
import com.hmdm.plugins.deviceinfo.guice.module.DeviceInfoLiquibaseModule;
import com.hmdm.plugins.devicelog.guice.module.DeviceLogLiquibaseModule;
import com.hmdm.plugins.devicelog.persistence.postgres.guice.module.DeviceLogPostgresLiquibaseModule;
import com.hmdm.plugins.messaging.guice.module.MessagingLiquibaseModule;
import com.hmdm.plugins.push.guice.module.PushLiquibaseModule;
import com.hmdm.plugins.xtra.guice.module.XtraLiquibaseModule;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.InputStreamList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * <p>Tests every change log that the deployed application applies at start-up.</p>
 *
 * <p>The server module depends on all modules that subclass {@link AbstractLiquibaseModule}, so this test sees the
 * same aggregate classpath as the deployed WAR. Two properties are checked for each module:</p>
 *
 * <ul>
 *     <li>the resource path is classpath-relative and resolves to exactly one resource, which a per-module test
 *     cannot show, because a basename collision is only visible when all modules share one classpath;</li>
 *     <li>every change set records the expected logical file path. Liquibase writes this value to the
 *     <code>FILENAME</code> column of <code>DATABASECHANGELOG</code> and matches on it. A change to the value makes
 *     Liquibase apply every change set again to a database that already has the tables.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class ChangeLogResourceTest {

    /**
     * <p>Gets the module and the logical file path to check, one pair for each deployed change log.</p>
     *
     * <p>Four modules share the <code>db.changelog.xml</code> logical file path, so their change sets share one
     * <code>FILENAME</code> identity space. {@link ChangeSetIdentityTest} reads this list and proves that no two
     * change sets in that space collide.</p>
     *
     * @return the test data, as module name, module and expected logical file path triples.
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> changeLogs() {
        return Arrays.asList(new Object[][]{
                {"server", new LiquibaseModule(null), "db.changelog.xml"},
                {"plugin platform", new PluginLiquibaseModule(null), "db.changelog.xml"},
                {"devicelog core", new DeviceLogLiquibaseModule(null), "db.changelog.xml"},
                {"devicelog postgres", new DeviceLogPostgresLiquibaseModule(null), "db.changelog.xml"},
                {"notification", new NotificationLiquibaseModule(null), "notification.changelog.xml"},
                {"audit", new AuditLiquibaseModule(null), "audit.changelog.xml"},
                {"deviceinfo", new DeviceInfoLiquibaseModule(null), "deviceinfo.changelog.xml"},
                {"messaging", new MessagingLiquibaseModule(null), "messaging.changelog.xml"},
                {"push", new PushLiquibaseModule(null), "push.changelog.xml"},
                {"xtra", new XtraLiquibaseModule(null), "xtra.changelog.xml"},
        });
    }

    /**
     * <p>The name of the module under test, used to label the test run.</p>
     */
    @Parameterized.Parameter
    public String moduleName;

    /**
     * <p>The module under test.</p>
     */
    @Parameterized.Parameter(1)
    public AbstractLiquibaseModule module;

    /**
     * <p>The logical file path that the change log of the module under test must declare.</p>
     */
    @Parameterized.Parameter(2)
    public String expectedLogicalFilePath;

    /**
     * <p>Proves that the change log path of the module resolves to exactly one classpath resource.</p>
     */
    @Test
    public void testChangeLogPathResolvesToOneResource() throws Exception {
        String changeLogPath = this.module.getChangeLogResourcePath();

        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor();
             InputStreamList streams = accessor.openStreams(null, changeLogPath)) {
            assertEquals("Change log path must resolve to exactly one classpath resource: " + changeLogPath,
                    1, streams.size());
        }
    }

    /**
     * <p>Proves that every change set of the module records the expected logical file path.</p>
     */
    @Test
    public void testChangeSetsRecordExpectedLogicalFilePath() throws Exception {
        String changeLogPath = this.module.getChangeLogResourcePath();

        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor()) {
            ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(changeLogPath, accessor);
            DatabaseChangeLog changeLog = parser.parse(changeLogPath, new ChangeLogParameters(), accessor);

            assertFalse("Change log must contain change sets: " + changeLogPath, changeLog.getChangeSets().isEmpty());
            for (ChangeSet changeSet : changeLog.getChangeSets()) {
                assertEquals("Change set " + changeSet.getId() + " must record the logical file path. A different"
                                + " value makes Liquibase apply this change set again to an existing database.",
                        this.expectedLogicalFilePath, changeSet.getFilePath());
            }
        }
    }
}
