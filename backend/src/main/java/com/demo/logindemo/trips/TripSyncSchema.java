package com.demo.logindemo.trips;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** 仅创建本模块新增表，不修改或清理任何已有业务表。 */
@Component
public class TripSyncSchema implements ApplicationRunner {
    private final DataSource dataSource;
    public TripSyncSchema(DataSource dataSource) { this.dataSource = dataSource; }
    public void run(ApplicationArguments args) {
        new ResourceDatabasePopulator(new ClassPathResource("db/trip-sync.sql")).execute(dataSource);
    }
}
