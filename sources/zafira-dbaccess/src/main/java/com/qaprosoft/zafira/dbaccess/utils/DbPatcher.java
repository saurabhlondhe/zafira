package com.qaprosoft.zafira.dbaccess.utils;

import com.mchange.v2.c3p0.DataSources;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class DbPatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbPatcher.class);

    private static final String SQL_FIND_ALL_TENANTS = "SELECT name FROM management.tenancies";

    @Value("${zafira.multitenant}")
    private boolean multitenant;

    @Qualifier("appDataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @PostConstruct
    private void updateDatabase() throws SQLException {
        try {
            LOGGER.info("================ Starting DB activities ================");

            List<String> schemas = Arrays.asList("zafira");

            if (multitenant) {
                ResultSet resultSet = dataSource.getConnection()
                                                .createStatement()
                                                .executeQuery(SQL_FIND_ALL_TENANTS);
                while (resultSet.next()) {
                    schemas.add(resultSet.getString("name"));
                }
            }

            MultiTenantSpringLiquibase liquibase = new MultiTenantSpringLiquibase();

            liquibase.setChangeLog("classpath:changelog.yml");
            liquibase.setDataSource(dataSource);
            liquibase.setSchemas(schemas);
            liquibase.setDefaultSchema("zafira");
            liquibase.setResourceLoader(resourceLoader);

            liquibase.afterPropertiesSet(); // method name is confusing but it actually runs liquibase magic
        } catch (Exception e) {
            LOGGER.error("Unable to execute database updates", e);
            throw new RuntimeException("Unable to execute database updates: stopping, see log for details");
        } finally {
            DataSources.destroy(dataSource);
        }
    }

}