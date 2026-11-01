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

import com.hmdm.notification.guice.module.NotificationLiquibaseModule;
import com.hmdm.plugin.guice.module.PluginLiquibaseModule;
import com.hmdm.plugins.audit.guice.module.AuditLiquibaseModule;
import com.hmdm.plugins.deviceinfo.guice.module.DeviceInfoLiquibaseModule;
import com.hmdm.plugins.devicelog.guice.module.DeviceLogLiquibaseModule;
import com.hmdm.plugins.devicelog.persistence.postgres.guice.module.DeviceLogPostgresLiquibaseModule;
import com.hmdm.plugins.messaging.guice.module.MessagingLiquibaseModule;
import com.hmdm.plugins.nclusion.guice.module.NclusionLiquibaseModule;
import com.hmdm.plugins.push.guice.module.PushLiquibaseModule;
import com.hmdm.plugins.xtra.guice.module.XtraLiquibaseModule;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>A change log that the deployed application applies, with the module that applies it and the logical file
 * path that its change sets must record.</p>
 *
 * <p>Five modules share the <code>db.changelog.xml</code> logical file path, so their change sets share one
 * <code>FILENAME</code> identity space in <code>DATABASECHANGELOG</code>. {@link ChangeSetIdentityTest} proves that
 * no two change sets in that space collide.</p>
 *
 * <p>Add a module here when it joins the application. Every test that reads {@link #all()} then covers it.</p>
 */
public final class DeployedChangeLog {

    /**
     * <p>The Nclusion fork's change sets for core server tables.</p>
     */
    public static final DeployedChangeLog NCLUSION =
            new DeployedChangeLog("nclusion", new NclusionLiquibaseModule(null), "db.changelog.xml");

    /**
     * <p>The change sets of the PostgreSQL device log.</p>
     */
    public static final DeployedChangeLog DEVICELOG_POSTGRES =
            new DeployedChangeLog("devicelog postgres", new DeviceLogPostgresLiquibaseModule(null), "db.changelog.xml");

    private static final List<DeployedChangeLog> ALL = Collections.unmodifiableList(Arrays.asList(
            new DeployedChangeLog("server", new LiquibaseModule(null), "db.changelog.xml"),
            new DeployedChangeLog("plugin platform", new PluginLiquibaseModule(null), "db.changelog.xml"),
            new DeployedChangeLog("devicelog core", new DeviceLogLiquibaseModule(null), "db.changelog.xml"),
            DEVICELOG_POSTGRES,
            NCLUSION,
            new DeployedChangeLog("notification", new NotificationLiquibaseModule(null), "notification.changelog.xml"),
            new DeployedChangeLog("audit", new AuditLiquibaseModule(null), "audit.changelog.xml"),
            new DeployedChangeLog("deviceinfo", new DeviceInfoLiquibaseModule(null), "deviceinfo.changelog.xml"),
            new DeployedChangeLog("messaging", new MessagingLiquibaseModule(null), "messaging.changelog.xml"),
            new DeployedChangeLog("push", new PushLiquibaseModule(null), "push.changelog.xml"),
            new DeployedChangeLog("xtra", new XtraLiquibaseModule(null), "xtra.changelog.xml")
    ));

    private final String name;
    private final AbstractLiquibaseModule module;
    private final String logicalFilePath;

    private DeployedChangeLog(String name, AbstractLiquibaseModule module, String logicalFilePath) {
        this.name = name;
        this.module = module;
        this.logicalFilePath = logicalFilePath;
    }

    /**
     * <p>Gets every change log that the deployed application applies.</p>
     *
     * @return the change logs, in no particular order.
     */
    public static List<DeployedChangeLog> all() {
        return ALL;
    }

    /**
     * <p>Gets the classpath path from which the module reads its change log.</p>
     *
     * @return the change log resource path.
     */
    public String resourcePath() {
        return this.module.getChangeLogResourcePath();
    }

    /**
     * <p>Gets the logical file path that every change set of this change log must record.</p>
     *
     * @return the expected logical file path.
     */
    public String logicalFilePath() {
        return this.logicalFilePath;
    }

    /**
     * <p>Parses the change log the way the application does, with the parser that Liquibase selects for the
     * path and a class loader resource accessor.</p>
     *
     * @return the parsed change log.
     * @throws Exception if the change log cannot be found or parsed.
     */
    public DatabaseChangeLog parse() throws Exception {
        String path = resourcePath();
        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor()) {
            ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(path, accessor);
            return parser.parse(path, new ChangeLogParameters(), accessor);
        }
    }

    /**
     * <p>Finds a change set of this change log by its identifier.</p>
     *
     * @param id the change set identifier.
     * @return the change set, or <code>null</code> if this change log has none with that identifier.
     * @throws Exception if the change log cannot be found or parsed.
     */
    public ChangeSet findChangeSet(String id) throws Exception {
        for (ChangeSet changeSet : parse().getChangeSets()) {
            if (id.equals(changeSet.getId())) {
                return changeSet;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
