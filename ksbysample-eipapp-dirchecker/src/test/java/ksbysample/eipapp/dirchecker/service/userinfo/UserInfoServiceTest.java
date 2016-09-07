package ksbysample.eipapp.dirchecker.service.userinfo;

import ksbysample.common.test.rule.db.*;
import ksbysample.eipapp.dirchecker.Application;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.csv.CsvDataSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.sql.DataSource;
import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class UserInfoServiceTest {

    private static final String CLASSPATH_EXCEL_FOR_TEST
            = "ksbysample/eipapp/dirchecker/service/userinfo/Book1.xlsx";

    @Rule
    @Autowired
    public TestDataResource testDataResource;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserInfoService userInfoService;

    @Test
    @NoUseTestDataResource
    public void loadFromExcelToList() throws Exception {
        Resource resource = new ClassPathResource(CLASSPATH_EXCEL_FOR_TEST);
        List<UserInfoExcelRow> userInfoExcelRowList = userInfoService.loadFromExcelToList(resource.getFile());
        assertThat(userInfoExcelRowList).hasSize(2);
        assertThat(userInfoExcelRowList).extracting("username", "password", "mailAddress", "roles")
                .containsOnly(tuple("yota takahashi", "12345678", "yota.takahashi@test.co.jp", "ROLE_USER")
                        , tuple("aoi inoue", "abcdefgh", "aoi.inoue@sample.com", "ROLE_ADMIN,ROLE_USER"));
    }

    @Test
    @TestData("service/userinfo/testdata/001")
    public void loadUserInfoFromExcel() throws Exception {
        Resource resource = new ClassPathResource(CLASSPATH_EXCEL_FOR_TEST);
        userInfoService.loadUserInfoFromExcel(resource.getFile());

        IDataSet dataSet = new CsvDataSet(new File("src/test/resources/ksbysample/eipapp/dirchecker/service/userinfo/assertdata/001"));
        TableDataAssert tableDataAssert = new TableDataAssert(dataSet, dataSource);
        tableDataAssert.assertEqualsByQuery(
                "select username, mail_address, enabled, cnt_badcredentials from user_info order by user_id"
                , "user_info"
                , new String[]{"username", "mail_address", "enabled", "cnt_badcredentials"}
                , AssertOptions.INCLUDE_COLUMN);
        tableDataAssert.assertEqualsByQuery("select role from user_role order by user_id, role_id"
                , "user_role"
                , new String[]{"role"}
                , AssertOptions.INCLUDE_COLUMN);
    }

}