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

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * <p>Tests that the change log parser does not resolve an external XML entity.</p>
 *
 * <p>This is the property that CVE-2022-0839 is about. A parser that resolves an external entity lets a change log
 * read any file that the application server can read. The change log parser blocks the entity by default, so no
 * application code configures it. This test fails if a later change turns that default off.</p>
 *
 * <p>The test states the security property and not the way the parser reports it. A parser is secure when the
 * content of the referenced file reaches neither the parsed change log nor the error, whether the parser rejects
 * the change log or drops the entity. An assertion on the text of the error would instead fail on a Liquibase
 * release that only rewords its message.</p>
 *
 * <p>{@link #testChangeLogWithoutExternalEntityParses()} is the control. It parses the same change log with the
 * entity removed. Without it, a change log that the parser rejects for an unrelated reason, such as a schema fault
 * in the fixture, would pass the security test for the wrong reason.</p>
 */
public class ChangeLogSecureParsingTest {

    /**
     * <p>The text that the malicious change log tries to read.</p>
     */
    private static final String SECRET = "this-must-never-reach-the-parsed-change-log";

    /**
     * <p>The path of the change log that declares an external entity.</p>
     */
    private static final String XXE_PATH = "liquibase/xxe.changelog.xml";

    /**
     * <p>The path of the control change log, which declares no external entity.</p>
     */
    private static final String CONTROL_PATH = "liquibase/control.changelog.xml";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * <p>The class loader that holds the two change logs, set as the thread context class loader for the test.</p>
     */
    private URLClassLoader classLoader;

    /**
     * <p>The class loader to put back when the test ends.</p>
     */
    private ClassLoader previousClassLoader;

    /**
     * <p>Writes the two change logs to a directory on a class loader of their own.</p>
     */
    @Before
    public void setUp() throws Exception {
        Path classesRoot = this.tempFolder.newFolder("classes").toPath();

        Path secretFile = this.tempFolder.newFile("secret.txt").toPath();
        Files.write(secretFile, SECRET.getBytes(StandardCharsets.UTF_8));

        String entityDeclaration = "<!DOCTYPE databaseChangeLog [<!ENTITY xxe SYSTEM \""
                + secretFile.toUri() + "\">]>\n";
        writeChangeLog(classesRoot, XXE_PATH, entityDeclaration, "&xxe;");
        writeChangeLog(classesRoot, CONTROL_PATH, "", "control-1");

        this.previousClassLoader = Thread.currentThread().getContextClassLoader();
        this.classLoader = new URLClassLoader(new URL[]{classesRoot.toUri().toURL()}, this.previousClassLoader);
        Thread.currentThread().setContextClassLoader(this.classLoader);
    }

    /**
     * <p>Puts back the class loader that the test replaced.</p>
     */
    @After
    public void tearDown() throws Exception {
        Thread.currentThread().setContextClassLoader(this.previousClassLoader);
        this.classLoader.close();
    }

    /**
     * <p>Proves that the content of the referenced file reaches neither the parsed change log nor the error.</p>
     */
    @Test
    public void testExternalEntityIsNotResolved() throws Exception {
        String result;
        try {
            DatabaseChangeLog parsed = parse(XXE_PATH);
            StringBuilder identifiers = new StringBuilder();
            for (ChangeSet changeSet : parsed.getChangeSets()) {
                identifiers.append(changeSet.getId()).append('\n');
            }
            result = identifiers.toString();
        } catch (Exception e) {
            result = collectMessages(e);
        }

        assertFalse("Parser must not resolve the external entity, but the content of the referenced file reached the"
                + " parser output: " + result, result.contains(SECRET));
    }

    /**
     * <p>Proves that the same change log parses when the external entity is removed.</p>
     *
     * <p>This is the control for {@link #testExternalEntityIsNotResolved()}. It shows that the fixture is valid, so
     * that the security test cannot pass because the parser rejected the change log for another reason.</p>
     */
    @Test
    public void testChangeLogWithoutExternalEntityParses() throws Exception {
        DatabaseChangeLog parsed = parse(CONTROL_PATH);

        assertEquals("Control change log must contain the declared change set", 1, parsed.getChangeSets().size());
        assertEquals("Control change log must record the declared change set identifier",
                "control-1", parsed.getChangeSets().get(0).getId());
    }

    /**
     * <p>Parses the change log at the given classpath path.</p>
     *
     * @param changeLogPath a classpath-relative path of a change log.
     * @return the parsed change log.
     */
    private DatabaseChangeLog parse(String changeLogPath) throws Exception {
        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor()) {
            ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(changeLogPath, accessor);
            return parser.parse(changeLogPath, new ChangeLogParameters(), accessor);
        }
    }

    /**
     * <p>Writes a change log that carries the given document type declaration and change set identifier.</p>
     *
     * <p>The change log carries a valid schema location on purpose. Without it the parser fails on schema
     * validation before it reads the entity.</p>
     *
     * @param classesRoot a directory that acts as the root of the classpath.
     * @param changeLogPath a path of the change log below that root.
     * @param doctype a document type declaration, or an empty string for none.
     * @param changeSetId an identifier for the single change set of the change log.
     */
    private void writeChangeLog(Path classesRoot, String changeLogPath, String doctype, String changeSetId)
            throws Exception {
        String changeLog = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + doctype
                + "<databaseChangeLog\n"
                + "        xmlns=\"http://www.liquibase.org/xml/ns/dbchangelog\"\n"
                + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "        xsi:schemaLocation=\"http://www.liquibase.org/xml/ns/dbchangelog\n"
                + "        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.0.xsd\">\n"
                + "    <changeSet id=\"" + changeSetId + "\" author=\"test\">\n"
                + "        <createTable tableName=\"xxe_table\">\n"
                + "            <column name=\"id\" type=\"INTEGER\"/>\n"
                + "        </createTable>\n"
                + "    </changeSet>\n"
                + "</databaseChangeLog>\n";

        Path target = classesRoot.resolve(changeLogPath);
        Files.createDirectories(target.getParent());
        Files.write(target, changeLog.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>Joins the messages of an exception and of every cause below it.</p>
     *
     * @param e an exception to read.
     * @return the joined messages.
     */
    private String collectMessages(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
