package ksbysample.eipapp.dirchecker.service.userinfo;

import ksbysample.eipapp.dirchecker.dao.UserInfoDao;
import ksbysample.eipapp.dirchecker.dao.UserRoleDao;
import ksbysample.eipapp.dirchecker.entity.UserInfo;
import ksbysample.eipapp.dirchecker.entity.UserRole;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.jxls.reader.ReaderBuilder;
import org.jxls.reader.XLSReadStatus;
import org.jxls.reader.XLSReader;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserInfoService {

    private static final String CLASSPATH_USERINFO_EXCEL_CFG_XML
            = "ksbysample/eipapp/dirchecker/service/userinfo/userinfo-excel-cfg.xml";

    @Autowired
    private UserInfoDao userInfoDao;

    @Autowired
    private UserRoleDao userRoleDao;

    public void loadUserInfoFromExcel(File excelFile)
            throws InvalidFormatException, SAXException, IOException {
        // Excel ファイルからデータを読み込む
        List<UserInfoExcelRow> userInfoExcelRowList = loadFromExcelToList(excelFile);

        // user_info, user_role テーブルに登録する
        userInfoExcelRowList.forEach(userInfoExcelRow -> {
            UserInfo userInfo = makeUserInfo(userInfoExcelRow);
            userInfoDao.insert(userInfo);

            userInfoExcelRow.getRoleListFromRoles().forEach(role -> {
                UserRole userRole = makeUserRole(userInfo.getUserId(), role);
                userRoleDao.insert(userRole);
            });
        });
    }

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

    private UserInfo makeUserInfo(UserInfoExcelRow userInfoExcelRow) {
        UserInfo userInfo = new UserInfo();
        BeanUtils.copyProperties(userInfoExcelRow, userInfo);
        userInfo.setPassword(new BCryptPasswordEncoder().encode(userInfoExcelRow.getPassword()));
        userInfo.setEnabled((short) 1);
        userInfo.setCntBadcredentials((short) 0);
        userInfo.setExpiredAccount(LocalDateTime.now().plusMonths(3));
        userInfo.setExpiredPassword(LocalDateTime.now().plusMonths(1));
        return userInfo;
    }

    private UserRole makeUserRole(Long userId, String role) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRole(role);
        return userRole;
    }

}
