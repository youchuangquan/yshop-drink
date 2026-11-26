package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.ShopPotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShopPotMapper extends BaseMapperX<ShopPotDO> {

    @Select("select id, shop_id, name, price_fen from yshop_shop_pot where deleted = 0 and shop_id = #{shopId}")
    List<ShopPotDO> selectByShop(@Param("shopId") Long shopId);

}
