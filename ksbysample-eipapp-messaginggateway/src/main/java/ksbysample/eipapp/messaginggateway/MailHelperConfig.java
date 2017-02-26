package ksbysample.eipapp.messaginggateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.mail.Mail;

import java.util.Map;

@Configuration
public class MailHelperConfig {

    @Value("${spring.mail.default-encoding:UTF-8}")
    private String mailDefaultEncoding;

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${spring.mail.port:25}")
    private int mailPort;

    @Value("${spring.mail.protocol:smtp}")
    private String mailProtocol;

    @MessagingGateway
    public interface MailHelper {

        @Gateway(requestChannel = "sendMailFlow.input")
        void send(String payload, Map<Object, Object> headers);

    }

    /**
     * メールを送信する
     * メールの From, To, Subject 等は Message の header に、メール本文は payload にセットする
     * ヘッダーにセットする時の key 文字列は org.springframework.integration.mail.MailHeaders クラス参照
     *
     * @return IntegrationFlow オブジェクト
     */
    @Bean
    public IntegrationFlow sendMailFlow() {
        return f -> f
                .handle(Mail.outboundAdapter(this.mailHost)
                        .port(this.mailPort)
                        .protocol(this.mailProtocol)
                        .defaultEncoding(this.mailDefaultEncoding)
                        .javaMailProperties(p -> p.put("mail.debug", "false")));
    }

}
