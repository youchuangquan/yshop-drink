package co.yixiang.yshop.module.coupon.service.coupon;

import co.yixiang.yshop.framework.common.pojo.PageResult;
import co.yixiang.yshop.module.coupon.controller.admin.coupon.vo.CouponCreateReqVO;
import co.yixiang.yshop.module.coupon.controller.admin.coupon.vo.CouponExportReqVO;
import co.yixiang.yshop.module.coupon.controller.admin.coupon.vo.CouponPageReqVO;
import co.yixiang.yshop.module.coupon.controller.admin.coupon.vo.CouponUpdateReqVO;
import co.yixiang.yshop.module.coupon.convert.coupon.CouponConvert;
import co.yixiang.yshop.module.coupon.dal.dataobject.coupon.CouponDO;
import co.yixiang.yshop.module.coupon.dal.mysql.coupon.CouponMapper;
import co.yixiang.yshop.module.store.dal.dataobject.storeshop.StoreShopDO;
import co.yixiang.yshop.module.store.dal.mysql.storeshop.StoreShopMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.module.coupon.enums.ErrorCodeConstants.COUPON_NOT_EXISTS;

/**
 * 优惠券 Service 实现类
 *
 * @author yshop
 */
@Service
@Validated
public class CouponServiceImpl implements CouponService {

    @Resource
    private CouponMapper Mapper;
    @Resource
    private StoreShopMapper storeShopMapper;

    @Override
    public Long create(CouponCreateReqVO createReqVO) {
        // 插入
        CouponDO  couponDO = CouponConvert.INSTANCE.convert(createReqVO);
        String shopId = co.yixiang.yshop.framework.common.shop.ShopAccess.shopKey(
                co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(createReqVO.getShopId()));
        couponDO.setShopId(shopId);
        StoreShopDO storeShopDO = storeShopMapper.selectById(shopId);
        couponDO.setShopName(storeShopDO.getName());
        Mapper.insert(couponDO);
        // 返回
        return couponDO.getId();
    }

    @Override
    public void update(CouponUpdateReqVO updateReqVO) {
        // 校验存在
        CouponDO exists = Mapper.selectById(updateReqVO.getId());
        if (exists == null) {
            throw exception(COUPON_NOT_EXISTS);
        }
        String shopId = co.yixiang.yshop.framework.common.shop.ShopAccess.shopKey(
                co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(updateReqVO.getShopId()));
        co.yixiang.yshop.framework.common.shop.ShopAccess.assertCouponBelongs(exists.getShopId(), Long.parseLong(shopId));
        CouponDO updateObj = CouponConvert.INSTANCE.convert(updateReqVO);
        updateObj.setShopId(shopId);
        StoreShopDO storeShopDO = storeShopMapper.selectById(shopId);
        updateObj.setShopName(storeShopDO.getName());
        Mapper.updateById(updateObj);
    }

    @Override
    public void delete(Long id) {
        // 校验存在
        validateExists(id);
        // 删除
        Mapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (Mapper.selectById(id) == null) {
            throw exception(COUPON_NOT_EXISTS);
        }
    }

    @Override
    public CouponDO get(Long id) {
        return Mapper.selectById(id);
    }

    @Override
    public List<CouponDO> getList() {
        String shopId = co.yixiang.yshop.framework.common.shop.ShopAccess.shopKey(
                co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(null));
        return Mapper.selectList(new LambdaQueryWrapper<CouponDO>().eq(CouponDO::getShopId, shopId));
    }

    @Override
    public PageResult<CouponDO> getPage(CouponPageReqVO pageReqVO) {
        return Mapper.selectPage(pageReqVO);
    }

    @Override
    public List<CouponDO> getList(CouponExportReqVO exportReqVO) {
        return Mapper.selectList(exportReqVO);
    }

}
