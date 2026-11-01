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

import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.InputStreamList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * <p>Tests every change log that the deployed application applies at start-up.</p>
 *
 * <p>The server module depends on all modules that subclass {@link AbstractLiquibaseModule}, so this test sees the
 * same aggregate classpath as the deployed WAR. Two properties are checked for each change log in
 * {@link DeployedChangeLog#all()}:</p>
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
     * <p>Gets the change logs to check.</p>
     *
     * @return one single-element parameter array for each deployed change log.
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> changeLogs() {
        List<Object[]> parameters = new ArrayList<>();
        for (DeployedChangeLog changeLog : DeployedChangeLog.all()) {
            parameters.add(new Object[]{changeLog});
        }
        return parameters;
    }

    /**
     * <p>The change log under test.</p>
     */
    @Parameterized.Parameter
    public DeployedChangeLog changeLog;

    /**
     * <p>Proves that the change log path of the module resolves to exactly one classpath resource.</p>
     */
    @Test
    public void testChangeLogPathResolvesToOneResource() throws Exception {
        String changeLogPath = this.changeLog.resourcePath();

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
        DatabaseChangeLog parsed = this.changeLog.parse();

        assertFalse("Change log must contain change sets: " + this.changeLog.resourcePath(),
                parsed.getChangeSets().isEmpty());
        for (ChangeSet changeSet : parsed.getChangeSets()) {
            assertEquals("Change set " + changeSet.getId() + " must record the logical file path. A different"
                            + " value makes Liquibase apply this change set again to an existing database.",
                    this.changeLog.logicalFilePath(), changeSet.getFilePath());
        }
    }
}
