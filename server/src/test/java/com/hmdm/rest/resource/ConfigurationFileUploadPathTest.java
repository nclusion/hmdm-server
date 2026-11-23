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
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Configuration-file uploads resolve the client-supplied file name inside the customer directory.
 */
public class ConfigurationFileUploadPathTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static File resolvedFile(File resolved) throws Exception {
        assertNotNull(resolved);
        return resolved.getCanonicalFile();
    }

    @Test
    public void configFileTraversalNameIsRejected() throws Exception {
        File customerDir = temporaryFolder.newFolder("files", "cust");
        assertNull(ConfigurationFileResource.configFileTarget(customerDir, "../../../../tmp/x"));
        assertNull(ConfigurationFileResource.configFileTarget(customerDir, "../x"));
        // A leading '/' marks the base root, so the target stays inside the customer directory.
        assertEquals(new File(customerDir, "etc/passwd").getCanonicalFile(),
                resolvedFile(ConfigurationFileResource.configFileTarget(customerDir, "/etc/passwd")));
    }

    @Test
    public void configFilePlainNameResolvesInsideCustomerDirectory() throws Exception {
        File customerDir = temporaryFolder.newFolder("files", "cust");
        File target = ConfigurationFileResource.configFileTarget(customerDir, "plain.txt");
        assertEquals(new File(customerDir, "plain.txt").getCanonicalFile(), resolvedFile(target));

        // The browser sends UTF-8 bytes as ISO-8859-1 characters; the target keeps the UTF-8 name.
        String sent = new String("café.txt".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        File accented = ConfigurationFileResource.configFileTarget(customerDir, sent);
        assertEquals(new File(customerDir, "café.txt").getCanonicalFile(), resolvedFile(accented));
    }
}
