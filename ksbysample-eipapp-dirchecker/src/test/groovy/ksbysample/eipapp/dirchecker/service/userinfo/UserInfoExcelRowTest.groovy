package ksbysample.eipapp.dirchecker.service.userinfo

import spock.lang.Specification
import spock.lang.Unroll

class UserInfoExcelRowTest extends Specification {

    @Unroll
    def "GetRoleListFromRoles(#roles) --> #result"() {
        given:
        UserInfoExcelRow userInfoExcelRow = new UserInfoExcelRow()
        userInfoExcelRow.roles = roles

        expect:
        userInfoExcelRow.getRoleListFromRoles() == result

        where:
        roles                  || result
        null                   || []
        ""                     || []
        "ROLE_USER"            || ["ROLE_USER"]
        "ROLE_USER,ROLE_ADMIN" || ["ROLE_USER", "ROLE_ADMIN"]
    }

}
