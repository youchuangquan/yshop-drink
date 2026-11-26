package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSessionPotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HotpotSessionPotMapper extends BaseMapperX<HotpotSessionPotDO> {

    @Select("select id, shop_id, session_id, pot_id, name, price_fen from yshop_hotpot_session_pot where deleted = 0 and shop_id = #{shopId}")
    List<HotpotSessionPotDO> selectByShop(@Param("shopId") Long shopId);

}
