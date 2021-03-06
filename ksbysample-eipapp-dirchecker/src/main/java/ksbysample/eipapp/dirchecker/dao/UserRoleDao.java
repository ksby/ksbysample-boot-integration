package ksbysample.eipapp.dirchecker.dao;

import ksbysample.eipapp.dirchecker.entity.UserRole;
import ksbysample.eipapp.dirchecker.util.doma.ComponentAndAutowiredDomaConfig;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

/**
 */
@Dao
@ComponentAndAutowiredDomaConfig
public interface UserRoleDao {

    /**
     * @param roleId
     * @return the UserRole entity
     */
    @Select
    UserRole selectById(Long roleId);

    /**
     * @param entity
     * @return affected rows
     */
    @Insert
    int insert(UserRole entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Update
    int update(UserRole entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Delete
    int delete(UserRole entity);
}