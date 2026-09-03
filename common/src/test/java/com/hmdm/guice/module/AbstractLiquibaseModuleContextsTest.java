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

package com.hmdm.guice.module;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * <p>Tests the mapping from the usage scenario to the Liquibase contexts.</p>
 *
 * <p>The contexts decide which change sets are applied, so a wrong mapping gives a database with the wrong tables.
 * The usage scenario comes from the <code>usage.scenario</code> context parameter of the deployment.</p>
 */
public class AbstractLiquibaseModuleContextsTest {

    /**
     * <p>A module that supplies a change log path, so the contexts can be read without a database.</p>
     */
    private static class TestLiquibaseModule extends AbstractLiquibaseModule {

        TestLiquibaseModule() {
            super(null);
        }

        @Override
        protected String getChangeLogResourcePath() {
            return "liquibase/test.changelog.xml";
        }
    }

    private final AbstractLiquibaseModule module = new TestLiquibaseModule();

    /**
     * <p>Proves that the shared scenario selects the common and shared contexts.</p>
     */
    @Test
    public void testSharedScenarioSelectsSharedContexts() {
        assertEquals("common,shared", this.module.getContexts("shared"));
    }

    /**
     * <p>Proves that the private scenario selects the common and private contexts.</p>
     */
    @Test
    public void testPrivateScenarioSelectsPrivateContexts() {
        assertEquals("common,private", this.module.getContexts("private"));
    }

    /**
     * <p>Proves that the scenario is read without regard to letter case.</p>
     */
    @Test
    public void testScenarioIsNotCaseSensitive() {
        assertEquals("common,shared", this.module.getContexts("SHARED"));
        assertEquals("common,private", this.module.getContexts("Private"));
    }

    /**
     * <p>Proves that an unknown scenario is rejected and that the message names the rejected value.</p>
     */
    @Test
    public void testUnknownScenarioIsRejected() {
        try {
            this.module.getContexts("staging");
            fail("Unknown usage scenario must be rejected");
        } catch (RuntimeException e) {
            assertEquals("Invalid usage scenario specified: staging", e.getMessage());
        }
    }

    /**
     * <p>Proves that a missing scenario is rejected. The context parameter is null when the deployment does not
     * declare it.</p>
     */
    @Test
    public void testMissingScenarioIsRejected() {
        try {
            this.module.getContexts(null);
            fail("Missing usage scenario must be rejected");
        } catch (RuntimeException e) {
            assertEquals("Invalid usage scenario specified: null", e.getMessage());
        }
    }
}
