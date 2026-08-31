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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

/**
 * <p>Validates and opens external HTTP(S) URLs for server-side fetches (e.g. configuration-file
 * checksums) while rejecting common SSRF targets.</p>
 *
 * <p>Extends the CodeQL autofix in nclusion/hmdm-server#3: scheme allowlist, private/local
 * denylist (including CGNAT and IPv6 ULA), resolve-all-or-reject, and no HTTP redirects.</p>
 */
public final class ExternalUrlAccess {

    private ExternalUrlAccess() {
    }

    /**
     * <p>Replaces CR/LF in loggable strings so user-controlled URLs cannot inject log lines.</p>
     */
    public static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', '_').replace('\r', '_');
    }

    /**
     * <p>Returns {@code true} when the address must not be contacted from the server.</p>
     */
    public static boolean isBlockedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] octets = address.getAddress();
        if (octets.length == 4) {
            int first = octets[0] & 0xff;
            int second = octets[1] & 0xff;
            // Carrier-grade NAT 100.64.0.0/10
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
        } else if (octets.length == 16) {
            int first = octets[0] & 0xff;
            // Unique local addresses fc00::/7
            if (first >= 0xfc && first <= 0xfd) {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>Returns {@code true} when {@code externalUrl} is http(s) and every resolved address is
     * globally routable unicast (not blocked).</p>
     */
    public static boolean isSafeExternalUrl(String externalUrl) {
        if (externalUrl == null || externalUrl.isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(externalUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                return false;
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return false;
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                return false;
            }
            for (int i = 0; i < addresses.length; i++) {
                if (isBlockedAddress(addresses[i])) {
                    return false;
                }
            }
            return true;
        } catch (URISyntaxException | IOException e) {
            return false;
        }
    }

    /**
     * <p>Opens an HTTP(S) connection with redirect following disabled.</p>
     */
    public static HttpURLConnection createHttpConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        if (!(connection instanceof HttpURLConnection)) {
            throw new IOException("Expected HTTP(S) connection for " + url);
        }
        HttpURLConnection http = (HttpURLConnection) connection;
        http.setInstanceFollowRedirects(false);
        return http;
    }

    /**
     * <p>Validates {@code externalUrl} then opens its body stream without following redirects.
     * Callers must close the returned stream.</p>
     *
     * @throws IOException if the URL is unsafe, the response is a redirect/error, or I/O fails
     */
    public static InputStream openValidatedStream(String externalUrl) throws IOException {
        if (!isSafeExternalUrl(externalUrl)) {
            throw new IOException("Rejected unsafe external URL");
        }

        URL url = new URL(externalUrl);
        HttpURLConnection http = createHttpConnection(url);
        http.connect();

        int status = http.getResponseCode();
        if (status >= 300 && status < 400) {
            http.disconnect();
            throw new IOException("Rejected HTTP redirect from external URL (status " + status + ")");
        }
        if (status >= 400) {
            http.disconnect();
            throw new IOException("External URL returned HTTP " + status);
        }

        return http.getInputStream();
    }
}
