package ksbysample.eipapp.wiretap;

import com.jcraft.jsch.ChannelSftp;
import org.aopalliance.aop.Advice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.support.Transformers;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.AcceptAllFileListFilter;
import org.springframework.integration.file.filters.IgnoreHiddenFileListFilter;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.mail.MailHeaders;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static java.util.Collections.singletonMap;

@Configuration
public class FlowConfig {

    private static final String ROOT_DIR = "C:/eipapp/ksbysample-eipapp-wiretap";
    private static final String IN_DIR = ROOT_DIR + "/in";
    private static final String OUT_DIR = ROOT_DIR + "/out";

    private static final String MAIL_FROM = "system@sample.com";
    private static final String MAIL_TO = "download@test.co.jp";

    private static final String SFTP_UPLOAD_DIR = "/in";

    private static final String CRLF = "\r\n";

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${spring.mail.port:25}")
    private int mailPort;

    @Value("${spring.mail.protocol:smtp}")
    private String mailProtocol;

    @Value("${spring.mail.default-encoding:UTF-8}")
    private String mailDefaultEncoding;

    /**
     * SFTP サーバに接続するための SessionFactory オブジェクトを生成する
     * 今回は CachingSessionFactory を使用せず処理毎に接続・切断されるようにする
     *
     * @return SFTP サーバ接続用の SessionFactory オブジェクト
     */
    @Bean
    public SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost("localhost");
        factory.setPort(22);
        factory.setUser("send01");
        factory.setPassword("send01");
        factory.setAllowUnknownKeys(true);
        return factory;
    }

    /**
     * EIP の１つ wireTap のサンプル Flow
     *
     * @return IntegrationFlow オブジェクト
     */
    @Bean
    public IntegrationFlow wiretapSampleFlow() {
        return IntegrationFlows
                // C:/eipapp/ksbysample-eipapp-wiretap/in にファイルが作成されたか 1秒間隔でチェックする
                .from(s -> s.file(new File(IN_DIR))
                                // 同じファイルが置かれても処理する
                                .filter(new AcceptAllFileListFilter<>())
                                .filter(new IgnoreHiddenFileListFilter())
                                // ファイルが新規作成された時だけ Message を送信する
                                // これを入れないとファイルが存在する限り何度も Message が送信され続ける
                                .useWatchService(true)
                                .watchEvents(FileReadingMessageSource.WatchEventType.CREATE)
                        , e -> e.poller(Pollers.fixedDelay(1000)))
                // ファイル名とファイルの絶対パス、メール送信用の From, To, Subject を Message の header にセットする
                .enrichHeaders(h -> h
                        .headerExpression(FileHeaders.FILENAME, "payload.name")
                        .headerExpression(FileHeaders.ORIGINAL_FILE, "payload.absolutePath")
                        .header(MailHeaders.FROM, MAIL_FROM)
                        .header(MailHeaders.TO, MAIL_TO)
                        .headerExpression(MailHeaders.SUBJECT, "payload.name"))
                .wireTap(f -> f
                        // File の内容を読み込んで payload へセットする
                        .transform(Transformers.fileToString())
                        .wireTap(sf -> sf
                                // メールを送信する
                                .handleWithAdapter(a -> a.mail(this.mailHost)
                                        .port(this.mailPort)
                                        .protocol(this.mailProtocol)
                                        .defaultEncoding(this.mailDefaultEncoding)))
                        .wireTap(sf -> sf
                                // payload の内容をファイルに出力する内容に変更する
                                .handle((p, h) -> {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("To: " + h.get(MailHeaders.TO) + CRLF);
                                    sb.append("Subject: " + h.get(MailHeaders.SUBJECT) + CRLF);
                                    sb.append(CRLF);
                                    sb.append(p);

                                    return MessageBuilder.withPayload(sb.toString())
                                            .build();
                                })
                                // /out ディレクトリにファイルを生成する
                                // ファイル名は header 内の FileHeaders.FILENAME のキー名の文字列が使用される
                                .handleWithAdapter(a -> a.file(new File(OUT_DIR))))
                        .channel("nullChannel"))
                .wireTap(f -> f
                        // payload の File クラスを /out ディレクトリのファイルに変更する
                        .handle((p, h) -> Paths.get(OUT_DIR, (String) h.get(FileHeaders.FILENAME)).toFile())
                        // SFTP サーバにファイルをアップロードする
                        .handleWithAdapter(a -> a.sftp(sftpSessionFactory())
                                        .remoteDirectory(SFTP_UPLOAD_DIR)
                                , e -> e.advice(sftpUploadRetryAdvice())))
                // /in, /out ディレクトリのファイルを削除する
                .<File>handle((p, h) -> {
                    try {
                        Files.delete(Paths.get(p.getAbsolutePath()));
                        Files.delete(Paths.get(OUT_DIR, p.getName()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                })
                .get();
    }

    /**
     * リトライは最大５回 ( SimpleRetryPolicy で指定 )、
     * リトライ間隔は初期値２秒、最大１０秒、倍数2.0 ( ExponentialBackOffPolicy で指定 )
     * の RequestHandlerRetryAdvice オブジェクトを生成する
     *
     * @return RequestHandlerRetryAdvice オブジェクト
     */
    @Bean
    public Advice sftpUploadRetryAdvice() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(
                new SimpleRetryPolicy(5, singletonMap(Exception.class, true)));
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000);
        backOffPolicy.setMaxInterval(10000);
        backOffPolicy.setMultiplier(2.0);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        RequestHandlerRetryAdvice advice = new RequestHandlerRetryAdvice();
        advice.setRetryTemplate(retryTemplate);

        return advice;
    }

}
