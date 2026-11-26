package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSessionDishDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HotpotSessionDishMapper extends BaseMapperX<HotpotSessionDishDO> {

    @Select("select id, shop_id, session_id, product_id, name, price_fen, quantity from yshop_hotpot_session_dish where deleted = 0 and shop_id = #{shopId}")
    List<HotpotSessionDishDO> selectByShop(@Param("shopId") Long shopId);

}
