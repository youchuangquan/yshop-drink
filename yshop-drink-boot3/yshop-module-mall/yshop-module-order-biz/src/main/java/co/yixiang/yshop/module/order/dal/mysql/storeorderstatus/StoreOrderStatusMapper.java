package co.yixiang.yshop.module.order.dal.mysql.storeorderstatus;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.order.dal.dataobject.storeorderstatus.StoreOrderStatusDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单操作记录 Mapper
 *
 * @author yshop
 */
@Mapper
public interface StoreOrderStatusMapper extends BaseMapperX<StoreOrderStatusDO> {

    @Select("SELECT id, oid, change_type, change_message, change_time FROM yshop_store_order_status "
            + "WHERE oid = #{oid} AND change_type = #{changeType} ORDER BY id ASC")
    List<StoreOrderStatusDO> selectByOrderAndType(@Param("oid") Long oid, @Param("changeType") String changeType);

}
