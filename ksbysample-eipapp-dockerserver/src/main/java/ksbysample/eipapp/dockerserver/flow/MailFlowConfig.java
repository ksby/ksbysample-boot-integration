package ksbysample.eipapp.dockerserver.flow;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.mail.MailHeaders;
import org.springframework.integration.mail.MailReceivingMessageSource;
import org.springframework.integration.mail.MailSendingMessageHandler;
import org.springframework.integration.mail.Pop3MailReceiver;
import org.springframework.integration.support.StringObjectMapBuilder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// このサンプルを実行したい場合には、@Configuration のコメントアウトを外すこと
//@Configuration
public class MailFlowConfig {

    /****************************************
     * メール送信処理のサンプル                 *
     ****************************************/

    private final JavaMailSender mailSender;

    private final AtomicInteger count;

    public MailFlowConfig(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.count = new AtomicInteger(0);
    }

    @Bean
    public MailSendingMessageHandler mailSendingMessageHandler() {
        return new MailSendingMessageHandler(this.mailSender);
    }

    @Autowired
    private MailSendingMessageHandler mailSendingMessageHandler;

    /**
     * 受信したメッセージを元にメールを送信する
     *
     * @return {@IntegrationFlow} オブジェクト
     */
    @Bean
    public IntegrationFlow sendMailFlow() {
        return f -> f
                // メッセージ送信に時間がかかるので（1件あたり約10秒）、スレッドを生成してメール送信は別スレッドに処理させる
                .channel(c -> c.executor(Executors.newCachedThreadPool()))
                .filter(Message.class, m ->
                        m.getHeaders().containsKey(MailHeaders.FROM)
                                && m.getHeaders().containsKey(MailHeaders.TO)
                                && m.getHeaders().containsKey(MailHeaders.SUBJECT))
                .log(LoggingHandler.Level.WARN, m -> String.format("★★★ メール送信: %s", m.getPayload()))
                .wireTap(sf -> sf.handle(mailSendingMessageHandler))
                .log(LoggingHandler.Level.WARN, m -> String.format("◇◇◇ メール送信: %s", m.getPayload()));
    }

    /**
     * sendMailFlow へ５秒おきに MailHeaders.* のヘッダーをセットした Message を送信する
     */
    @Scheduled(initialDelay = 5000, fixedDelay = 5000)
    public void sendMessageTask() {
        Map<String, Object> headers = new StringObjectMapBuilder()
                .put(MailHeaders.FROM, "sample@test.co.jp")
                .put(MailHeaders.TO, "tanaka@mail.example.com,suzuki@mail.example.com")
                .put(MailHeaders.SUBJECT, "これはテストです")
                .get();
        MessageHeaders messageHeaders = new MessageHeaders(headers);
        Message<String> message
                = MessageBuilder.createMessage(String.format("count = %d", this.count.incrementAndGet())
                , messageHeaders);
        sendMailFlow().getInputChannel().send(message);
    }


    /****************************************
     * メール受信処理のサンプル                 *
     ****************************************/

    /**
     * Pop3MailReceiver {@link MailFlowConfig#pop3MessageSource()} に記述せず Bean で定義する必要がある
     * Bean にしないと受信したメッセージをサーバから削除してくれない
     *
     * @return {@Pop3MailReceiver} オブジェクト
     */
    @Bean
    public Pop3MailReceiver pop3MailReceiver() {
        Pop3MailReceiver pop3MailReceiver
                = new Pop3MailReceiver("localhost", "tanaka@mail.example.com", "xxxxxxxx");
        pop3MailReceiver.setShouldDeleteMessages(true);
        Properties javaMailProperties = new Properties();
        // debug したい場合には以下のコメントアウトを解除する
        // javaMailProperties.put("mail.debug", "true");
        pop3MailReceiver.setJavaMailProperties(javaMailProperties);
        return pop3MailReceiver;
    }

    @Bean
    public MailReceivingMessageSource pop3MessageSource() {
        return new MailReceivingMessageSource(pop3MailReceiver());
    }

    /**
     * ５秒おきにメールを受信してみる
     *
     * @return
     */
    @Bean
    public IntegrationFlow mailRecvByPop3Flow() {
        return IntegrationFlows.from(pop3MessageSource()
                // ５秒おきに最大100件受信する
                , c -> c.poller(Pollers.fixedDelay(5000).maxMessagesPerPoll(100)))
                .<MimeMessage>log(LoggingHandler.Level.ERROR, m -> {
                    try {
                        return String.format("◎◎◎ メール受信: %s"
                                , StringUtils.chomp((String) m.getPayload().getContent()));
                    } catch (IOException | MessagingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .get();
    }

}
