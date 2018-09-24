package ksbysample.eipapp.dockerserver.flow;

import com.jcraft.jsch.ChannelSftp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.AcceptAllFileListFilter;
import org.springframework.integration.file.remote.handler.FileTransferringMessageHandler;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.messaging.support.GenericMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Configuration
public class SftpFlowConfig {

    private static final String SFTP_SERVER = "localhost";
    private static final int SFTP_PORT = 22;
    private static final String SFTP_USER = "user01";
    private static final String SFTP_PASSWORD = "pass01";

    private static final String SFTP_REMOTE_DIR = "/upload";
    private static final String SFTP_LOCAL_ROOT_DIR = "D:/eipapp/ksbysample-eipapp-dockerserver/sftp";
    private static final String SFTP_LOCAL_UPLOAD_DIR = SFTP_LOCAL_ROOT_DIR + "/upload";
    private static final String SFTP_LOCAL_UPLOADING_DIR = SFTP_LOCAL_ROOT_DIR + "/uploading";
    private static final String SFTP_LOCAL_DOWNLOAD_DIR = SFTP_LOCAL_ROOT_DIR + "/download";

    @Bean
    public SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
        factory.setHost(SFTP_SERVER);
        factory.setPort(SFTP_PORT);
        factory.setUser(SFTP_USER);
        factory.setPassword(SFTP_PASSWORD);
        factory.setAllowUnknownKeys(true);
        return new CachingSessionFactory<>(factory);
    }

    /****************************************
     * SFTPアップロード処理のサンプル            *
     ****************************************/

    @Bean
    public FileReadingMessageSource sftpUploadFileMessageSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File(SFTP_LOCAL_UPLOAD_DIR));
        source.setFilter(new AcceptAllFileListFilter<>());
        return source;
    }

    @Bean
    public FileTransferringMessageHandler<ChannelSftp.LsEntry> sftpFileTransferringMessageHandler() {
        FileTransferringMessageHandler<ChannelSftp.LsEntry> handler
                = new FileTransferringMessageHandler<>(sftpSessionFactory());
        handler.setRemoteDirectoryExpression(new LiteralExpression(SFTP_REMOTE_DIR));
        return handler;
    }

    @Bean
    public IntegrationFlow sftpUploadFlow() {
        return IntegrationFlows.from(
                // 200ミリ秒毎に upload ディレクトリを監視し、ファイルがあれば処理を進める
                sftpUploadFileMessageSource(), c -> c.poller(Pollers.fixedDelay(200)))
                // ファイルを uploading ディレクトリへ移動する
                .<File>handle((p, h) -> {
                    try {
                        Path movedFilePath = Files.move(p.toPath(), Paths.get(SFTP_LOCAL_UPLOADING_DIR, p.getName())
                                , StandardCopyOption.REPLACE_EXISTING);
                        return new GenericMessage<>(movedFilePath.toFile(), h);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                // SFTPサーバにファイルをアップロードする
                .wireTap(f -> f.handle(sftpFileTransferringMessageHandler()))
                .log(LoggingHandler.Level.WARN)
                // アップロードしたファイルを削除する
                .<File>handle((p, h) -> {
                    p.delete();
                    return null;
                })
                .get();
    }

    /****************************************
     * SFTPダウンロード処理のサンプル            *
     ****************************************/

    @Bean
    public SftpInboundFileSynchronizer sftpInboundFileSynchronizer() {
        SftpInboundFileSynchronizer synchronizer = new SftpInboundFileSynchronizer(sftpSessionFactory());
        synchronizer.setRemoteDirectory(SFTP_REMOTE_DIR);
        synchronizer.setFilter(new AcceptAllFileListFilter<>());
        synchronizer.setPreserveTimestamp(true);
        synchronizer.setDeleteRemoteFiles(true);
        return synchronizer;
    }

    @Bean
    public SftpInboundFileSynchronizingMessageSource sftpDownloadFileMessageSource() {
        SftpInboundFileSynchronizingMessageSource messageSource
                = new SftpInboundFileSynchronizingMessageSource(sftpInboundFileSynchronizer());
        messageSource.setLocalDirectory(new File(SFTP_LOCAL_DOWNLOAD_DIR));
        messageSource.setLocalFilter(new AcceptAllFileListFilter<>());
        messageSource.setMaxFetchSize(1);
        return messageSource;
    }

    @Bean
    public IntegrationFlow sftpDownloadFlow() {
        return IntegrationFlows.from(
                // 1秒毎に SFTPサーバを監視し、ファイルがあれば download ディレクトリにダウンロードする
                sftpDownloadFileMessageSource(), c -> c.poller(Pollers.fixedDelay(1000)))
                .log(LoggingHandler.Level.ERROR)
                // ファイルを upload ディレクトリへ移動する
                .<File>handle((p, h) -> {
                    try {
                        Files.move(p.toPath(), Paths.get(SFTP_LOCAL_UPLOAD_DIR, p.getName())
                                , StandardCopyOption.REPLACE_EXISTING);
                        return null;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .get();
    }

}
