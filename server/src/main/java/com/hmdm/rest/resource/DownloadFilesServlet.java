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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.hmdm.notification.rest.NotificationResource;
import com.hmdm.persistence.ApplicationDAO;
import com.hmdm.rest.filter.PublicIPFilter;
import com.hmdm.rest.json.Response;
import com.hmdm.util.CryptoUtil;
import org.apache.poi.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DownloadFilesServlet extends HttpServlet {
    private final ApplicationDAO applicationDAO;
    private final String filesDirectory;
    private final File baseDirectory;
    private final PublicIPFilter publicIPFilter;

    private boolean secureEnrollment;
    private String hashSecret;

    private static final String HEADER_ENROLLMENT_SIGNATURE = "X-Request-Signature";
    private static final String CONTENT_TYPE_APK = "application/vnd.android.package-archive";

    private static final Logger log = LoggerFactory.getLogger(DownloadFilesServlet.class);

    @Inject
    public DownloadFilesServlet(ApplicationDAO applicationDAO,
                                PublicIPFilter publicIPFilter,
                                @Named("files.directory") String filesDirectory,
                                @Named("secure.enrollment") boolean secureEnrollment,
                                @Named("hash.secret") String hashSecret) {
        this.applicationDAO = applicationDAO;
        this.filesDirectory = filesDirectory;
        this.baseDirectory = new File(filesDirectory);
        this.publicIPFilter = publicIPFilter;
        this.secureEnrollment = secureEnrollment;
        this.hashSecret = hashSecret;
        if (!this.baseDirectory.exists()) {
            this.baseDirectory.mkdirs();
        }

    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = URLDecoder.decode(req.getRequestURI(), "UTF8");
        int index = path.indexOf("/files/", 0) + "/files/".length();
        path = path.substring(index);

        if (!publicIPFilter.match(req)) {
            log.warn("Request blocked by IP: " + req.getRemoteAddr());
            resp.sendError(403);
            return;
        }

        if (secureEnrollment && !applicationDAO.isMainApp(req.getRequestURL().toString())) {
            String signature = req.getHeader(HEADER_ENROLLMENT_SIGNATURE);
            if (signature == null) {
                log.warn("No signature for file request " + path);
                resp.sendError(403);
                return;
            }
            try {
                String goodSignature = CryptoUtil.getSHA1String(hashSecret + path);
                if (!signature.equalsIgnoreCase(goodSignature)) {
                    log.warn("Wrong signature for file request " + path + ": " + signature + " Should be: " + goodSignature);
                    resp.sendError(403);
                    return;
                }
            } catch (Exception e) {
            }
        }

        File file = new File(String.format("%s/%s", this.filesDirectory, path));
        if (file.exists()) {

            long modifiedSince = req.getDateHeader("If-Modified-Since");
            if (modifiedSince != -1 && modifiedSince > file.lastModified()) {
                // Client can use cached images
                resp.setStatus(304);
                return;
            }

            String range = req.getHeader("Range");
            if (range != null && range.startsWith("bytes=")) {
                sendPartialContent(range.substring(6), file, resp);
                return;
            }

            try (InputStream input = new FileInputStream(file);
                 ServletOutputStream outputStream = resp.getOutputStream()) {
                long length = file.length();
                if (length <= 2147483647L) {
                    resp.setContentLength((int)length);
                } else {
                    resp.addHeader("Content-Length", Long.toString(length));
                }
                if (file.getAbsolutePath().endsWith(".apk")) {
                    resp.setContentType(CONTENT_TYPE_APK);
                }

                IOUtils.copy(input, outputStream);
                outputStream.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Not found: " + file.getAbsolutePath());
            resp.sendError(404);
        }

    }

    private void sendPartialContent(String rangeStr, File file, HttpServletResponse resp) {
        try {
            long length = file.length();
            ResolvedByteRange range = resolveByteRange(rangeStr, length);
            if (range == null) {
                resp.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                resp.setHeader("Content-Range", "bytes */" + length);
                return;
            }

            long start = range.start;
            long end = range.end;
            long contentLength = range.contentLength;

            InputStream input = new FileInputStream(file);
            ServletOutputStream outputStream = resp.getOutputStream();

            resp.setStatus(206);
            resp.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
            if (length <= 2147483647L) {
                resp.setContentLength((int)contentLength);
            } else {
                resp.addHeader("Content-Length", Long.toString(contentLength));
            }
            if (file.getAbsolutePath().endsWith(".apk")) {
                resp.setContentType(CONTENT_TYPE_APK);
            }

            input.skip(start);

            IOUtils.copy(input, outputStream, contentLength);
            outputStream.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the value after {@code bytes=} and validates it against {@code length}.
     * Returns {@code null} when the range is malformed or unsatisfiable (caller sends 416).
     *
     * <p>Preserves the historical half-open convention used by this servlet: when the
     * client omits the end, {@code end} defaults to {@code length} and
     * {@code contentLength = end - start}. That differs from RFC 7233 inclusive ends
     * (default end {@code length - 1}, length {@code end - start + 1}); do not change
     * without confirming launcher resume clients.</p>
     */
    static ResolvedByteRange resolveByteRange(String rangeStr, long length) {
        if (rangeStr == null || length <= 0) {
            return null;
        }

        // Multiple ranges are not supported by this servlet.
        String single = rangeStr.trim();
        int comma = single.indexOf(',');
        if (comma >= 0) {
            single = single.substring(0, comma).trim();
        }
        if (single.isEmpty()) {
            return null;
        }

        try {
            long start;
            long end;

            if (single.charAt(0) == '-') {
                // Suffix form: bytes=-N (last N bytes).
                if (single.length() == 1) {
                    return null;
                }
                long suffixLength = Long.parseLong(single.substring(1).trim());
                if (suffixLength <= 0) {
                    return null;
                }
                if (suffixLength > length) {
                    suffixLength = length;
                }
                start = length - suffixLength;
                end = length;
            } else {
                int dash = single.indexOf('-');
                if (dash <= 0) {
                    return null;
                }
                start = Long.parseLong(single.substring(0, dash).trim());
                String endPart = single.substring(dash + 1).trim();
                if (endPart.isEmpty()) {
                    end = length;
                } else {
                    end = Long.parseLong(endPart);
                }
            }

            // Half-open historical bounds: 0 <= start <= end <= length and start < length.
            // (Inclusive RFC form would be 0 <= start <= end < length after defaulting end.)
            if (start < 0 || end < start || end > length || start >= length) {
                return null;
            }

            return new ResolvedByteRange(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A byte range resolved against a resource length under this servlet's historical
     * half-open arithmetic ({@code contentLength = end - start}).
     */
    static final class ResolvedByteRange {
        final long start;
        final long end;
        final long contentLength;

        ResolvedByteRange(long start, long end) {
            this.start = start;
            this.end = end;
            this.contentLength = end - start;
        }
    }
}
