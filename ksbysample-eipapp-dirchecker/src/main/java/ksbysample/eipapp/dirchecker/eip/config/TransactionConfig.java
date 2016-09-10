package ksbysample.eipapp.dirchecker.eip.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.transaction.PseudoTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class TransactionConfig {

    @Autowired
    private DataSource dataSource;

    @Bean
    public PseudoTransactionManager pseudoTransactionManager() {
        return new PseudoTransactionManager();
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        return new DataSourceTransactionManager(this.dataSource);
    }

}
