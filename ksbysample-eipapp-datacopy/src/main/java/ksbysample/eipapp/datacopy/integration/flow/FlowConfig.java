package ksbysample.eipapp.datacopy.integration.flow;

import ksbysample.eipapp.datacopy.dto.OrdersDtoRowMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class FlowConfig {

    private final DataSource dataSourceWorld;

    private final PlatformTransactionManager transactionManagerWorld;

    private final MessageChannel copyChannel;

    private final MessageChannel delChannel;

    private final CopyOrdersMessageHandler copyOrdersMessageHandler;

    private final DelOrdersMessageHandler delOrdersMessageHandler;

    public FlowConfig(@Qualifier("dataSourceWorld") DataSource dataSourceWorld
            , @Qualifier("transactionManagerWorld") PlatformTransactionManager transactionManagerWorld
            , MessageChannel copyChannel
            , MessageChannel delChannel
            , CopyOrdersMessageHandler copyOrdersMessageHandler
            , DelOrdersMessageHandler delOrdersMessageHandler) {
        this.dataSourceWorld = dataSourceWorld;
        this.transactionManagerWorld = transactionManagerWorld;
        this.copyChannel = copyChannel;
        this.delChannel = delChannel;
        this.copyOrdersMessageHandler = copyOrdersMessageHandler;
        this.delOrdersMessageHandler = delOrdersMessageHandler;
    }

    @Bean
    public MessageSource<Object> ordersJdbcMessageSource() {
        JdbcPollingChannelAdapter adapter
                = new JdbcPollingChannelAdapter(this.dataSourceWorld, "select * from orders where status = '00'");
        adapter.setRowMapper(new OrdersDtoRowMapper());
        adapter.setUpdateSql("update orders set status = '01' where order_id in (:orderId)");
        return adapter;
    }

    @Bean
    public IntegrationFlow getFlow() {
        // MySQL の world データベースの orders テーブルから status = '00' のデータを
        // 1秒間隔で取得して copyChannel にデータを渡す。取得したデータの status カラム
        // は '00'→'01' に更新する。
        return IntegrationFlows.from(ordersJdbcMessageSource(),
                c -> c.poller(Pollers.fixedRate(1000)
                        .transactional(this.transactionManagerWorld)))
                .channel(this.copyChannel)
                .get();
    }

    @Bean
    public IntegrationFlow copyFlow() {
        // copyChannel を 1秒間隔でチェックして、データがある場合には PostgreSQL
        // の ksbylending データベースの orders テーブルに insert する。insert に成功した
        // 場合にはデータをそのまま delChannel に渡す。
        return IntegrationFlows.from(this.copyChannel)
                .handle(this.copyOrdersMessageHandler
                        , c -> c.poller(Pollers.fixedRate(1000)))
                .channel(this.delChannel)
                .get();
    }

    @Bean
    public IntegrationFlow delFlow() {
        // delChannel を 1秒間隔でチェックして、データがある場合には MySQL の
        // world データベースの orders テーブルのデータを delete する。
        return IntegrationFlows.from(this.delChannel)
                .handle(this.delOrdersMessageHandler
                        , c -> c.poller(Pollers.fixedRate(1000)))
                .get();
    }

}
