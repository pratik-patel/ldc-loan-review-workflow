package com.ldc.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * DataSource configuration for Lambda environment.
 * Reads database credentials from environment variables.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        String url = System.getenv("DATABASE_URL");
        String username = System.getenv("DATABASE_USER");
        String password = System.getenv("DATABASE_PASSWORD");

        // Use defaults if environment variables are not set
        if (url == null) {
            url = "jdbc:postgresql://localhost:5432/ldc_loan_review";
        }
        if (username == null) {
            username = "postgres";
        }
        if (password == null) {
            password = "postgres";
        }

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
