package co.yixiang.yshop.module.order.dal.mysql.ordernumber;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.order.dal.dataobject.ordernumber.OrderNumberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单 Mapper
 *
 * @author yshop
 */
@Mapper
public interface OrderNumberMapper extends BaseMapperX<OrderNumberDO> {

    @Select("select id, order_id, shop_id, number_no from yshop_order_number where shop_id = #{shopId}")
    List<OrderNumberDO> selectByShop(@Param("shopId") Long shopId);

}
