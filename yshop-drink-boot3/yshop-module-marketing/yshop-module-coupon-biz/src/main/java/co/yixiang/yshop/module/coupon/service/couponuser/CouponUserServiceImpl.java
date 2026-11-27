package co.yixiang.yshop.module.coupon.service.couponuser;

import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import co.yixiang.yshop.module.coupon.controller.admin.couponuser.vo.*;
import co.yixiang.yshop.module.coupon.dal.dataobject.couponuser.CouponUserDO;
import co.yixiang.yshop.framework.common.pojo.PageResult;

import co.yixiang.yshop.module.coupon.convert.couponuser.CouponUserConvert;
import co.yixiang.yshop.module.coupon.dal.mysql.couponuser.CouponUserMapper;

import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.module.coupon.enums.ErrorCodeConstants.*;

/**
 * 用户领的优惠券 Service 实现类
 *
 * @author yshop
 */
@Service
@Validated
public class CouponUserServiceImpl implements CouponUserService {

    @Resource
    private CouponUserMapper userMapper;

    @Override
    public Integer createUser(CouponUserCreateReqVO createReqVO) {
        CouponUserDO user = CouponUserConvert.INSTANCE.convert(createReqVO);
        user.setShopId(co.yixiang.yshop.framework.common.shop.ShopAccess.shopKey(
                co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(createReqVO.getShopId())));
        userMapper.insert(user);
        // 返回
        return user.getId();
    }

    @Override
    public void updateUser(CouponUserUpdateReqVO updateReqVO) {
        CouponUserDO exists = userMapper.selectById(updateReqVO.getId());
        if (exists == null) {
            throw exception(COUPON_USER_NOT_EXISTS);
        }
        String shopId = co.yixiang.yshop.framework.common.shop.ShopAccess.shopKey(
                co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(updateReqVO.getShopId()));
        co.yixiang.yshop.framework.common.shop.ShopAccess.assertCouponBelongs(exists.getShopId(), Long.parseLong(shopId));
        CouponUserDO updateObj = CouponUserConvert.INSTANCE.convert(updateReqVO);
        updateObj.setShopId(shopId);
        userMapper.updateById(updateObj);
    }

    @Override
    public void deleteUser(Integer id) {
        CouponUserDO exists = userMapper.selectById(id);
        if (exists == null) {
            throw exception(COUPON_USER_NOT_EXISTS);
        }
        co.yixiang.yshop.framework.common.shop.ShopAccess.assertCouponBelongs(exists.getShopId(),
                co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(null));
        // 删除
        userMapper.deleteById(id);
    }

    private void validateUserExists(Integer id) {
        if (userMapper.selectById(id) == null) {
            throw exception(COUPON_USER_NOT_EXISTS);
        }
    }

    @Override
    public CouponUserDO getUser(Integer id) {
        CouponUserDO couponUser = userMapper.selectById(id);
        if (couponUser != null) {
            co.yixiang.yshop.framework.common.shop.ShopAccess.assertCouponBelongs(couponUser.getShopId(),
                    co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(null));
        }
        return couponUser;
    }

    @Override
    public List<CouponUserDO> getUserList(Integer id) {
        CouponUserExportReqVO exportReqVO = new CouponUserExportReqVO();
        exportReqVO.setCouponId(id);
        return userMapper.selectList(exportReqVO);
    }

    @Override
    public PageResult<CouponUserDO> getUserPage(CouponUserPageReqVO pageReqVO) {
        return userMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CouponUserDO> getUserList(CouponUserExportReqVO exportReqVO) {
        return userMapper.selectList(exportReqVO);
    }

}
