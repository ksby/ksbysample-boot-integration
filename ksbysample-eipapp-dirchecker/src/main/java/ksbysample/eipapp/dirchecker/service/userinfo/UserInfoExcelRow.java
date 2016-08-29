package ksbysample.eipapp.dirchecker.service.userinfo;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
public class UserInfoExcelRow {

    private String username;

    private String password;

    private String mailAddress;

    private String roles;

    public List<String> getRoleListFromRoles() {
        List<String> result = Collections.EMPTY_LIST;
        if (StringUtils.isNotBlank(this.roles)) {
            String[] roleArray = roles.split(",");
            result = Arrays.asList(roleArray);
        }
        return result;
    }

}
