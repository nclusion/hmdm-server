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
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * <p>Validates and opens external HTTP(S) URLs for server-side fetches (e.g. configuration-file
 * checksums) while rejecting common SSRF targets.</p>
 *
 * <p>Guards: http/https scheme allowlist; address denylist (see
 * {@link #isBlockedAddress(InetAddress)}); rejection when any resolved address is blocked;
 * HTTP redirects are not followed.</p>
 *
 * <p>Limitation: validation and connection resolve DNS independently, so a name served with a
 * short TTL can pass validation and then rebind to a blocked address (DNS rebinding). The
 * address checks are airtight for IP-literal URLs and best-effort for hostname URLs; the URLs
 * handled here are entered by authenticated administrators, not anonymous callers.</p>
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
     * <p>Returns {@code true} when the address must not be contacted from the server: wildcard,
     * loopback, link-local, site-local (RFC 1918), multicast, CGNAT {@code 100.64.0.0/10}, and
     * IPv6 ULA {@code fc00::/7}. The last two need manual range checks because the
     * {@link InetAddress} predicates miss them (IPv6 "site-local" covers only the deprecated
     * {@code fec0::/10}).</p>
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
     * <p>Returns {@code true} when {@code externalUrl} is http(s), its host resolves, and no
     * resolved address is blocked (see {@link #isBlockedAddress(InetAddress)}).</p>
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

            return allAddressesSafe(InetAddress.getAllByName(host));
        } catch (URISyntaxException | UnknownHostException e) {
            return false;
        }
    }

    /**
     * <p>Returns {@code true} only when at least one address is present and none is blocked,
     * so a host is rejected if any of its resolved addresses is blocked.</p>
     */
    static boolean allAddressesSafe(InetAddress[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return false;
        }
        for (int i = 0; i < addresses.length; i++) {
            if (isBlockedAddress(addresses[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>Creates an unconnected HTTP(S) connection with redirect following disabled and the
     * standard connect/read timeouts, so one unresponsive host cannot stall the calling thread.</p>
     */
    public static HttpURLConnection createHttpConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        if (!(connection instanceof HttpURLConnection)) {
            throw new IOException("Expected HTTP(S) connection for " + url);
        }
        HttpURLConnection http = (HttpURLConnection) connection;
        http.setInstanceFollowRedirects(false);
        http.setConnectTimeout(30000);
        http.setReadTimeout(30000);
        http.setUseCaches(false);
        http.setAllowUserInteraction(false);
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
            throw new IOException("Rejected unsafe external URL: " + sanitizeForLog(externalUrl));
        }

        URL url = new URL(externalUrl);
        HttpURLConnection http = createHttpConnection(url);
        try {
            http.connect();
            int status = http.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new IOException("Rejected HTTP redirect from external URL (status " + status + ")");
            }
            if (status >= 400) {
                throw new IOException("External URL returned HTTP " + status);
            }
            return http.getInputStream();
        } catch (IOException e) {
            http.disconnect();
            throw e;
        }
    }
}
