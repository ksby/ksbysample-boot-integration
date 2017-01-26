package ksbysample.eipapp.ftp2sftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.file.filters.AcceptAllFileListFilter;
import org.springframework.integration.file.filters.IgnoreHiddenFileListFilter;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Configuration
public class FlowConfig {

    private final SessionFactory<FTPFile> ftpSessionFactory;

    private final SftpUploadMessageHandler sftpUploadMessageHandler;

    public FlowConfig(SessionFactory<FTPFile> ftpSessionFactory
            , SftpUploadMessageHandler sftpUploadMessageHandler) {
        this.ftpSessionFactory = ftpSessionFactory;
        this.sftpUploadMessageHandler = sftpUploadMessageHandler;
    }

    @Bean
    public IntegrationFlow ftp2SftpFlow() {
        return IntegrationFlows
                // FTP サーバを５秒間隔でチェックしてファイルがあればダウンロードする
                .from(s -> s.ftp(this.ftpSessionFactory)
                                // ファイルの更新日時を保持する(はず)。ただし Windows 上の FTPサーバ(Xlight ftp server)
                                // --> Windows のローカルディスクにダウンロードした時は保持されたのは更新日だけで
                                // 更新時刻は 0:00 だった。
                                .preserveTimestamp(true)
                                // ダウンロードしたら FTPサーバからファイルを削除する
                                .deleteRemoteFiles(true)
                                .remoteDirectory("/out")
                                .localDirectory(new File("C:/eipapp/ksbysample-eipapp-ftp2sftp/recv"))
                                // .localFilter(new AcceptAllFileListFilter<>()) を指定しないと１度ダウンロードされた
                                // ファイルは次の処理に渡されない
                                .localFilter(new AcceptAllFileListFilter<>())
                                .localFilter(new IgnoreHiddenFileListFilter())
                        , e -> e.poller(Pollers.fixedDelay(5000)
                                // FTP サーバに存在するファイルは１度で全てダウンロードされるが、次の処理に Message
                                // として渡すのは .maxMessagesPerPoll(100) で指定された数だけである
                                .maxMessagesPerPoll(100)))
                // SFTP アップロードのエラーチェック用 header を追加する
                .enrichHeaders(h -> h.header("sftpUploadError", false))
                // SFTPサーバにファイルをアップロードする
                .handle(this.sftpUploadMessageHandler)
                // SFTP アップロードのエラーチェック用 header ( sftpUploadError ) をチェックし、
                // false ならば send ディレクトリへ、true ならば error ディレクトリへファイルを移動する
                .routeToRecipients(r -> r
                        .recipientFlow("headers['sftpUploadError'] == false"
                                , f -> f.handle((p, h) -> {
                                    try {
                                        Path src = Paths.get(((File) p).getAbsolutePath());
                                        Files.move(src
                                                , Paths.get("C:/eipapp/ksbysample-eipapp-ftp2sftp/send/" + src.getFileName())
                                                , StandardCopyOption.REPLACE_EXISTING);
                                        log.info("FTP --> SFTP 正常終了 ( {} )", src.toAbsolutePath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    return null;
                                }))
                        .recipientFlow("headers['sftpUploadError'] == true"
                                , f -> f.handle((p, h) -> {
                                    try {
                                        Path src = Paths.get(((File) p).getAbsolutePath());
                                        Files.move(src
                                                , Paths.get("C:/eipapp/ksbysample-eipapp-ftp2sftp/error/" + src.getFileName())
                                                , StandardCopyOption.REPLACE_EXISTING);
                                        log.info("FTP --> SFTP エラー ( {} )", src.toAbsolutePath());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    return null;
                                })))
                .get();
    }

}
