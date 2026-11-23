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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Video downloads resolve client-supplied names inside the video directory. The names below are the
 * values Jersey hands to {@code @PathParam}, after it percent-decodes the URL.
 */
public class VideoDownloadPathTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File videoDir;

    @Before
    public void setUp() throws Exception {
        videoDir = temporaryFolder.newFolder("videos");
        // A file next to the video directory that no lookup may reach.
        assertEquals(true, temporaryFolder.newFile("x").exists());
    }

    private File video(String name) throws Exception {
        File file = new File(videoDir, name);
        assertEquals(true, file.createNewFile());
        return file.getCanonicalFile();
    }

    private static File resolvedFile(File resolved) throws Exception {
        assertNotNull(resolved);
        return resolved.getCanonicalFile();
    }

    @Test
    public void oldStyleUrlWithPlusForSpaceResolves() throws Exception {
        File file = video("my video.mp4");
        // Stored as .../my+video.mp4; Jersey leaves '+' literal.
        assertEquals(file, resolvedFile(VideosResource.resolveVideoFile(videoDir, "my+video.mp4")));
    }

    @Test
    public void newStyleUrlResolves() throws Exception {
        File file = video("my video.mp4");
        // Stored as .../my%20video.mp4; Jersey decodes %20 to a space.
        assertEquals(file, resolvedFile(VideosResource.resolveVideoFile(videoDir, "my video.mp4")));
    }

    @Test
    public void realPlusInFileNameResolvesByLiteralName() throws Exception {
        File plus = video("a+b.mp4");
        assertEquals(plus, resolvedFile(VideosResource.resolveVideoFile(videoDir, "a+b.mp4")));

        video("a b.mp4");
        assertEquals(plus, resolvedFile(VideosResource.resolveVideoFile(videoDir, "a+b.mp4")));
    }

    @Test
    public void literalPercentInFileNameResolves() throws Exception {
        File file = video("100%.mp4");
        assertEquals(file, resolvedFile(VideosResource.resolveVideoFile(videoDir, "100%.mp4")));
    }

    @Test
    public void fallbackDecodeCannotEscapeVideoDirectory() throws Exception {
        File outside = new File(videoDir.getParentFile(), "x");
        File resolved = VideosResource.resolveVideoFile(videoDir, "..%2Fx");
        assertFalse(resolved != null && resolved.getCanonicalFile().equals(outside.getCanonicalFile()));
        assertFalse(resolved != null && resolved.isFile());

        resolved = VideosResource.resolveVideoFile(videoDir, "%2Fetc%2Fpasswd");
        assertFalse(resolved != null && resolved.isFile());
    }

    @Test
    public void malformedEscapeInFallbackIsAMiss() throws Exception {
        File resolved = VideosResource.resolveVideoFile(videoDir, "bad%zz.mp4");
        assertFalse(resolved != null && resolved.isFile());
    }

    @Test
    public void literalTraversalIsRejected() throws Exception {
        assertNull(VideosResource.resolveVideoFile(videoDir, "../x"));
    }
}
