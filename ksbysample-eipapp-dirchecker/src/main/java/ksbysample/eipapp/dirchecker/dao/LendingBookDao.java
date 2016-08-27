package ksbysample.eipapp.dirchecker.dao;

import ksbysample.eipapp.dirchecker.entity.LendingBook;
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
public interface LendingBookDao {

    /**
     * @param lendingBookId
     * @return the LendingBook entity
     */
    @Select
    LendingBook selectById(Long lendingBookId);

    /**
     * @param lendingBookId
     * @param version
     * @return the LendingBook entity
     */
    @Select(ensureResult = true)
    LendingBook selectByIdAndVersion(Long lendingBookId, Long version);

    /**
     * @param entity
     * @return affected rows
     */
    @Insert
    int insert(LendingBook entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Update
    int update(LendingBook entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Delete
    int delete(LendingBook entity);
}