package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotMenuDishRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 只读商品标价。不选会员价，避免拿另一套价格来下单。
 */
@Mapper
public interface HotpotMenuDishMapper {

    @Select("select id, shop_id, store_name, price, is_show from yshop_store_product where deleted = 0 and id = #{id}")
    HotpotMenuDishRow selectByProductId(@Param("id") Long id);

}
