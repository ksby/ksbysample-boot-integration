package ksbysample.batch.integration.recvmailusingpop3batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.integration.mail.MailReceiver;
import org.springframework.integration.mail.Pop3MailReceiver;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;

public class RecvMailUsingPOP3BatchRunner implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String BATCH_NAME = "RecvMailUsingPop3Batch";

    @Autowired
    private MailReceiver receiver;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Message[] recvMessages = receiver.receive();
        for (Message message : recvMessages) {
            String body = null;
            // 受信しているメールの内容は org.apache.commons.io.IOUtils の toString メソッドを使用して確認できる
            // System.out.println("★" + IOUtils.toString(message.getInputStream(), "UTF-8"));
            if (message.getContent() instanceof MimeMultipart) {
                body = processMimeMultipart((MimeMultipart) message.getContent());
            } else {
                body = (String) message.getContent();
            }
            System.out.println(message.getSubject() + ", " + body);
        }
    }

    private String processMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        StringBuilder sb = new StringBuilder();
        String body = null;
        for (int i = 0; i < mimeMultipart.getCount(); i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.getContent() instanceof MimeMultipart) {
                body = processMimeMultipart((MimeMultipart) bodyPart.getContent());
                sb.append(body);
            } else if (bodyPart.isMimeType("text/plain")) {
                body = (String) bodyPart.getContent();
                sb.append(body);
            }
        }

        return sb.toString();
    }

    @Configuration
    @PropertySource("classpath:ksbysample/batch/integration/recvmailusingpop3batch/recvmailusingpop3batch.properties")
    public static class RecvMailUsingPOP3BatchConfig {

        @Value("${pop3.url}")
        private String POP3_URL;

        @Bean
        @ConditionalOnProperty(value = {"batch.execute"}, havingValue = RecvMailUsingPOP3BatchRunner.BATCH_NAME)
        public MailReceiver pop3MailReceiver() {
            return createPop3MailReceiverInstance();
        }

        @Bean
        @ConditionalOnProperty(value = {"batch.execute"}, havingValue = RecvMailUsingPOP3BatchRunner.BATCH_NAME)
        public ApplicationRunner recvMailUsingPOP3BatchRunner() {
            return new RecvMailUsingPOP3BatchRunner();
        }

        public Pop3MailReceiver createPop3MailReceiverInstance() {
            Pop3MailReceiver pop3MailReceiver = new Pop3MailReceiver(POP3_URL);
            // 受信したメールは削除する
            pop3MailReceiver.setShouldDeleteMessages(true);
            return pop3MailReceiver;
        }

    }

}
