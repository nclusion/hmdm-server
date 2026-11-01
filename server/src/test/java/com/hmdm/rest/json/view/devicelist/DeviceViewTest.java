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

package com.hmdm.rest.json.view.devicelist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.persistence.domain.Device;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * <p>Tests the launcher keys of the <code>info</code> object that the device list and the device-by-number
 * endpoints return.</p>
 *
 * <p>Consumers outside this server read <code>launcherType</code> and <code>launcherPackage</code> to tell which
 * launcher sent a check-in. The keys reach the response only through the getters of {@link DeviceInfoView},
 * because the view hides the wrapped device info.</p>
 */
public class DeviceViewTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * <p>Proves that the launcher keys that a device sends appear in the serialized view.</p>
     */
    @Test
    public void testLauncherKeysAreSerialized() throws Exception {
        JsonNode info = serializedInfo(
                "{\"defaultLauncher\":true,\"launcherType\":\"opensource\",\"launcherPackage\":\"com.nclusion.launcher\"}");

        assertEquals("opensource", info.path("launcherType").asText());
        assertEquals("com.nclusion.launcher", info.path("launcherPackage").asText());
    }

    /**
     * <p>Proves that an empty launcher package, which a device sends when it has no default launcher, stays an
     * empty string.</p>
     */
    @Test
    public void testEmptyLauncherPackageIsKept() throws Exception {
        JsonNode info = serializedInfo("{\"defaultLauncher\":false,\"launcherPackage\":\"\"}");

        assertTrue(info.has("launcherPackage"));
        assertEquals("", info.path("launcherPackage").asText());
    }

    /**
     * <p>Proves that the launcher keys are absent when a device did not send them, as older launchers do not.</p>
     */
    @Test
    public void testMissingLauncherKeysAreOmitted() throws Exception {
        JsonNode info = serializedInfo("{\"defaultLauncher\":true}");

        assertFalse(info.has("launcherType"));
        assertFalse(info.has("launcherPackage"));
    }

    private JsonNode serializedInfo(String deviceInfoJson) throws Exception {
        Device device = new Device();
        device.setNumber("device-1");
        device.setInfo(deviceInfoJson);

        JsonNode view = mapper.readTree(mapper.writeValueAsString(new DeviceView(device)));
        assertTrue("The view has no info object", view.has("info"));
        return view.get("info");
    }
}
