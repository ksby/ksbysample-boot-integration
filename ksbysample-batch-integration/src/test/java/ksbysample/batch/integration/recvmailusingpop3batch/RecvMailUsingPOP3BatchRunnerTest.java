package ksbysample.batch.integration.recvmailusingpop3batch;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import ksbysample.batch.integration.Application;
import ksbysample.common.test.rule.mail.MailServerResource;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.integration.mail.Pop3MailReceiver;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.mail.internet.MimeMessage;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class RecvMailUsingPOP3BatchRunnerTest {

    private final String POP3_USER_MAILADDR = "test@sample.co.jp";

    @Rule
    @Autowired
    public MailServerResource smtpPop3Server;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private RecvMailUsingPOP3BatchRunner.RecvMailUsingPOP3BatchConfig recvMailUsingPOP3BatchConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void run() throws Exception {
        GreenMail greenMail = smtpPop3Server.getGreenMail();
        GreenMailUser user = greenMail.setUser(POP3_USER_MAILADDR, "tanaka", "12345678");

        // メールを送信する ( １通目 )( 非マルチパート )
        MimeMessage mimeMessage = this.mailSender.createMimeMessage();
        MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, "UTF-8");
        message.setFrom("from@test.com");
        message.setTo(POP3_USER_MAILADDR);
        message.setSubject("件名");
        message.setText("本文", false);
        user.deliver(message.getMimeMessage());

        // メールを送信する ( ２通目 )( マルチパート、本文のみ )
        mimeMessage = this.mailSender.createMimeMessage();
        message = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        message.setFrom("from2@multipart.co.jp");
        message.setTo(POP3_USER_MAILADDR);
        message.setSubject("件名(Multipart、添付ファイルなし)");
        message.setText("本文(Multipart、添付ファイルなし)", false);
        user.deliver(message.getMimeMessage());

        // メールを送信する ( ３通目 )( マルチパート、本文＋添付ファイル )
        mimeMessage = this.mailSender.createMimeMessage();
        message = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        message.setFrom("from3@multipart.co.jp");
        message.setTo(POP3_USER_MAILADDR);
        message.setSubject("件名(Multipart、本文＋添付ファイル)");
        message.setText("本文(Multipart、本文＋添付ファイル)", false);
        Resource resource = new ClassPathResource("logback-spring.xml");
        message.addAttachment("設定ファイル", resource.getFile());
        user.deliver(message.getMimeMessage());

        // メールを送信する ( ４通目 )( マルチパート、本文＋本文INLINE )
        mimeMessage = this.mailSender.createMimeMessage();
        message = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        message.setFrom("from4@multipart.co.jp");
        message.setTo(POP3_USER_MAILADDR);
        message.setSubject("件名(Multipart、本文＋本文INLINE)");
        message.setText("本文(Multipart、本文＋本文INLINE)", false);
        resource = new ClassPathResource("application.properties");
        message.addInline("設定ファイル", new ByteArrayResource(IOUtils.toByteArray(resource.getInputStream())), "text/plain");
        user.deliver(message.getMimeMessage());

        // Pop3MailReceiver クラスの Bean を作成して context に登録する
        // Bean を生成する
        Pop3MailReceiver pop3MailReceiverBean = recvMailUsingPOP3BatchConfig.createPop3MailReceiverInstance();
        AutowireCapableBeanFactory factory = applicationContext.getAutowireCapableBeanFactory();
        factory.autowireBean(pop3MailReceiverBean);
        factory.initializeBean(pop3MailReceiverBean, pop3MailReceiverBean.getClass().getSimpleName());
        // context に登録する
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        beanFactory.registerSingleton(pop3MailReceiverBean.getClass().getCanonicalName(), pop3MailReceiverBean);

        // RecvMailUsingPOP3BatchRunner クラスの Bean を作成して run メソッドを実行する
        RecvMailUsingPOP3BatchRunner recvMailUsingPOP3BatchRunnerBean = new RecvMailUsingPOP3BatchRunner();
        factory.autowireBean(recvMailUsingPOP3BatchRunnerBean);
        factory.initializeBean(recvMailUsingPOP3BatchRunnerBean, recvMailUsingPOP3BatchRunnerBean.getClass().getSimpleName());
        recvMailUsingPOP3BatchRunnerBean.run(null);

        // 再度 run メソッドを実行すると、メールが POP3 サーバに残っていないことを確認する
        recvMailUsingPOP3BatchRunnerBean.run(null);

        // 今回はバッチを実行しているだけで、assert 入れてテストしている訳ではありません
    }

}
