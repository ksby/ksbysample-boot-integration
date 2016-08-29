package ksbysample.eipapp.dirchecker.service.userinfo;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.jxls.reader.ReaderBuilder;
import org.jxls.reader.XLSReadStatus;
import org.jxls.reader.XLSReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserInfoService {

    private static final String CLASSPATH_USERINFO_EXCEL_CFG_XML
            = "ksbysample/eipapp/dirchecker/service/userinfo/userinfo-excel-cfg.xml";

    public List<UserInfoExcelRow> loadFromExcelToList(File excelFile)
            throws IOException, SAXException, InvalidFormatException {
        Resource rsExcelCfgXml = new ClassPathResource(CLASSPATH_USERINFO_EXCEL_CFG_XML);
        Resource rsUserInfoExcel = new FileSystemResource(excelFile.getAbsolutePath());

        XLSReader reader = ReaderBuilder.buildFromXML(rsExcelCfgXml.getFile());

        List<UserInfoExcelRow> userInfoExcelRowList = new ArrayList<>();
        Map<String, Object> beans = new HashMap<>();
        beans.put("userInfoExcelRow", new UserInfoExcelRow());
        beans.put("userInfoExcelRowList", userInfoExcelRowList);

        try (InputStream isUserInfoExcel = new BufferedInputStream(rsUserInfoExcel.getInputStream())) {
            XLSReadStatus status = reader.read(isUserInfoExcel, beans);
        }

        return userInfoExcelRowList;
    }

}
