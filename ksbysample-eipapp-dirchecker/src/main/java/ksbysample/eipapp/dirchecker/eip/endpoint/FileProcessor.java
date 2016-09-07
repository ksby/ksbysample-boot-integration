package ksbysample.eipapp.dirchecker.eip.endpoint;

import ksbysample.eipapp.dirchecker.service.userinfo.UserInfoService;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

@MessageEndpoint
public class FileProcessor {

    @Autowired
    private UserInfoService userInfoService;

    @ServiceActivator(inputChannel = "excelToDbChannel")
    public void process(Message<File> message)
            throws InvalidFormatException, SAXException, IOException {
        File file = message.getPayload();
        userInfoService.loadUserInfoFromExcel(file);
    }

}
