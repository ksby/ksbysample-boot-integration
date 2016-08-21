package ksbysample.eipapp.dirchecker.eip.channel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

@Configuration
public class ChannelConfig {

    @Bean
    public MessageChannel inChannel() {
        return new DirectChannel();
    }

}
