package ksbysample.eipapp.channelcapacity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Slf4j
@Configuration
public class FlowConfig {

    private final DataSource dataSource;

    private final PlatformTransactionManager transactionManager;

    private final NullChannel nullChannel;

    public FlowConfig(DataSource dataSource
            , PlatformTransactionManager transactionManager
            , NullChannel nullChannel) {
        this.dataSource = dataSource;
        this.transactionManager = transactionManager;
        this.nullChannel = nullChannel;
    }

    @Bean
    public MessageSource<Object> jdbcMessageSource() {
        JdbcPollingChannelAdapter adapter
                = new JdbcPollingChannelAdapter(this.dataSource
                , "select * from QUEUE_SOURCE where status = 0");
        adapter.setRowMapper(new BeanPropertyRowMapper<>(QueueSourceDto.class));
        adapter.setUpdateSql("update QUEUE_SOURCE set status = 1 where seq in (:seq)");
        return adapter;
    }

    @Bean
    public IntegrationFlow selectDbFlow() {
        return IntegrationFlows.from(jdbcMessageSource()
                , e -> e.poller(Pollers
                        .fixedDelay(1000)
                        .maxMessagesPerPoll(5)
                        .transactional(this.transactionManager)))
                // 取得したデータは List 形式で全件 payload にセットされているので、split で１payload１データに分割する
                .split()
                .<QueueSourceDto>log(LoggingHandler.Level.WARN, m -> "☆☆☆ " + m.getPayload().getSeq())
                .channel(dstChannel())
                .get();
    }

    @Bean
    public QueueChannel dstChannel() {
        return MessageChannels.queue(3).get();
    }

    @Bean
    public IntegrationFlow getDstChannelFlow() {
        return IntegrationFlows.from(dstChannel())
                .bridge(e -> e.poller(Pollers
                        .fixedDelay(3000)
                        .maxMessagesPerPoll(1)))
                .<QueueSourceDto>log(LoggingHandler.Level.ERROR, m -> "★★★ " + m.getPayload().getSeq())
                .channel(nullChannel)
                .get();
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 1000)
    public void checkDstChannel() {
        log.info("dstChannel().getQueueSize() = " + dstChannel().getQueueSize());
    }

}
