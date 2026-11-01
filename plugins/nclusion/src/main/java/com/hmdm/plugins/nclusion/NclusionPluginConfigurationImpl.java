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

package com.hmdm.plugins.nclusion;

import com.google.inject.Module;
import com.hmdm.plugin.PluginConfiguration;
import com.hmdm.plugins.nclusion.guice.module.NclusionLiquibaseModule;

import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.List;

/**
 * <p>A configuration for the <code>nclusion</code> plugin, which holds the changes that the Nclusion fork makes to
 * Headwind MDM and that a plugin can carry.</p>
 *
 * <p>The plugin keeps those changes out of files that upstream Headwind MDM owns, so a sync with upstream does not
 * conflict on them. The plugin has no row in the <code>plugins</code> table, so the web UI does not list it.</p>
 *
 * <p>The server runs plugin modules after its own modules, so the change sets of this plugin run after the core
 * schema exists.</p>
 */
public class NclusionPluginConfigurationImpl implements PluginConfiguration {

    public static final String PLUGIN_ID = "nclusion";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getRootPackage() {
        return "com.hmdm.plugins.nclusion";
    }

    @Override
    public List<Module> getPluginModules(ServletContext context) {
        return Collections.singletonList(new NclusionLiquibaseModule(context));
    }
}
