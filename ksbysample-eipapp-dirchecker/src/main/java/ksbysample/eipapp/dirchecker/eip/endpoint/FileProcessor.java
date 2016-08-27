package ksbysample.eipapp.dirchecker.eip.endpoint;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;

import java.io.File;

@MessageEndpoint
public class FileProcessor {

    @ServiceActivator(inputChannel = "excelToDbChannel")
    public void process(Message<File> message) throws Exception {
        File file = message.getPayload();
        System.out.println(file.getAbsolutePath());
    }

}
