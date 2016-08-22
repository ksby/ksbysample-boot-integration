package ksbysample.eipapp.dirchecker.eip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.transaction.PseudoTransactionManager;

@Configuration
public class TransactionConfig {

    @Bean
    public PseudoTransactionManager pseudoTransactionManager() {
        return new PseudoTransactionManager();
    }

}
