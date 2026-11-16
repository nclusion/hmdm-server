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

import javax.inject.Inject;
import javax.inject.Singleton;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * <p>A REST API for accessing the files from mobile applications.</p>
 *
 * <p>This class must never be used. {@link DownloadFilesServlet} is used instead for downloading files from server
 * by mobile applications.</p>
 *
 * <p>Historically this endpoint built a doubled CWD-relative path and effectively always 404'd. It is intentionally
 * kept disabled: resolving the client path under {@code files.directory} without device/tenant authorization would
 * turn it into an unauthenticated cross-tenant file reader.</p>
 *
 * @author isv
 */
@Singleton
@Path("/public/files")
@Deprecated
public class PublicFilesResource {

    @Inject
    public PublicFilesResource() {
    }

    /**
     * <p>Disabled. Returns {@code 410 Gone} without reading any filesystem path derived from client input.</p>
     *
     * @param filePath a relative path to a file (ignored).
     * @return a 410 response.
     */
    @GET
    @Path("/{filePath}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public javax.ws.rs.core.Response downloadFile(@PathParam("filePath") String filePath) {
        // TODO : ISV : Needs to identify the device and do a security check if device is granted access to specified
        //  file — until then do not serve bytes from files.directory.
        return javax.ws.rs.core.Response.status(410).build();
    }
}
