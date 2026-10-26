package com.courseguide.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

/**
 * In-memory H2 database in MySQL compatibility mode, so services that use
 * MySQL-specific SQL (ON DUPLICATE KEY UPDATE, INSERT IGNORE, inline INDEX)
 * can be tested without a MySQL server.
 */
public final class TestDatabase {

    private TestDatabase() {}

    /** Returns a JdbcTemplate for a fresh, isolated database. */
    public static JdbcTemplate newJdbcTemplate() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1";
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        return new JdbcTemplate(ds);
    }
}
