package ksbysample.eipapp.datacopy.integration.channel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.MessageChannel;

@Configuration
public class ChannelConfig {

    @Bean
    public MessageChannel copyChannel() {
        return new QueueChannel(100);
    }

    @Bean
    public MessageChannel delChannel() {
        return new QueueChannel(100);
    }

}
