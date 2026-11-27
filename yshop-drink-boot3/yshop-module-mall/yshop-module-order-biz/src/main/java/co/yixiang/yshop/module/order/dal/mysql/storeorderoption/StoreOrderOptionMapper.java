package co.yixiang.yshop.module.order.dal.mysql.storeorderoption;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.order.dal.dataobject.storeorderoption.StoreOrderOptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StoreOrderOptionMapper extends BaseMapperX<StoreOrderOptionDO> {

    @Select("select id, order_id, shop_id, option_id, kind, name, unit_price, number, amount "
            + "from yshop_store_order_option where deleted = 0 and shop_id = #{shopId} and order_id = #{orderId}")
    List<StoreOrderOptionDO> selectByOrderAndShop(@Param("orderId") String orderId, @Param("shopId") Long shopId);

}
