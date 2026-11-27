package co.yixiang.yshop.module.shop.dal.mysql.shopads;

import java.util.*;

import co.yixiang.yshop.framework.common.pojo.PageResult;
import co.yixiang.yshop.framework.common.shop.ShopAccess;
import co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils;
import co.yixiang.yshop.framework.mybatis.core.query.LambdaQueryWrapperX;
import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.shop.dal.dataobject.shopads.ShopAdsDO;
import org.apache.ibatis.annotations.Mapper;
import co.yixiang.yshop.module.shop.controller.admin.shopads.vo.*;

/**
 * 广告图管理 Mapper
 *
 * @author yshop
 */
@Mapper
public interface ShopAdsMapper extends BaseMapperX<ShopAdsDO> {

    default PageResult<ShopAdsDO> selectPage(ShopAdsPageReqVO reqVO) {
        String shopId = ShopAccess.shopKey(ShopSecurityUtils.currentStaffShop(null));
        return selectPage(reqVO, new LambdaQueryWrapperX<ShopAdsDO>()
                .eq(ShopAdsDO::getShopId, shopId)
                .eqIfPresent(ShopAdsDO::getImage, reqVO.getImage())
                .eqIfPresent(ShopAdsDO::getIsSwitch, reqVO.getIsSwitch())
                .eqIfPresent(ShopAdsDO::getWeigh, reqVO.getWeigh())
                .likeIfPresent(ShopAdsDO::getShopName, reqVO.getShopName())
                .betweenIfPresent(ShopAdsDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ShopAdsDO::getId));
    }

    default List<ShopAdsDO> selectList(ShopAdsExportReqVO reqVO) {
        String shopId = ShopAccess.shopKey(ShopSecurityUtils.currentStaffShop(reqVO.getShopId()));
        return selectList(new LambdaQueryWrapperX<ShopAdsDO>()
                .eq(ShopAdsDO::getShopId, shopId)
                .eqIfPresent(ShopAdsDO::getImage, reqVO.getImage())
                .eqIfPresent(ShopAdsDO::getIsSwitch, reqVO.getIsSwitch())
                .eqIfPresent(ShopAdsDO::getWeigh, reqVO.getWeigh())
                .betweenIfPresent(ShopAdsDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ShopAdsDO::getId));
    }

}
