package co.yixiang.yshop.module.member.dal.mysql.userbill;

import java.util.*;

import co.yixiang.yshop.framework.common.pojo.PageResult;
import co.yixiang.yshop.framework.mybatis.core.query.LambdaQueryWrapperX;
import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.framework.security.core.LoginUser;
import co.yixiang.yshop.framework.security.core.util.SecurityFrameworkUtils;
import co.yixiang.yshop.module.member.dal.dataobject.userbill.UserBillDO;
import co.yixiang.yshop.module.member.enums.BillDetailEnum;
import org.apache.ibatis.annotations.Mapper;
import co.yixiang.yshop.module.member.controller.admin.userbill.vo.*;

/**
 * 用户账单 Mapper
 *
 * @author yshop
 */
@Mapper
public interface UserBillMapper extends BaseMapperX<UserBillDO> {

    default PageResult<UserBillDO> selectPage(UserBillPageReqVO reqVO) {
        LambdaQueryWrapperX<UserBillDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eqIfPresent(UserBillDO::getUid, reqVO.getUid())
                .eqIfPresent(UserBillDO::getLinkId, reqVO.getLinkId())
                .eqIfPresent(UserBillDO::getPm, reqVO.getPm())
                .eqIfPresent(UserBillDO::getTitle, reqVO.getTitle())
                .eqIfPresent(UserBillDO::getCategory, reqVO.getCategory())
                .eqIfPresent(UserBillDO::getType, reqVO.getType())
                .eqIfPresent(UserBillDO::getNumber, reqVO.getNumber())
                .eqIfPresent(UserBillDO::getBalance, reqVO.getBalance())
                .eqIfPresent(UserBillDO::getMark, reqVO.getMark())
                .betweenIfPresent(UserBillDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(UserBillDO::getStatus, reqVO.getStatus());
        wrapper.orderByDesc(UserBillDO::getId);
        applyIntegralShopScope(wrapper);
        return selectPage(reqVO, wrapper);
    }

    default List<UserBillDO> selectList(UserBillExportReqVO reqVO) {
        LambdaQueryWrapperX<UserBillDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eqIfPresent(UserBillDO::getUid, reqVO.getUid())
                .eqIfPresent(UserBillDO::getLinkId, reqVO.getLinkId())
                .eqIfPresent(UserBillDO::getPm, reqVO.getPm())
                .eqIfPresent(UserBillDO::getTitle, reqVO.getTitle())
                .eqIfPresent(UserBillDO::getCategory, reqVO.getCategory())
                .eqIfPresent(UserBillDO::getType, reqVO.getType())
                .eqIfPresent(UserBillDO::getNumber, reqVO.getNumber())
                .eqIfPresent(UserBillDO::getBalance, reqVO.getBalance())
                .eqIfPresent(UserBillDO::getMark, reqVO.getMark())
                .betweenIfPresent(UserBillDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(UserBillDO::getStatus, reqVO.getStatus());
        wrapper.orderByDesc(UserBillDO::getId);
        applyIntegralShopScope(wrapper);
        return selectList(wrapper);
    }

    private static void applyIntegralShopScope(LambdaQueryWrapperX<UserBillDO> wrapper) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            return;
        }
        String integral = BillDetailEnum.CATEGORY_2.getValue();
        Long shopId = user.getShopId();
        if (shopId != null && shopId > 0) {
            wrapper.and(query -> query.eq(UserBillDO::getShopId, shopId)
                    .or(other -> other.isNull(UserBillDO::getShopId).ne(UserBillDO::getCategory, integral)));
            return;
        }
        wrapper.ne(UserBillDO::getCategory, integral);
    }

}
