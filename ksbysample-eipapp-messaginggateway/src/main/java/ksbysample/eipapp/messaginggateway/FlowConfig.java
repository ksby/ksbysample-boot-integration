package ksbysample.eipapp.messaginggateway;

import com.google.common.base.Throwables;
import com.jcraft.jsch.ChannelSftp;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.support.MapBuilder;
import org.springframework.integration.dsl.support.Transformers;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.filters.AcceptAllFileListFilter;
import org.springframework.integration.file.filters.IgnoreHiddenFileListFilter;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.ftp.session.DefaultFtpSessionFactory;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
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

@Slf4j
@Configuration
public class FlowConfig {

    private static final String PATH_SFTP_DOWNLOAD_DIR = "/in";
    private static final String PATH_LOCAL_DOWNLOAD_DIR = "C:/eipapp/ksbysample-eipapp-messaginggateway/recv";
    private static final String PATH_LOCAL_UPLOAD_DIR = "C:/eipapp/ksbysample-eipapp-messaginggateway/send";
    private static final String PATH_FTP_UPLOAD_DIR = "/out";

    private static final String DOWNLOADFILEMAIL_FROM = "system@sample.com";
    private static final String DOWNLOADFILEMAIL_TO = "download@test.co.jp";

    private static final String ERRORMAIL_FROM = "system@sample.com";
    private static final String ERRORMAIL_TO = "alert@test.co.jp";
    private static final String ERRORMAIL_SUBJECT = "エラーが発生しました";

    private static final String CRLF = "\r\n";

    private final MailHelperConfig.MailHelper mailHelper;

    public FlowConfig(MailHelperConfig.MailHelper mailHelper) {
        this.mailHelper = mailHelper;
    }

    /**
     * SFTP サーバに接続するための SessionFactory オブジェクトを生成する
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
        return new CachingSessionFactory<>(factory);
    }

    /**
     * FTP サーバに接続するための SessionFactory オブジェクトを生成する
     *
     * @return FTP サーバ接続用の SessionFactory オブジェクト
     */
    @Bean
    public SessionFactory<FTPFile> ftpSessionFactory() {
        DefaultFtpSessionFactory factory = new DefaultFtpSessionFactory();
        factory.setHost("localhost");
        factory.setPort(21);
        factory.setUsername("recv01");
        factory.setPassword("recv01");
        return new CachingSessionFactory<>(factory);
    }

    /**
     * SFTP サーバにあるファイルをダウンロードした後、ファイルの内容をメールで送信して、
     * 送信したメールの内容をファイルに出力して FTP サーバにアップロードする
     *
     * @return IntegrationFlow オブジェクト
     */
    @Bean
    public IntegrationFlow sftpToMailToFtpFlow() {
        return IntegrationFlows
                // SFTP サーバの /in ディレクトリにファイルがあるか 5秒間隔でチェックする
                .from(s -> s.sftp(sftpSessionFactory())
                                .preserveTimestamp(true)
                                .deleteRemoteFiles(true)
                                .remoteDirectory(PATH_SFTP_DOWNLOAD_DIR)
                                .localDirectory(new File(PATH_LOCAL_DOWNLOAD_DIR))
                                .localFilter(new AcceptAllFileListFilter<>())
                                .localFilter(new IgnoreHiddenFileListFilter())
                        , e -> e.poller(Pollers.fixedDelay(5000)
                                .maxMessagesPerPoll(100)))
                .log()
                // ファイル名とファイルの絶対パスを Message の header にセットする
                .enrichHeaders(h -> h
                        .headerExpression(FileHeaders.FILENAME, "payload.name")
                        .headerExpression(FileHeaders.ORIGINAL_FILE, "payload.absolutePath"))
                // File の内容を読み込んで payload へセットする
                .transform(Transformers.fileToString())
                // ファイルの内容をメール本文とするメールを送信する
                .<String>handle((p, h) -> {
                    this.mailHelper.send(p
                            , new MapBuilder<>()
                                    .put(MailHeaders.FROM, DOWNLOADFILEMAIL_FROM)
                                    .put(MailHeaders.TO, DOWNLOADFILEMAIL_TO)
                                    .put(MailHeaders.SUBJECT, h.get(FileHeaders.FILENAME))
                                    .get());

                    return MessageBuilder.withPayload(p)
                            .setHeader(MailHeaders.TO, DOWNLOADFILEMAIL_TO)
                            .setHeader(MailHeaders.SUBJECT, h.get(FileHeaders.FILENAME))
                            .build();
                })
                .log()
                // メール送信した内容を payload にセットする
                .<String>handle((p, h) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("To: " + h.get(MailHeaders.TO) + CRLF);
                    sb.append("Subject: " + h.get(MailHeaders.SUBJECT) + CRLF);
                    sb.append(CRLF);
                    sb.append(p);

                    return MessageBuilder.withPayload(sb.toString())
                            .build();
                })
                // /send ディレクトリの下に payload の内容を出力したファイルを生成する
                .handleWithAdapter(a -> a.fileGateway(new File(PATH_LOCAL_UPLOAD_DIR)))
                .log()
                // /recv ディレクトリの下のファイルを削除し、payload には /send ディレクトリの下の
                // ファイルを指す File オブジェクトをセットする
                .handle((p, h) -> {
                    try {
                        Files.delete(Paths.get((String) h.get(FileHeaders.ORIGINAL_FILE)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    return MessageBuilder
                            .withPayload(Paths.get(PATH_LOCAL_UPLOAD_DIR
                                    , (String) h.get(FileHeaders.FILENAME)).toFile())
                            .build();
                })
                // /send ディレクトリの下に作成したファイルを FTP サーバにアップロードする
                .bridge(e -> e.advice(ftpUploadAdvice()))
                .log()
                // /send ディレクトリの下に作成したファイルを削除する
                .<File>handle((p, h) -> {
                    try {
                        Files.delete(Paths.get(p.getAbsolutePath()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    return null;
                })
                .log()
                .get();
    }

    /**
     * Message の payload にセットされた File オブジェクトが指し示すファイルを FTP サーバにアップロードする
     *
     * @return IntegrationFlow オブジェクト
     */
    @Bean
    public IntegrationFlow ftpUploadFlow() {
        return f -> f
                .handleWithAdapter(a -> a.ftp(ftpSessionFactory()).remoteDirectory(PATH_FTP_UPLOAD_DIR)
                        , e -> e.advice(ftpUploadRetryAdvice()));
    }

    /**
     * ftpUploadFlow へ Message を送信する ExpressionEvaluatingRequestHandlerAdvice Bean
     *
     * @return ExpressionEvaluatingRequestHandlerAdvice オブジェクト
     */
    @Bean
    public Advice ftpUploadAdvice() {
        ExpressionEvaluatingRequestHandlerAdvice advice
                = new ExpressionEvaluatingRequestHandlerAdvice();
        advice.setOnSuccessExpressionString("payload");
        advice.setSuccessChannelName("ftpUploadFlow.input");
        return advice;
    }

    /**
     * リトライは最大５回 ( SimpleRetryPolicy で指定 )、
     * リトライ間隔は初期値２秒、最大１０秒、倍数2.0 ( ExponentialBackOffPolicy で指定 )
     * の RequestHandlerRetryAdvice オブジェクトを生成する
     *
     * @return RequestHandlerRetryAdvice オブジェクト
     */
    @Bean
    public Advice ftpUploadRetryAdvice() {
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

    /**
     * errorChannel に送信された Message からエラーメッセージを取得してメールする
     *
     * @return IntegrationFlow オブジェクト
     */
    @Bean
    public IntegrationFlow errorChannelFlow() {
        return IntegrationFlows.from("errorChannel")
                .<Exception>handle((p, h) -> {
                    String stacktrace = Throwables.getStackTraceAsString(p);
                    this.mailHelper.send(stacktrace
                            , new MapBuilder<>()
                                    .put(MailHeaders.FROM, ERRORMAIL_FROM)
                                    .put(MailHeaders.TO, ERRORMAIL_TO)
                                    .put(MailHeaders.SUBJECT, ERRORMAIL_SUBJECT)
                                    .get());

                    return null;
                })
                .get();
    }

}
