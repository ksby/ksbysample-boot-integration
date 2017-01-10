package ksbysample.eipapp.datacopy.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class ApplicationConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.world")
    public DataSource dataSourceWorld() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager transactionManagerWorld() {
        return new DataSourceTransactionManager(dataSourceWorld());
    }

    @Bean
    @ConfigurationProperties("spring.datasource.ksbylending")
    public DataSource dataSourceKsbylending() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public PlatformTransactionManager transactionManagerKsbylending() {
        return new DataSourceTransactionManager(dataSourceKsbylending());
    }

}
