package ksbysample.eipapp.dirchecker.service.userinfo;

import ksbysample.eipapp.dirchecker.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class UserInfoServiceTest {

    private static final String CLASSPATH_EXCEL_FOR_TEST
            = "ksbysample/eipapp/dirchecker/service/userinfo/Book1.xlsx";

    @Autowired
    private UserInfoService userInfoService;

    @Test
    public void loadFromExcelToList() throws Exception {
        Resource resource = new ClassPathResource(CLASSPATH_EXCEL_FOR_TEST);
        List<UserInfoExcelRow> userInfoExcelRowList = userInfoService.loadFromExcelToList(resource.getFile());
        assertThat(userInfoExcelRowList).hasSize(2);
        assertThat(userInfoExcelRowList).extracting("username", "password", "mailAddress", "roles")
                .containsOnly(tuple("yota takahashi", "12345678", "yota.takahashi@test.co.jp", "ROLE_USER")
                        , tuple("aoi inoue", "abcdefgh", "aoi.inoue@sample.com", "ROLE_ADMIN,ROLE_USER"));
    }

}