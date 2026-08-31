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
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

/**
 * <p>Tests for {@link ExternalUrlAccess} SSRF controls used when checksumming configuration files.</p>
 */
public class ExternalUrlAccessTests {

    @Test
    public void rejectsNonHttpSchemes() {
        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("ftp://example.com/file"));
        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("file:///etc/passwd"));
        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("not-a-url"));
        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl(null));
    }

    @Test
    public void rejectsLoopbackLinkLocalAndSiteLocal() throws Exception {
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("127.0.0.1")));
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("169.254.169.254")));
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("10.0.0.1")));
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("192.168.1.1")));
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("172.16.0.1")));

        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("http://127.0.0.1/"));
        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("http://169.254.169.254/latest/meta-data/"));
        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("http://10.0.0.1/payload"));
    }

    @Test
    public void rejectsCarrierGradeNatAndIpv6Ula() throws Exception {
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("100.64.0.1")));
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("100.127.255.254")));
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("fd00::1")));
        Assert.assertTrue(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("fc00::1")));

        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("http://100.64.0.1/"));
        Assert.assertFalse(ExternalUrlAccess.isSafeExternalUrl("http://[fd00::1]/"));
    }

    @Test
    public void allowsPublicUnicastLiterals() throws Exception {
        Assert.assertFalse(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("8.8.8.8")));
        Assert.assertFalse(ExternalUrlAccess.isBlockedAddress(InetAddress.getByName("1.1.1.1")));
        Assert.assertTrue(ExternalUrlAccess.isSafeExternalUrl("http://8.8.8.8/"));
        Assert.assertTrue(ExternalUrlAccess.isSafeExternalUrl("https://1.1.1.1/"));
    }

    @Test
    public void httpConnectionDisablesRedirectFollowing() throws Exception {
        HttpURLConnection connection = ExternalUrlAccess.createHttpConnection(new URL("http://example.com/"));
        try {
            Assert.assertFalse(connection.getInstanceFollowRedirects());
        } finally {
            connection.disconnect();
        }
    }

    @Test
    public void sanitizeForLogStripsNewlines() {
        Assert.assertEquals("a_b_c", ExternalUrlAccess.sanitizeForLog("a\nb\rc"));
        Assert.assertNull(ExternalUrlAccess.sanitizeForLog(null));
    }

    @Test
    public void openValidatedStreamRejectsLoopback() {
        try {
            ExternalUrlAccess.openValidatedStream("http://127.0.0.1/");
            Assert.fail("expected IOException for loopback URL");
        } catch (java.io.IOException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("rejected")
                    || expected.getMessage().toLowerCase().contains("unsafe"));
        }
    }
}
