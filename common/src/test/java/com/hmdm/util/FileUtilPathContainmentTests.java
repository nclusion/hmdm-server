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

package com.hmdm.util;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * <p>Unit tests for {@link FileUtil#resolveContainedFile(File, String)} and related path-safety helpers.</p>
 */
public class FileUtilPathContainmentTests {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsValidNestedPath() throws IOException {
        File base = temporaryFolder.newFolder("files");
        File nestedDir = new File(base, "customer/a");
        Assert.assertTrue(nestedDir.mkdirs());
        File expected = new File(nestedDir, "app.apk");
        Assert.assertTrue(expected.createNewFile());

        File resolved = FileUtil.resolveContainedChild(base, "customer/a/app.apk");
        Assert.assertNotNull(resolved);
        Assert.assertEquals(expected.getCanonicalFile(), resolved);
    }

    @Test
    public void acceptsDotSlashPrefix() throws IOException {
        File base = temporaryFolder.newFolder("files-dot");
        File child = new File(base, "ok.txt");
        Assert.assertTrue(child.createNewFile());

        File resolved = FileUtil.resolveContainedChild(base, "./ok.txt");
        Assert.assertNotNull(resolved);
        Assert.assertEquals(child.getCanonicalFile(), resolved);
    }

    @Test
    public void acceptsLeadingSlashAsRootOfBase() throws IOException {
        File base = temporaryFolder.newFolder("files-slash");
        File nested = new File(base, "sub");
        Assert.assertTrue(nested.mkdirs());
        File child = new File(nested, "ok.txt");
        Assert.assertTrue(child.createNewFile());

        File resolved = FileUtil.resolveContainedChild(base, "/sub/ok.txt");
        Assert.assertNotNull(resolved);
        Assert.assertEquals(child.getCanonicalFile(), resolved);
        Assert.assertTrue(FileUtil.isSafePath("/sub/ok.txt"));
    }

    @Test
    public void rejectsTraversal() throws IOException {
        File base = temporaryFolder.newFolder("files-trav");
        File outside = temporaryFolder.newFile("secret.txt");
        writeBytes(outside, "secret".getBytes(StandardCharsets.UTF_8));

        Assert.assertNull(FileUtil.resolveContainedFile(base, "../secret.txt"));
        Assert.assertNull(FileUtil.resolveContainedFile(base, "sub/../../secret.txt"));
        Assert.assertNull(FileUtil.resolveContainedFile(base, "a/../../../secret.txt"));
    }

    @Test
    public void unixAbsoluteChildDoesNotEscapeBase() throws IOException {
        File base = temporaryFolder.newFolder("files-abs");
        File outside = temporaryFolder.newFile("abs-secret.txt");
        writeBytes(outside, "secret".getBytes(StandardCharsets.UTF_8));

        // A Unix absolute path is treated as a root-of-base marker (leading '/' stripped),
        // so the resolved file stays under base and is not the outside file.
        File resolved = FileUtil.resolveContainedFile(base, outside.getAbsolutePath());
        Assert.assertNotNull(resolved);
        Assert.assertTrue(resolved.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator)
                || resolved.getCanonicalPath().equals(base.getCanonicalPath()));
        Assert.assertFalse(resolved.getCanonicalFile().equals(outside.getCanonicalFile()));
    }

    @Test
    public void rejectsWindowsDriveAbsolute() throws IOException {
        File base = temporaryFolder.newFolder("files-win");
        Assert.assertNull(FileUtil.resolveContainedFile(base, "C:\\Windows\\system32\\drivers"));
        Assert.assertFalse(FileUtil.isSafePath("C:\\Windows\\system32\\drivers"));
        Assert.assertFalse(FileUtil.isSafePath("C:/Windows/system32"));
    }

    @Test
    public void rejectsEmptyOrNullUnsafeNamesForIsSafePath() {
        Assert.assertFalse(FileUtil.isSafePath(null));
        Assert.assertFalse(FileUtil.isSafePath(""));
        Assert.assertFalse(FileUtil.isSafePath(".."));
        Assert.assertFalse(FileUtil.isSafePath("a/../../b"));
        Assert.assertTrue(FileUtil.isSafePath("/"));
        Assert.assertTrue(FileUtil.isSafePath("nested/file.apk"));
        Assert.assertTrue(FileUtil.isSafePath("./nested/file.apk"));
        Assert.assertTrue(FileUtil.isSafePath("/nested/file.apk"));
    }

    @Test
    public void emptyChildResolvesToBaseButChildHelperRejects() throws IOException {
        File base = temporaryFolder.newFolder("files-empty");
        File resolved = FileUtil.resolveContainedFile(base, "");
        Assert.assertNotNull(resolved);
        Assert.assertEquals(base.getCanonicalFile(), resolved);

        File slash = FileUtil.resolveContainedFile(base, "/");
        Assert.assertNotNull(slash);
        Assert.assertEquals(base.getCanonicalFile(), slash);

        Assert.assertNull(FileUtil.resolveContainedChild(base, ""));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "/"));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "."));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "./"));
    }

    @Test
    public void confirmContainedAcceptsTempFile() throws IOException {
        File tmpBase = new File(System.getProperty("java.io.tmpdir"));
        File temp = File.createTempFile("hmdm-path-test", ".temp");
        try {
            File confirmed = FileUtil.confirmContained(tmpBase, temp.getAbsolutePath());
            Assert.assertNotNull(confirmed);
            Assert.assertEquals(temp.getCanonicalFile(), confirmed);
        } finally {
            temp.delete();
        }
    }

    @Test
    public void twoSegmentResolveKeepsContainment() throws IOException {
        File base = temporaryFolder.newFolder("files-two");
        File resolved = FileUtil.resolveContainedChild(base, "local", "name.apk");
        Assert.assertNotNull(resolved);
        Assert.assertTrue(resolved.getAbsolutePath().startsWith(base.getCanonicalFile().getAbsolutePath()));
        Assert.assertEquals("name.apk", resolved.getName());

        Assert.assertNull(FileUtil.resolveContainedChild(base, "../escape", "name.apk"));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "local", "../escape.apk"));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "local", "."));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "local", "/"));
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }
}
