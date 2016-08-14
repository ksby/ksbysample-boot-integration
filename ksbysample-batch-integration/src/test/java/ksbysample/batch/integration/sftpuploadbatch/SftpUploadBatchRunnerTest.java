package ksbysample.batch.integration.sftpuploadbatch;

import com.icegreen.greenmail.util.GreenMailUtil;
import ksbysample.batch.integration.Application;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.Assert.*;

@Ignore("バッチの動作確認用のテストクラスなので、通常は @Ignore アノテーションを付加して実行されないようにします")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@TestPropertySource(properties = { "batch.execute=" + SftpUploadBatchRunner.BATCH_NAME })
public class SftpUploadBatchRunnerTest {

    @Test
    public void run() throws Exception {
        // 今は動作させたいだけなので Assert は書きません
    }

}
