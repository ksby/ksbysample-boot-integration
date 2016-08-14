package ksbysample.common.test.rule.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.stereotype.Component;

import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class MailServerResource extends TestWatcher {

    private final GreenMail greenMail;

    public MailServerResource() {
        List<ServerSetup> serverSetupList = new ArrayList<>();
        serverSetupList.add(new ServerSetup(25, "localhost", ServerSetup.PROTOCOL_SMTP));
        serverSetupList.add(new ServerSetup(110, "localhost", ServerSetup.PROTOCOL_POP3));
        this.greenMail = new GreenMail((ServerSetup[]) serverSetupList.toArray(new ServerSetup[serverSetupList.size()]));
    }

    @Override
    protected void starting(Description description) {
        greenMail.start();
    }

    @Override
    protected void finished(Description description) {
        greenMail.stop();
    }

    public GreenMail getGreenMail() {
        return this.greenMail;
    }

    public int getMessagesCount() {
        return greenMail.getReceivedMessages().length;
    }

    public List<MimeMessage> getMessages() {
        return Arrays.asList(greenMail.getReceivedMessages());
    }

    public MimeMessage getFirstMessage() {
        MimeMessage message = null;
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        if (receivedMessages.length > 0) {
            message = receivedMessages[0];
        }
        return message;
    }

}
