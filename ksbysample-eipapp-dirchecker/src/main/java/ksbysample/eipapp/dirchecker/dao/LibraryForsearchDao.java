package ksbysample.eipapp.dirchecker.dao;

import ksbysample.eipapp.dirchecker.entity.LibraryForsearch;
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
public interface LibraryForsearchDao {

    /**
     * @param systemid
     * @return the LibraryForsearch entity
     */
    @Select
    LibraryForsearch selectById(String systemid);

    /**
     * @param entity
     * @return affected rows
     */
    @Insert
    int insert(LibraryForsearch entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Update
    int update(LibraryForsearch entity);

    /**
     * @param entity
     * @return affected rows
     */
    @Delete
    int delete(LibraryForsearch entity);
}