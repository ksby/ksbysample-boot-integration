package ksbysample.eipapp.batchexecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;

import javax.sql.DataSource;

/**
 * Application 用メイン + JavaConfig 用クラス
 */
@SpringBootApplication(exclude = {JpaRepositoriesAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ImportResource("classpath:applicationContext.xml")
public class Application {

    /**
     * メインクラス
     * @param args アプリケーション起動時のオプション
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * @return Tomcat JDBC Connection Pool の DataSource オブジェクト
     */
    @Bean
    @ConfigurationProperties("spring.datasource.tomcat")
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .type(org.apache.tomcat.jdbc.pool.DataSource.class)
                .build();
    }

}
