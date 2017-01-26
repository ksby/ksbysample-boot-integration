package ksbysample.eipapp.ftp2sftp;

import com.jcraft.jsch.ChannelSftp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;

import java.io.File;
import java.util.Map;

@Slf4j
@MessageEndpoint
public class SftpUploadMessageHandler implements GenericHandler<File> {

    private final SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory;

    public SftpUploadMessageHandler(SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory) {
        this.sftpSessionFactory = sftpSessionFactory;
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 5, backoff = @Backoff(delay = 10000))
    public Object handle(File payload, Map<String, Object> headers) {
        // リトライした場合にはリトライ回数をログに出力する
        RetryContext retryContext = RetrySynchronizationManager.getContext();
        if (retryContext.getRetryCount() > 0) {
            log.info("リトライ回数 = {}", retryContext.getRetryCount());
        }

        SftpRemoteFileTemplate sftpClient = new SftpRemoteFileTemplate(sftpSessionFactory);
        sftpClient.setRemoteDirectoryExpression(new LiteralExpression("/in"));
        sftpClient.send(MessageBuilder.withPayload(payload).build(), FileExistsMode.REPLACE);
        return payload;
    }

    @Recover
    public Object recoverException(Exception e, File payload, Map<String, Object> headers) {
        log.error("SFTPサーバにアップロードできませんでした ( {} )", payload.getAbsolutePath());
        return MessageBuilder.withPayload(payload)
                .setHeader("sftpUploadError", true)
                .build();
    }

}
