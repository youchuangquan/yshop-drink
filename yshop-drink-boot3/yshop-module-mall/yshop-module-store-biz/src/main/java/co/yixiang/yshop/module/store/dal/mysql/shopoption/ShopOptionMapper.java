package co.yixiang.yshop.module.store.dal.mysql.shopoption;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.shopoption.ShopOptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShopOptionMapper extends BaseMapperX<ShopOptionDO> {

    @Select("select id, shop_id, kind, name, price from yshop_shop_option where deleted = 0 and shop_id = #{shopId}")
    List<ShopOptionDO> selectByShop(@Param("shopId") Long shopId);

    @Update("update yshop_shop_option set deleted = 1 where shop_id = #{shopId} and deleted = 0")
    int deleteByShop(@Param("shopId") Long shopId);

}
