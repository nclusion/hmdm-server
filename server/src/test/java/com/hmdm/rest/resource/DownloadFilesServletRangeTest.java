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

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DownloadFilesServlet#resolveByteRange(String, long)}.
 */
public class DownloadFilesServletRangeTest {

    private static final long LENGTH = 1000L;

    @Test
    public void openEndedRange_defaultsEndToLength() {
        DownloadFilesServlet.ResolvedByteRange range =
                DownloadFilesServlet.resolveByteRange("500-", LENGTH);
        Assert.assertNotNull(range);
        Assert.assertEquals(500L, range.start);
        Assert.assertEquals(LENGTH, range.end);
        Assert.assertEquals(500L, range.contentLength);
    }

    @Test
    public void closedRange_preservesHistoricalHalfOpenLength() {
        DownloadFilesServlet.ResolvedByteRange range =
                DownloadFilesServlet.resolveByteRange("0-499", LENGTH);
        Assert.assertNotNull(range);
        Assert.assertEquals(0L, range.start);
        Assert.assertEquals(499L, range.end);
        Assert.assertEquals(499L, range.contentLength);
    }

    @Test
    public void suffixRange_selectsLastNBytes() {
        DownloadFilesServlet.ResolvedByteRange range =
                DownloadFilesServlet.resolveByteRange("-100", LENGTH);
        Assert.assertNotNull(range);
        Assert.assertEquals(900L, range.start);
        Assert.assertEquals(LENGTH, range.end);
        Assert.assertEquals(100L, range.contentLength);
    }

    @Test
    public void suffixLargerThanFile_returnsWholeFile() {
        DownloadFilesServlet.ResolvedByteRange range =
                DownloadFilesServlet.resolveByteRange("-5000", LENGTH);
        Assert.assertNotNull(range);
        Assert.assertEquals(0L, range.start);
        Assert.assertEquals(LENGTH, range.end);
        Assert.assertEquals(LENGTH, range.contentLength);
    }

    @Test
    public void startPastEof_isUnsatisfiable() {
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("1000-", LENGTH));
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("1001-", LENGTH));
    }

    @Test
    public void endEqualLength_isAllowedUnderHistoricalHalfOpen() {
        DownloadFilesServlet.ResolvedByteRange range =
                DownloadFilesServlet.resolveByteRange("0-1000", LENGTH);
        Assert.assertNotNull(range);
        Assert.assertEquals(0L, range.start);
        Assert.assertEquals(LENGTH, range.end);
        Assert.assertEquals(LENGTH, range.contentLength);
    }

    @Test
    public void endPastEof_isUnsatisfiable() {
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("0-1001", LENGTH));
    }

    @Test
    public void invertedRange_isUnsatisfiable() {
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("500-400", LENGTH));
    }

    @Test
    public void negativeClosedStart_isUnsatisfiable() {
        // Leading '-' is suffix form; "-500-600" is not a valid suffix integer.
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("-500-600", LENGTH));
    }

    @Test
    public void malformed_isUnsatisfiable() {
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("abc", LENGTH));
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("12-xy", LENGTH));
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("", LENGTH));
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("-", LENGTH));
        Assert.assertNull(DownloadFilesServlet.resolveByteRange(null, LENGTH));
    }

    @Test
    public void emptyFile_isUnsatisfiable() {
        Assert.assertNull(DownloadFilesServlet.resolveByteRange("0-", 0L));
    }

    @Test
    public void takesFirstRangeWhenCommaSeparated() {
        DownloadFilesServlet.ResolvedByteRange range =
                DownloadFilesServlet.resolveByteRange("10-20,30-40", LENGTH);
        Assert.assertNotNull(range);
        Assert.assertEquals(10L, range.start);
        Assert.assertEquals(20L, range.end);
    }
}
