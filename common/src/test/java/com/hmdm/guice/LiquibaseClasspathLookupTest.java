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

package com.hmdm.guice;

import com.hmdm.guice.module.AbstractLiquibaseModule;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.InputStreamList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * <p>Tests that a change log held as a file in a directory on the classpath is found and parsed.</p>
 *
 * <p>A deployment holds a change log in one of two layouts. The server module holds it as a file below
 * <code>WEB-INF/classes</code>, and a plugin holds it as an entry in a JAR. Liquibase reaches the two layouts
 * through different code, so each layout needs its own test. This test covers the directory, and
 * <code>PluginChangelogJarTest</code> in the plugin platform covers the JAR.</p>
 *
 * <p>The accessor is built with the constructor that {@link AbstractLiquibaseModule} uses, which reads the thread
 * context class loader. The servlet container sets that class loader, so the test sets it as well.</p>
 */
public class LiquibaseClasspathLookupTest {

    /**
     * <p>The path of the change log below the classpath directory.</p>
     */
    private static final String CHANGELOG_PATH = "liquibase/test-exploded.changelog.xml";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * <p>Proves that a change log in a directory on the classpath is found exactly once and parses into the change
     * sets it declares.</p>
     */
    @Test
    public void testExplodedChangelogIsFoundAndParsed() throws Exception {
        Path classesRoot = this.tempFolder.newFolder("classes").toPath();
        Path changeLog = classesRoot.resolve(CHANGELOG_PATH);
        Files.createDirectories(changeLog.getParent());
        Files.write(changeLog, buildChangeLog().getBytes(StandardCharsets.UTF_8));

        URL[] classpath = {classesRoot.toUri().toURL()};
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader classLoader = new URLClassLoader(classpath, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor()) {
                try (InputStreamList streams = accessor.openStreams(null, CHANGELOG_PATH)) {
                    assertEquals("Change log in the classpath directory must be found exactly once",
                            1, streams.size());
                }

                ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(CHANGELOG_PATH, accessor);
                DatabaseChangeLog parsed = parser.parse(CHANGELOG_PATH, new ChangeLogParameters(), accessor);

                assertNotNull("Parsed change log must not be null", parsed);
                assertEquals("Parsed change log must contain the declared change set",
                        1, parsed.getChangeSets().size());
                assertEquals("Change set must record the logical file path declared by the change log",
                        "test-exploded.changelog.xml", parsed.getChangeSets().get(0).getFilePath());
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    /**
     * <p>Builds a minimal change log, in the form that the change log of a deployed module uses.</p>
     *
     * @return the text of the change log.
     */
    private String buildChangeLog() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<databaseChangeLog\n"
                + "        xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n"
                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "        xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog\n"
                + "        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.0.xsd\"\n"
                + "        logicalFilePath=\"test-exploded.changelog.xml\">\n"
                + "    <changeSet id=\"exploded-test-1\" author=\"test\">\n"
                + "        <createTable tableName=\"test_table\">\n"
                + "            <column name=\"id\" type=\"INTEGER\"/>\n"
                + "        </createTable>\n"
                + "    </changeSet>\n"
                + "</databaseChangeLog>\n";
    }
}
