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

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * <p>Tests that no two change sets of the deployed application claim the same identity.</p>
 *
 * <p>Liquibase identifies an applied change set by the triple of author, identifier and logical file path, which it
 * writes to <code>DATABASECHANGELOG</code>. Four modules declare the same <code>db.changelog.xml</code> logical file
 * path, so their change sets share one identity space. Two change sets with the same author and identifier in
 * that space collide: Liquibase applies the first and then skips the second, because the row of the first already
 * marks the second as applied.</p>
 *
 * <p>{@link ChangeLogResourceTest} supplies the modules, so a new module joins this test when it joins that one.</p>
 */
public class ChangeSetIdentityTest {

    /**
     * <p>Proves that the author and identifier of a change set are unique within its logical file path.</p>
     */
    @Test
    public void testChangeSetIdentitiesAreUnique() throws Exception {
        Map<String, Set<String>> identitiesBySpace = new HashMap<>();
        List<String> collisions = new ArrayList<>();

        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor()) {
            for (Object[] changeLog : ChangeLogResourceTest.changeLogs()) {
                String moduleName = (String) changeLog[0];
                String changeLogPath = ((AbstractLiquibaseModule) changeLog[1]).getChangeLogResourcePath();

                ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(changeLogPath, accessor);
                DatabaseChangeLog parsed = parser.parse(changeLogPath, new ChangeLogParameters(), accessor);

                for (ChangeSet changeSet : parsed.getChangeSets()) {
                    Set<String> identities = identitiesBySpace.computeIfAbsent(
                            changeSet.getFilePath(), space -> new HashSet<>());
                    String identity = changeSet.getAuthor() + ":" + changeSet.getId();
                    if (!identities.add(identity)) {
                        collisions.add(identity + " in " + changeSet.getFilePath() + ", seen again in " + moduleName);
                    }
                }
            }
        }

        assertTrue("Change sets that share a logical file path must have different authors or identifiers, because"
                + " Liquibase skips the second change set of a colliding pair. Collisions: " + collisions,
                collisions.isEmpty());
    }
}
