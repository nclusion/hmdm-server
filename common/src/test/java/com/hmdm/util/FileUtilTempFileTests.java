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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * <p>A test suite for upload temp files created by {@link FileUtil}.</p>
 */
public class FileUtilTempFileTests {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void createTempFileRoundTripsNameOutsideSystemTemp() throws Exception {
        File filesDirectory = folder.newFolder("files");
        File tempFile = FileUtil.createTempFile(filesDirectory.getAbsolutePath(), "report.pdf");

        Assert.assertEquals("report.pdf", FileUtil.getNameFromTmpPath(tempFile.getAbsolutePath()));
        Assert.assertEquals(
                FileUtil.uploadTempDirectoryPath(filesDirectory.getAbsolutePath()),
                tempFile.getParentFile().getAbsolutePath());
        Assert.assertEquals(
                filesDirectory.getParentFile().getAbsolutePath(),
                tempFile.getParentFile().getParentFile().getAbsolutePath());
        Assert.assertNotEquals(
                new File(System.getProperty("java.io.tmpdir")).getCanonicalPath(),
                tempFile.getParentFile().getCanonicalPath());
    }

    @Test
    public void sanitizeKeepsPrefixInsideUploadDirectory() throws Exception {
        File filesDirectory = folder.newFolder("files");
        File tempFile = FileUtil.createTempFile(filesDirectory.getAbsolutePath(), "a/b.pdf");

        Assert.assertEquals("a_b.pdf", FileUtil.getNameFromTmpPath(tempFile.getAbsolutePath()));
        Assert.assertFalse(FileUtil.isStagedTempPath(
                filesDirectory.getAbsolutePath(),
                tempFile.getParentFile().getAbsolutePath() + "/../../etc/passwd"));
    }

    @Test
    public void doubleDotInFileNameRoundTrips() throws Exception {
        File filesDirectory = folder.newFolder("files");
        File tempFile = FileUtil.createTempFile(filesDirectory.getAbsolutePath(), "report..final.pdf");

        Assert.assertEquals("report..final.pdf", FileUtil.getNameFromTmpPath(tempFile.getAbsolutePath()));
        Assert.assertTrue(FileUtil.isStagedTempPath(filesDirectory.getAbsolutePath(), tempFile.getAbsolutePath()));
        Assert.assertEquals(
                FileUtil.uploadTempDirectoryPath(filesDirectory.getAbsolutePath()),
                tempFile.getParentFile().getAbsolutePath());
    }

    @Test
    public void posixPermissionsAreOwnerOnly() throws Exception {
        File filesDirectory = folder.newFolder("files");
        Path tempFile = FileUtil.createTempFile(filesDirectory.getAbsolutePath(), "report.pdf").toPath();
        if (!Files.getFileStore(tempFile).supportsFileAttributeView("posix")) {
            return;
        }

        Set<PosixFilePermission> directoryPermissions = Files.getPosixFilePermissions(tempFile.getParent());
        Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(tempFile);
        Assert.assertEquals(EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE), directoryPermissions);
        Assert.assertEquals(EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE), filePermissions);
    }
}
