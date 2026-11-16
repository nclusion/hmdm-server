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

package com.hmdm.rest.resource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Listed file paths must stay relative to the canonical customer directory, including when the
 * configured files directory is a symlink.
 */
public class FileListingPathTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void relativeDirectoryUsesCanonicalBase() throws Exception {
        File real = temporaryFolder.newFolder("real-files");
        File linkParent = temporaryFolder.newFolder("links");
        File link = new File(linkParent, "files");
        Files.createSymbolicLink(link.toPath(), real.toPath());

        File customer = new File(real, "cust");
        File nested = new File(customer, "sub");
        assertEquals(true, nested.mkdirs());
        File apk = new File(nested, "app.apk");
        assertEquals(true, apk.createNewFile());
        File rootApk = new File(customer, "root.apk");
        assertEquals(true, rootApk.createNewFile());

        File customerThroughLink = new File(link, "cust").getCanonicalFile();
        assertEquals("sub", FilesResource.relativeDirectory(customerThroughLink, apk));
        assertEquals("", FilesResource.relativeDirectory(customerThroughLink, rootApk));

        File outside = temporaryFolder.newFile("outside.apk");
        assertNull(FilesResource.relativeDirectory(customerThroughLink, outside));
    }

    @Test
    public void videoNameAndEncodingRoundTripWithJersey() throws Exception {
        assertEquals("clip.mp4", VideosResource.videoFileName("C:\\Users\\a\\clip.mp4"));
        assertEquals("b.mp4", VideosResource.videoFileName("a\\b.mp4"));
        assertNull(VideosResource.videoFileName(".."));

        String encoded = VideosResource.encodeVideoPathSegment("my video.mp4");
        assertEquals("my%20video.mp4", encoded);
        assertEquals("my video.mp4", java.net.URLDecoder.decode(encoded, "UTF-8"));
    }
}
