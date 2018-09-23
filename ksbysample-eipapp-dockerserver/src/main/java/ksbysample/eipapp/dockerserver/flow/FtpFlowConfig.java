package ksbysample.eipapp.dockerserver.flow;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
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
import org.springframework.integration.ftp.inbound.FtpInboundFileSynchronizer;
import org.springframework.integration.ftp.inbound.FtpInboundFileSynchronizingMessageSource;
import org.springframework.integration.ftp.session.DefaultFtpSessionFactory;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.messaging.support.GenericMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Configuration
public class FtpFlowConfig {

    private static final String FTP_SERVER = "localhost";
    private static final int FTP_PORT = 21;
    private static final String FTP_USER = "test";
    private static final String FTP_PASSWORD = "12345678";

    private static final String FTP_REMOTE_DIR = "/";
    private static final String FTP_LOCAL_ROOT_DIR = "D:/eipapp/ksbysample-eipapp-dockerserver/ftp";
    private static final String FTP_LOCAL_UPLOAD_DIR = FTP_LOCAL_ROOT_DIR + "/upload";
    private static final String FTP_LOCAL_UPLOADING_DIR = FTP_LOCAL_ROOT_DIR + "/uploading";
    private static final String FTP_LOCAL_DOWNLOAD_DIR = FTP_LOCAL_ROOT_DIR + "/download";

    @Bean
    public SessionFactory<FTPFile> ftpSessionFactory() {
        DefaultFtpSessionFactory factory = new DefaultFtpSessionFactory();
        factory.setHost(FTP_SERVER);
        factory.setPort(FTP_PORT);
        factory.setUsername(FTP_USER);
        factory.setPassword(FTP_PASSWORD);
        factory.setClientMode(FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE);
        return new CachingSessionFactory<>(factory);
    }

    /****************************************
     * FTPアップロード処理のサンプル             *
     ****************************************/

    @Bean
    public FileReadingMessageSource ftpUploadFileMessageSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File(FTP_LOCAL_UPLOAD_DIR));
        source.setFilter(new AcceptAllFileListFilter<>());
        return source;
    }

    @Bean
    public FileTransferringMessageHandler<FTPFile> ftpFileTransferringMessageHandler() {
        FileTransferringMessageHandler<FTPFile> handler
                = new FileTransferringMessageHandler<>(ftpSessionFactory());
        handler.setRemoteDirectoryExpression(new LiteralExpression(FTP_REMOTE_DIR));
        return handler;
    }

    @Bean
    public IntegrationFlow ftpUploadFlow() {
        return IntegrationFlows.from(
                // 200ミリ秒毎に ftp ディレクトリを監視し、ファイルがあれば処理を進める
                ftpUploadFileMessageSource(), c -> c.poller(Pollers.fixedDelay(200)))
                // ファイルを uploading ディレクトリへ移動する
                .<File>handle((p, h) -> {
                    try {
                        Path movedFilePath = Files.move(p.toPath(), Paths.get(FTP_LOCAL_UPLOADING_DIR, p.getName())
                                , StandardCopyOption.REPLACE_EXISTING);
                        return new GenericMessage<>(movedFilePath.toFile(), h);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                // FTPサーバにファイルをアップロードする
                .wireTap(f -> f.handle(ftpFileTransferringMessageHandler()))
                .log(LoggingHandler.Level.WARN)
                // アップロードしたファイルを削除する
                .<File>handle((p, h) -> {
                    p.delete();
                    return null;
                })
                .get();
    }

    /****************************************
     * FTPダウンロード処理のサンプル             *
     ****************************************/

    @Bean
    public FtpInboundFileSynchronizer ftpInboundFileSynchronizer() {
        FtpInboundFileSynchronizer synchronizer = new FtpInboundFileSynchronizer(ftpSessionFactory());
        synchronizer.setRemoteDirectory(FTP_REMOTE_DIR);
        synchronizer.setFilter(new AcceptAllFileListFilter<>());
        synchronizer.setPreserveTimestamp(true);
        synchronizer.setDeleteRemoteFiles(true);
        return synchronizer;
    }

    @Bean
    public FtpInboundFileSynchronizingMessageSource ftpDownloadFileMessageSource() {
        FtpInboundFileSynchronizingMessageSource messageSource
                = new FtpInboundFileSynchronizingMessageSource(ftpInboundFileSynchronizer());
        messageSource.setLocalDirectory(new File(FTP_LOCAL_DOWNLOAD_DIR));
        messageSource.setLocalFilter(new AcceptAllFileListFilter<>());
        messageSource.setMaxFetchSize(1);
        return messageSource;
    }

    @Bean
    public IntegrationFlow ftpDownloadFlow() {
        return IntegrationFlows.from(
                // 1秒毎に FTPサーバを監視し、ファイルがあれば download ディレクトリにダウンロードする
                ftpDownloadFileMessageSource(), c -> c.poller(Pollers.fixedDelay(1000)))
                .log(LoggingHandler.Level.ERROR)
                // ファイルを upload ディレクトリへ移動する
                .<File>handle((p, h) -> {
                    try {
                        Files.move(p.toPath(), Paths.get(FTP_LOCAL_UPLOAD_DIR, p.getName())
                                , StandardCopyOption.REPLACE_EXISTING);
                        return null;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .get();
    }

}
