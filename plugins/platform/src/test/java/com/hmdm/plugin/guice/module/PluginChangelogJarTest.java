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

package com.hmdm.plugin.guice.module;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.InputStreamList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * <p>Tests that a change log inside a packaged JAR is found and parsed.</p>
 *
 * <p>A plugin is deployed as a JAR, so its change log is a JAR entry and not a file. The change log path is read
 * from the thread context class loader, which is the class loader that the servlet container sets. This test puts a
 * synthetic plugin JAR on that class loader to show that the JAR entry is reached the same way a file is.</p>
 */
public class PluginChangelogJarTest {

    /**
     * <p>The path of the change log inside the synthetic plugin JAR.</p>
     */
    private static final String CHANGELOG_PATH = "liquibase/test-plugin.changelog.xml";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * <p>Proves that a change log inside a packaged JAR is found exactly once and parses into the change sets it
     * declares.</p>
     */
    @Test
    public void testPackagedJarChangelogIsFoundAndParsed() throws Exception {
        URL jarUrl = createTestJar().toURI().toURL();
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor()) {
                try (InputStreamList streams = accessor.openStreams(null, CHANGELOG_PATH)) {
                    assertEquals("Change log inside the JAR must be found exactly once", 1, streams.size());
                }

                ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(CHANGELOG_PATH, accessor);
                DatabaseChangeLog changeLog = parser.parse(CHANGELOG_PATH, new ChangeLogParameters(), accessor);

                assertNotNull("Parsed change log must not be null", changeLog);
                assertEquals("Parsed change log must contain the declared change set",
                        1, changeLog.getChangeSets().size());
                assertEquals("Change set must record the logical file path declared by the change log",
                        "test-plugin.changelog.xml", changeLog.getChangeSets().get(0).getFilePath());
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    /**
     * <p>Creates a JAR that holds a minimal plugin change log, in the layout that a deployed plugin JAR uses.</p>
     *
     * <p>The change log declares the same schema version as the change logs of the deployed plugins, so the JAR is
     * parsed against the same schema.</p>
     *
     * @return the created JAR file.
     */
    private File createTestJar() throws IOException {
        String changeLog = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<databaseChangeLog\n"
                + "        xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n"
                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "        xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog\n"
                + "        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.0.xsd\"\n"
                + "        logicalFilePath=\"test-plugin.changelog.xml\">\n"
                + "    <changeSet id=\"plugin-test-1\" author=\"test\">\n"
                + "        <createTable tableName=\"test_table\">\n"
                + "            <column name=\"id\" type=\"INTEGER\"/>\n"
                + "        </createTable>\n"
                + "    </changeSet>\n"
                + "</databaseChangeLog>\n";
        byte[] content = changeLog.getBytes(StandardCharsets.UTF_8);

        File jarFile = this.tempFolder.newFile("test-plugin.jar");
        try (JarOutputStream jarOutput = new JarOutputStream(new FileOutputStream(jarFile))) {
            JarEntry directoryEntry = new JarEntry("liquibase/");
            directoryEntry.setSize(0);
            jarOutput.putNextEntry(directoryEntry);
            jarOutput.closeEntry();

            JarEntry changeLogEntry = new JarEntry(CHANGELOG_PATH);
            changeLogEntry.setSize(content.length);
            jarOutput.putNextEntry(changeLogEntry);
            jarOutput.write(content);
            jarOutput.closeEntry();
        }
        return jarFile;
    }
}
