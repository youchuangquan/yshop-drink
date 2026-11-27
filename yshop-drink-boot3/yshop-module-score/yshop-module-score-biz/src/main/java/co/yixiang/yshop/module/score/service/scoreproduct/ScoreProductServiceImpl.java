package co.yixiang.yshop.module.score.service.scoreproduct;

import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import co.yixiang.yshop.module.score.controller.admin.scoreproduct.vo.*;
import co.yixiang.yshop.module.score.dal.dataobject.scoreproduct.ScoreProductDO;
import co.yixiang.yshop.framework.common.pojo.PageResult;

import co.yixiang.yshop.module.score.convert.scoreproduct.ScoreProductConvert;
import co.yixiang.yshop.module.score.dal.mysql.scoreproduct.ScoreProductMapper;

import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.module.score.enums.ErrorCodeConstants.*;

/**
 * 积分产品 Service 实现类
 *
 * @author yshop
 */
@Service
@Validated
public class ScoreProductServiceImpl implements ScoreProductService {

    @Resource
    private ScoreProductMapper productMapper;

    @Override
    public Long createProduct(ScoreProductCreateReqVO createReqVO) {
        // 插入
        ScoreProductDO product = ScoreProductConvert.INSTANCE.convert(createReqVO);
        product.setShopId(co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(null));
        productMapper.insert(product);
        // 返回
        return product.getId();
    }

    @Override
    public void updateProduct(ScoreProductUpdateReqVO updateReqVO) {
        // 校验存在
        ScoreProductDO exists = productMapper.selectById(updateReqVO.getId());
        if (exists == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        long shopId = co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(null);
        co.yixiang.yshop.framework.common.shop.ShopAccess.assertSameShop(exists.getShopId(), shopId);
        ScoreProductDO updateObj = ScoreProductConvert.INSTANCE.convert(updateReqVO);
        updateObj.setShopId(shopId);
        productMapper.updateById(updateObj);
    }

    @Override
    public void deleteProduct(Long id) {
        // 校验存在
        requireStaffProduct(id);
        // 删除
        productMapper.deleteById(id);
    }

    private ScoreProductDO requireStaffProduct(Long id) {
        ScoreProductDO product = productMapper.selectById(id);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        co.yixiang.yshop.framework.common.shop.ShopAccess.assertSameShop(product.getShopId(),
                co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils.currentStaffShop(null));
        return product;
    }

    @Override
    public ScoreProductDO getProduct(Long id) {
        return requireStaffProduct(id);
    }

    @Override
    public List<ScoreProductDO> getProductList(Collection<Long> ids) {
        return productMapper.selectBatchIds(ids);
    }

    @Override
    public PageResult<ScoreProductDO> getProductPage(ScoreProductPageReqVO pageReqVO) {
        return productMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ScoreProductDO> getProductList(ScoreProductExportReqVO exportReqVO) {
        return productMapper.selectList(exportReqVO);
    }

}
