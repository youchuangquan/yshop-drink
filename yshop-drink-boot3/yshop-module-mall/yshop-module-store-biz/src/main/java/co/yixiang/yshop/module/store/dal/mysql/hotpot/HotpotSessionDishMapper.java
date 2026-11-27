package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSessionDishDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HotpotSessionDishMapper extends BaseMapperX<HotpotSessionDishDO> {

    @Select("select id, shop_id, session_id, product_id, name, price_fen, quantity, served_qty, refunded_qty from yshop_hotpot_session_dish where deleted = 0 and shop_id = #{shopId}")
    List<HotpotSessionDishDO> selectByShop(@Param("shopId") Long shopId);

    @Update("update yshop_hotpot_session_dish set served_qty = #{servedQty}, refunded_qty = #{refundedQty} where id = #{id} and shop_id = #{shopId} and session_id = #{sessionId} and deleted = 0 and #{servedQty} >= 0 and #{refundedQty} >= 0 and #{servedQty} + #{refundedQty} <= quantity")
    int updateQty(@Param("id") Long id, @Param("shopId") Long shopId, @Param("sessionId") Long sessionId,
                  @Param("servedQty") int servedQty, @Param("refundedQty") int refundedQty);

}
