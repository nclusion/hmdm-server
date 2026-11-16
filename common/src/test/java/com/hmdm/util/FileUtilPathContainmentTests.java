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

import com.hmdm.persistence.domain.Customer;
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
        Assert.assertFalse(FileUtil.isSafePath("//server/share/file"));
    }

    @Test
    public void acceptsColonInFileName() throws IOException {
        File base = temporaryFolder.newFolder("files-colon");
        File child = new File(base, "backup:1.apk");
        Assert.assertTrue(child.createNewFile());

        Assert.assertTrue(FileUtil.isSafePath("backup:1.apk"));
        Assert.assertTrue(FileUtil.isSafePath("2024:05:01 12:00:00.jpg"));
        File resolved = FileUtil.resolveContainedChild(base, "backup:1.apk");
        Assert.assertNotNull(resolved);
        Assert.assertEquals(child.getCanonicalFile(), resolved);
    }

    @Test
    public void rejectsControlCharactersWithoutThrowing() throws IOException {
        File base = temporaryFolder.newFolder("files-nul");
        Assert.assertFalse(FileUtil.isSafePath("a\u0000b"));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "a\u0000b"));
        Assert.assertNull(FileUtil.resolveContainedChild(base, "ok\u0001name.apk"));
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
        File temp = temporaryFolder.newFile("hmdm-path-test.temp");
        File confirmed = FileUtil.confirmContained(tmpBase, temp.getAbsolutePath());
        Assert.assertNotNull(confirmed);
        Assert.assertEquals(temp.getCanonicalFile(), confirmed);
    }

    @Test
    public void confirmContainedRejectsTmpPrefixEscape() throws IOException {
        File tmpBase = new File(System.getProperty("java.io.tmpdir"));
        File escaped = new File(tmpBase, "subdir/../../outside-hmdm.txt");
        Assert.assertTrue(escaped.getPath().startsWith(tmpBase.getPath())
                || escaped.getAbsolutePath().startsWith(tmpBase.getAbsolutePath()));
        Assert.assertNull(FileUtil.confirmContained(tmpBase, escaped.getPath()));

        String sibling = tmpBase.getAbsolutePath();
        if (sibling.endsWith(File.separator)) {
            sibling = sibling.substring(0, sibling.length() - 1);
        }
        Assert.assertNull(FileUtil.confirmContained(tmpBase, sibling + "-evil/file.txt"));
    }

    @Test
    public void moveFileRejectsSourceOutsideTmp() throws IOException {
        File files = temporaryFolder.newFolder("customer-files");
        String tmpProperty = System.getProperty("java.io.tmpdir");
        String separator = tmpProperty.endsWith(File.separator) ? "" : File.separator;
        // Textual prefix matches java.io.tmpdir; the canonical path does not.
        String escaped = tmpProperty + separator + "a/../../hmdm-outside-not-a-file";
        File canonical = new File(escaped).getCanonicalFile();
        File tmpCanonical = new File(tmpProperty).getCanonicalFile();
        Assert.assertFalse(canonical.equals(tmpCanonical));
        Assert.assertFalse(canonical.getPath().startsWith(tmpCanonical.getPath() + File.separator));

        Customer customer = new Customer();
        customer.setFilesDir(null);
        File moved = FileUtil.moveFile(customer, files.getAbsolutePath(), "/", escaped);
        Assert.assertNull(moved);
        Assert.assertFalse(new File(files, "hmdm-outside-not-a-file").exists());
    }

    @Test
    public void moveFileAcceptsRealTempFile() throws IOException {
        File files = temporaryFolder.newFolder("customer-files-ok");
        File temp = FileUtil.createTempFile("app.apk");
        try {
            writeBytes(temp, "apk".getBytes(StandardCharsets.UTF_8));
            Customer customer = new Customer();
            customer.setFilesDir("");
            File moved = FileUtil.moveFile(customer, files.getAbsolutePath(), "/", temp.getAbsolutePath());
            Assert.assertNotNull(moved);
            Assert.assertTrue(moved.isFile());
            Assert.assertEquals("app.apk", moved.getName());
            Assert.assertTrue(moved.getCanonicalPath().startsWith(files.getCanonicalPath() + File.separator));
            Assert.assertFalse(temp.exists());
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    @Test
    public void resolveDoesNotCreateMissingDirectories() throws IOException {
        File base = temporaryFolder.newFolder("files-no-mkdir");
        File resolved = FileUtil.resolveContainedChild(base, "a/b/c", "nope.txt");
        Assert.assertNotNull(resolved);
        Assert.assertFalse(new File(base, "a").exists());
        Assert.assertFalse(resolved.exists());

        File missingBase = new File(temporaryFolder.getRoot(), "missing-base");
        File underMissing = FileUtil.resolveContainedChild(missingBase, "child.txt");
        Assert.assertNotNull(underMissing);
        Assert.assertFalse(missingBase.exists());
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
