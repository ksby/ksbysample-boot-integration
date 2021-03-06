package ksbysample.eipapp.dirchecker.dao;

import ksbysample.eipapp.dirchecker.entity.LendingApp;
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
public interface LendingAppDao {

    /**
     * @param lendingAppId
     * @return the LendingApp entity
     */
    @Select
    LendingApp selectById(Long lendingAppId);

    /**
     * @param lendingAppId
     * @param version
     * @return the LendingApp entity
     */
    @Select(ensureResult = true)
    LendingApp selectByIdAndVersion(Long lendingAppId, Long version);

    /**
     * @param entity
     * @return affected rows
     */
    @Insert
    int insert(LendingApp entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Update
    int update(LendingApp entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Delete
    int delete(LendingApp entity);
}