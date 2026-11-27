package co.yixiang.yshop.module.member.dal.mysql.usershopintegral;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.member.dal.dataobject.usershopintegral.ShopUserIntegralDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ShopUserIntegralMapper extends BaseMapperX<ShopUserIntegralDO> {

    @Select("SELECT integral FROM yshop_user_shop_integral WHERE uid = #{uid} AND shop_id = #{shopId} AND deleted = 0")
    BigDecimal selectIntegral(@Param("uid") Long uid, @Param("shopId") Long shopId);

    @Update("UPDATE yshop_user_shop_integral SET integral = integral + #{amount} WHERE uid = #{uid} AND shop_id = #{shopId} AND deleted = 0")
    int add(@Param("uid") Long uid, @Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    @Update("UPDATE yshop_user_shop_integral SET integral = integral - #{amount} WHERE uid = #{uid} AND shop_id = #{shopId} AND deleted = 0 AND integral >= #{amount}")
    int deduct(@Param("uid") Long uid, @Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

}
