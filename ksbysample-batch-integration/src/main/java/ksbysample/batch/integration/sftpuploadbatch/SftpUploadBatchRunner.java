package ksbysample.batch.integration.sftpuploadbatch;

import com.jcraft.jsch.ChannelSftp;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SftpUploadBatchRunner implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String BATCH_NAME = "SftpUploadBatch";

    private final String PATH_LOCAL_DATA_DIR = "C:\\Batch\\data";
    private final String PATH_LOCAL_IMAGES_DIR = PATH_LOCAL_DATA_DIR + "\\images";

    private final String PATH_REMOTE_ROOT_DIR = "/";
    private final String PATH_REMOTE_IMAGES_DIR = "/images";
    private final Expression REMOTE_ROOT_DIR = new LiteralExpression(PATH_REMOTE_ROOT_DIR);
    private final Expression REMOTE_IMAGES_DIR = new LiteralExpression(PATH_REMOTE_IMAGES_DIR);

    @Autowired
    private SessionFactory<ChannelSftp.LsEntry> sessionFactory;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        SftpRemoteFileTemplate sftpClient = new SftpRemoteFileTemplate(sessionFactory);

        // sent ファイルがあるかチェックし、ある場合には処理を終了する
        logger.info("sent ファイルがあるかチェックします。");
        sftpClient.setRemoteDirectoryExpression(REMOTE_ROOT_DIR);
        if (sftpClient.exists("sent")) {
            logger.info("sent ファイルがあるため、ファイルアップロード処理を中断します。");
            return;
        }

        // ローカルの data.csv をリモートの / の下へアップロードする
        logger.info("data.csv をアップロードします。");
        sftpClient.send(fileMessage(PATH_LOCAL_DATA_DIR + "\\data.csv"), FileExistsMode.REPLACE);

        // ローカルの images ディレクトリの下にあるファイルを全て
        // リモートの /images の下へアップロードする
        logger.info("images ディレクトリの下のファイルを全てアップロードします。");
        List<Path> imagesPathList;
        try (Stream<Path> imagesPathStream
                     = Files.walk(Paths.get(PATH_LOCAL_IMAGES_DIR), FileVisitOption.FOLLOW_LINKS)) {
            imagesPathList = imagesPathStream
                    .map(Path::toAbsolutePath)
                    .filter(p -> !StringUtils.equals(p.toString(), PATH_LOCAL_IMAGES_DIR))
                    .collect(Collectors.toList());
        }
        sftpClient.setRemoteDirectoryExpression(REMOTE_IMAGES_DIR);
        imagesPathList.forEach(p -> sftpClient.send(fileMessage(p.toString()), FileExistsMode.REPLACE));

        // sent ファイルをアップロードする
        logger.info("sent ファイルをアップロードします。");
        sftpClient.setRemoteDirectoryExpression(REMOTE_ROOT_DIR);
        sftpClient.send(fileMessage(PATH_LOCAL_DATA_DIR + "\\sent"), FileExistsMode.REPLACE);
    }

    private Message<File> fileMessage(String path) {
        File file = Paths.get(path).toFile();
        return MessageBuilder.withPayload(file).build();
    }

    @Configuration
    public static class SftpUploadBatchConfig {

        @Bean
        @ConditionalOnProperty(value = { "batch.execute" }, havingValue = SftpUploadBatchRunner.BATCH_NAME)
        public SessionFactory<ChannelSftp.LsEntry> sftpSessionFactory() {
            DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
            factory.setHost("localhost");
            factory.setPort(22);
            factory.setUser("test");
            factory.setPassword("test");
            factory.setAllowUnknownKeys(true);
            return new CachingSessionFactory<>(factory);
        }

        @Bean
        @ConditionalOnProperty(value = { "batch.execute" }, havingValue = SftpUploadBatchRunner.BATCH_NAME)
        public ApplicationRunner applicationRunner() {
            return new SftpUploadBatchRunner();
        }

    }

}
