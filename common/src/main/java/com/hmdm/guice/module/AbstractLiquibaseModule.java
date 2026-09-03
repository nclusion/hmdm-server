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

import com.google.inject.AbstractModule;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * <p>An abstract module used for initializing or modifying the database based on the provided Liquibase change log.</p>
 *
 * @author isv
 */
public abstract class AbstractLiquibaseModule extends AbstractModule {

    /**
     * <p>A context for module usage.</p>
     */
    protected final ServletContext context;

    /**
     * <p>A logger to be use for logging the details on database initialization.</p>
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * <p>Constructs new <code>AbstractLiquibaseModule</code> instance for use in specified context.</p>
     *
     * @param context a context for module usage.
     */
    public AbstractLiquibaseModule(ServletContext context) {
        this.context = context;
    }

    /**
     * <p>Configures this module. Applies the recent changes from the {@link #getChangeLogResourcePath()} change log to
     * database.</p>
     */
    protected final void configure() {
        try (Connection connection = this.getConnection();
             ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(getChangeLogResourcePath(), resourceAccessor, database);
            String usageScenario = this.context.getInitParameter("usage.scenario");
            String contexts = getContexts(usageScenario);
            liquibase.update(contexts);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>Gets the list of <code>Liquibaase</code> contexts to be applied based on specified usage scenario.</p>
     *
     * @param usageScenario  usage scenario.
     * @return a comma-separated list of <code>Liquibase</code> contexts to be applied.
     */
    protected String getContexts(String usageScenario) {
        if ("shared".equalsIgnoreCase(usageScenario)) {
            return "common,shared";
        } else if ("private".equalsIgnoreCase(usageScenario)) {
            return "common,private";
        } else {
            log.error("Usage scenario is not valid: {}", usageScenario);
            throw new RuntimeException("Invalid usage scenario specified: " + usageScenario);
        }
    }

    /**
     * <p>Gets the path to the DB change log to be used by this module.</p>
     *
     * <p>The path is read from the classpath, so it must be relative and must have no leading slash, for example
     * <code>liquibase/audit.changelog.xml</code>. The path must find exactly one resource on the classpath of the
     * deployed application, which holds the change log of every module.</p>
     *
     * <p>The path is not the value that Liquibase writes to the <code>FILENAME</code> column of
     * <code>DATABASECHANGELOG</code>. The change log sets that value with its <code>logicalFilePath</code>
     * attribute, which every change log must declare. Liquibase matches an applied change set on that value, so a
     * change log that drops the attribute makes Liquibase apply every change set again to a database that already
     * has the tables.</p>
     *
     * @return a path to resource with Db change log.
     */
    protected abstract String getChangeLogResourcePath();

    /**
     * <p>Connects to target database using the parameters from the context.</p>
     *
     * @return a connection to target database.
     */
    private Connection getConnection() {
        try {
            Class.forName(this.context.getInitParameter("JDBC.driver"));
            return DriverManager.getConnection(
                    this.context.getInitParameter("JDBC.url"),
                    this.context.getInitParameter("JDBC.username"),
                    this.context.getInitParameter("JDBC.password")
            );
        } catch (Exception e) {
            log.error("Error during open JDBC connection", e);
            throw new RuntimeException(e);
        }
    }
}
