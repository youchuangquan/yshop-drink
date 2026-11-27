package co.yixiang.yshop.module.order.service.storeorder;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import co.yixiang.yshop.framework.common.constant.ShopConstants;
import co.yixiang.yshop.framework.common.enums.OrderInfoEnum;
import co.yixiang.yshop.framework.common.enums.ShopCommonEnum;
import co.yixiang.yshop.framework.common.exception.ErrorCode;
import co.yixiang.yshop.framework.common.exception.ServiceException;
import co.yixiang.yshop.framework.common.order.MalatangQuote;
import co.yixiang.yshop.framework.common.order.OrderRequestGate;
import co.yixiang.yshop.framework.common.order.OrderRequestTenant;
import co.yixiang.yshop.framework.common.order.PayChannels;
import co.yixiang.yshop.framework.common.order.PaymentReconciliation;
import co.yixiang.yshop.framework.common.order.QueueTickets;
import co.yixiang.yshop.framework.common.order.ShopOptionCatalog;
import co.yixiang.yshop.framework.common.shop.ShopAccess;
import co.yixiang.yshop.framework.tenant.core.aop.TenantIgnore;
import co.yixiang.yshop.framework.tenant.core.context.TenantContextHolder;
import co.yixiang.yshop.module.coupon.dal.dataobject.couponuser.CouponUserDO;
import co.yixiang.yshop.module.coupon.service.couponuser.AppCouponUserService;
import co.yixiang.yshop.module.member.controller.app.user.vo.AppUserQueryVo;
import co.yixiang.yshop.module.member.dal.dataobject.user.MemberUserDO;
import co.yixiang.yshop.module.member.dal.dataobject.useraddress.UserAddressDO;
import co.yixiang.yshop.module.member.dal.dataobject.userbill.UserBillDO;
import co.yixiang.yshop.module.member.enums.BillDetailEnum;
import co.yixiang.yshop.module.member.service.user.MemberUserService;
import co.yixiang.yshop.module.member.service.useraddress.AppUserAddressService;
import co.yixiang.yshop.module.member.service.userbill.UserBillService;
import co.yixiang.yshop.module.member.service.usershopintegral.ShopUserIntegralService;
import co.yixiang.yshop.module.message.enums.WechatTempateEnum;
import co.yixiang.yshop.module.message.mq.producer.WeixinNoticeProducer;
import co.yixiang.yshop.module.message.redismq.msg.OrderMsg;
import co.yixiang.yshop.module.order.controller.app.order.param.AppAddonPickParam;
import co.yixiang.yshop.module.order.controller.app.order.param.AppOrderParam;
import co.yixiang.yshop.module.order.controller.app.order.param.AppPayParam;
import co.yixiang.yshop.module.order.controller.app.order.vo.AppMalatangQuoteVo;
import co.yixiang.yshop.module.order.controller.app.order.vo.AppOrderOptionVo;
import co.yixiang.yshop.module.order.controller.app.order.vo.AppStoreOrderQueryVo;
import co.yixiang.yshop.module.order.convert.storeorder.StoreOrderConvert;
import co.yixiang.yshop.module.order.dal.dataobject.ordernumber.OrderNumberDO;
import co.yixiang.yshop.module.order.dal.dataobject.storeorder.StoreOrderDO;
import co.yixiang.yshop.module.order.dal.dataobject.storeordercartinfo.StoreOrderCartInfoDO;
import co.yixiang.yshop.module.order.dal.dataobject.storeorderoption.StoreOrderOptionDO;
import co.yixiang.yshop.module.order.dal.dataobject.paynotice.PayNoticeDO;
import co.yixiang.yshop.module.order.dal.mysql.ordernumber.OrderNumberMapper;
import co.yixiang.yshop.module.order.dal.mysql.paynotice.PayNoticeMapper;
import co.yixiang.yshop.module.order.dal.mysql.storeorder.StoreOrderMapper;
import co.yixiang.yshop.module.order.dal.mysql.storeorderoption.StoreOrderOptionMapper;
import co.yixiang.yshop.module.order.enums.AppFromEnum;
import co.yixiang.yshop.module.order.enums.OrderLogEnum;
import co.yixiang.yshop.module.order.enums.OrderStatusEnum;
import co.yixiang.yshop.module.order.enums.PayTypeEnum;
import co.yixiang.yshop.module.order.service.storeorder.dto.StatusDto;
import co.yixiang.yshop.module.order.service.storeordercartinfo.StoreOrderCartInfoService;
import co.yixiang.yshop.module.order.service.storeorderstatus.StoreOrderStatusService;
import co.yixiang.yshop.module.product.dal.dataobject.storeproduct.StoreProductDO;
import co.yixiang.yshop.module.product.dal.dataobject.storeproductattrvalue.StoreProductAttrValueDO;
import co.yixiang.yshop.module.product.service.storeproduct.AppStoreProductService;
import co.yixiang.yshop.module.product.service.storeproductattrvalue.StoreProductAttrValueService;
import co.yixiang.yshop.module.store.controller.admin.storeshop.vo.ShopOptionItemVO;
import co.yixiang.yshop.module.store.convert.storeshop.StoreShopConvert;
import co.yixiang.yshop.module.store.dal.dataobject.storeshop.StoreShopDO;
import co.yixiang.yshop.module.store.service.shopoption.ShopOptionService;
import co.yixiang.yshop.module.store.service.storeshop.AppStoreShopService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.LOCKED;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.module.member.enums.ErrorCodeConstants.COUPON_NOT_CONDITION;
import static co.yixiang.yshop.module.member.enums.ErrorCodeConstants.USER_ADDRESS_NOT_EXISTS;
import static co.yixiang.yshop.module.order.enums.ErrorCodeConstants.*;

/**
 * 订单 Service 实现类
 *
 * @author yshop
 */
@Slf4j
@Service
@Validated
public class AppStoreOrderServiceImpl extends ServiceImpl<StoreOrderMapper,StoreOrderDO> implements AppStoreOrderService {

    @Resource
    private StoreOrderMapper storeOrderMapper;

    @Resource
    private AppUserAddressService appUserAddressService;
    @Resource
    private MemberUserService userService;
    @Resource
    private AppStoreProductService appStoreProductService;
    @Resource
    private StoreOrderCartInfoService storeOrderCartInfoService;
    @Resource
    private StoreOrderStatusService storeOrderStatusService;
    @Resource
    private UserBillService billService;
    @Resource
    private WeixinNoticeProducer weixinNoticeProducer;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StoreProductAttrValueService storeProductAttrValueService;
    @Resource
    private AppStoreShopService appStoreShopService;
    @Resource
    private OrderNumberMapper orderNumberMapper;
    @Resource
    private StoreOrderOptionMapper storeOrderOptionMapper;
    @Resource
    private ShopOptionService shopOptionService;
    @Resource
    private AppCouponUserService appCouponUserService;
    @Resource
    private ShopUserIntegralService shopUserIntegralService;
    @Resource
    private AsyncStoreOrderService asyncStoreOrderService;
    @Resource
    private PayNoticeMapper payNoticeMapper;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private UnsetLatePaymentHold latePaymentHold;

    private static final String LOCK_KEY = "cart:check:stock:lock";
    private static final String STOCK_LOCK_KEY = "cart:do:stock:lock";




    /**
     * 订单信息
     *
     * @param unique 唯一值或者单号
     * @param uid    用户id
     * @return YxStoreOrderQueryVo
     */
    @Override
    public AppStoreOrderQueryVo getOrderInfo(String unique, Long uid) {
        LambdaQueryWrapper<StoreOrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(
                i -> i.eq(StoreOrderDO::getOrderId, unique).or().eq(StoreOrderDO::getUnique, unique).or()
                        .eq(StoreOrderDO::getExtendOrderId, unique));
        if (uid != null) {
            wrapper.eq(StoreOrderDO::getUid, uid);
        }

        AppStoreOrderQueryVo appStoreOrderQueryVo = StoreOrderConvert.INSTANCE.convert1(storeOrderMapper.selectOne(wrapper));
        return appStoreOrderQueryVo;
    }


    /**
     * 创建订单
     *
     * @param uid 用户uid
     * @param param    param
     * @return YxStoreOrder
     */
    @Override
    public Map<String, Object> createOrder(Long uid, AppOrderParam param) {
        long shopId = ShopAccess.requirePositiveShopId(param.getShopId());
        return OrderRequestGate.open(param.getRequestKey(), false,
                key -> {
                    StoreOrderDO existing = findByRequestKey(uid, shopId, key);
                    if (existing == null) {
                        return null;
                    }
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderId", existing.getOrderId());
                    return map;
                },
                key -> new TransactionTemplate(transactionManager).execute(status -> doCreateOrder(uid, param, key)));
    }

    @Override
    public AppMalatangQuoteVo quote(Long uid, AppOrderParam param) {
        long shopId = ShopAccess.requirePositiveShopId(param.getShopId());
        StoreShopDO storeShopDO = appStoreShopService.getById(shopId);
        if (storeShopDO == null) {
            throw exception(co.yixiang.yshop.module.store.enums.ErrorCodeConstants.SHOP_NOT_EXISTS);
        }
        PricedGoods priced = priceGoods(uid, shopId, param);
        BigDecimal couponPrice = lookupCoupon(uid, shopId, param, priced.amount);
        MalatangQuote.Quote quote = quoteFor(shopId, storeShopDO, param, priced.amount, couponPrice);
        AppMalatangQuoteVo vo = new AppMalatangQuoteVo();
        vo.setGoodsAmount(quote.goodsAmount);
        vo.setFlavorOptionId(quote.flavorOptionId);
        vo.setFlavorName(quote.flavorName);
        vo.setFlavorAmount(quote.flavorAmount);
        vo.setAddonAmount(quote.addonAmount);
        vo.setPackingAmount(quote.packingAmount);
        vo.setDeliveryAmount(quote.deliveryAmount);
        vo.setCouponAmount(couponPrice);
        vo.setPayAmount(quote.payAmount);
        List<AppOrderOptionVo> addons = new ArrayList<>();
        for (MalatangQuote.AddonLine line : quote.addonLines) {
            AppOrderOptionVo row = new AppOrderOptionVo();
            row.setKind(ShopOptionCatalog.ADDON);
            row.setName(line.name);
            row.setUnitPrice(line.unitPrice);
            row.setNumber(line.number);
            row.setAmount(line.amount);
            addons.add(row);
        }
        vo.setAddons(addons);
        return vo;
    }

    /**
     * 旧报文不带 requestKey 时仍会创建订单。那一次不是防重。
     */
    private Map<String, Object> doCreateOrder(Long uid, AppOrderParam param, String requestKey) {
        if(OrderLogEnum.ORDER_TAKE_DESK.getValue().equals(param.getOrderType())
                && StrUtil.isBlank(param.getDeskNumber())){
            throw exception(STORE_ORDER_DESK_NOT);
        }
        List<String> productIds = param.getProductId();
        List<String> numbers = param.getNumber();
        List<String> specs = param.getSpec();
        List<String> cartIds = new ArrayList<>();

        long shopId = ShopAccess.requirePositiveShopId(param.getShopId());
        StoreShopDO storeShopDO = appStoreShopService.getById(shopId);
        if (storeShopDO == null) {
            throw exception(co.yixiang.yshop.module.store.enums.ErrorCodeConstants.SHOP_NOT_EXISTS);
        }

        PricedGoods priced = priceGoods(uid, shopId, param);
        BigDecimal sumPrice = priced.amount;
        int totalNum = priced.totalNum;
        BigDecimal deductionPrice = BigDecimal.ZERO;
        BigDecimal couponPrice = consumeCoupon(uid, shopId, param, sumPrice);
        MalatangQuote.Quote quote = quoteFor(shopId, storeShopDO, param, sumPrice, couponPrice);
        BigDecimal payPrice = quote.payAmount;
        if (payPrice == null || payPrice.signum() < 0) {
            throw exception(new ErrorCode(400, "报价金额不正确"));
        }



        //计算奖励积分
        BigDecimal gainIntegral = this.getGainIntegral(productIds, shopId);

        StoreOrderDO storeOrder = new StoreOrderDO();
        String orderSn = "";
        //todo 桌面点餐功能 商业版本才有 官网地址：https://www.yixiang.co
        if(OrderLogEnum.ORDER_TAKE_DESK.getValue().equals(param.getOrderType())
                && StrUtil.isNotBlank(param.getOrderId())){

        }else{
            //生成分布式唯一值
            orderSn = IdUtil.getSnowflake(0, 0).nextIdStr();

            // 取餐号等付款入账后再发，这里不占号。
            LocalDateTime localDateTime = LocalDateTime.now();
            storeOrder.setGetTime(localDateTime.plusMinutes(param.getGettime()));
            storeOrder.setShopId(storeShopDO.getId());
            storeOrder.setShopName(storeShopDO.getName());
            storeOrder.setUid(uid);
            storeOrder.setOrderId(orderSn);
            //处理如果是外卖 地址
            if(OrderLogEnum.ORDER_TAKE_OUT.getValue().equals(param.getOrderType())){
                if (StrUtil.isEmpty(param.getAddressId())) {
                    throw exception(SELECT_ADDRESS);
                }
                UserAddressDO userAddress = appUserAddressService.getById(param.getAddressId());
                if (ObjectUtil.isNull(userAddress)) {
                    throw exception(USER_ADDRESS_NOT_EXISTS);
                }
                storeOrder.setRealName(userAddress.getRealName());
                storeOrder.setUserPhone(userAddress.getPhone());
                storeOrder.setUserAddress(userAddress.getAddress() + " " + userAddress.getDetail());
            }
            storeOrder.setCartId(StrUtil.join(",", cartIds));
            storeOrder.setTotalNum(totalNum);
            storeOrder.setTotalPrice(sumPrice);
            BigDecimal postage = quote.deliveryAmount == null ? BigDecimal.ZERO : quote.deliveryAmount;
            storeOrder.setTotalPostage(postage);

            storeOrder.setCouponId(hasCoupon(param.getCouponId()) ? Integer.valueOf(param.getCouponId()) : 0);
            storeOrder.setCouponPrice(couponPrice == null ? BigDecimal.ZERO : couponPrice);
            storeOrder.setPayPrice(payPrice);
            storeOrder.setPayPostage(postage);
            storeOrder.setPackingPrice(quote.packingAmount);
            storeOrder.setFlavorOptionId(quote.flavorOptionId);
            storeOrder.setFlavorName(quote.flavorName);
            storeOrder.setFlavorPrice(quote.flavorAmount);
            storeOrder.setAddonPrice(quote.addonAmount);
            storeOrder.setDeductionPrice(deductionPrice);
            initializeFulfillment(storeOrder);
            storeOrder.setPayType(param.getPayType());
            storeOrder.setUseIntegral(BigDecimal.ZERO);
            storeOrder.setBackIntegral(BigDecimal.ZERO);
            storeOrder.setGainIntegral(gainIntegral);
            storeOrder.setMark(param.getRemark());
            storeOrder.setCost(BigDecimal.ZERO);
            //storeOrder.setUnique(key);
            storeOrder.setShippingType(OrderInfoEnum.SHIPPIING_TYPE_1.getValue());
            storeOrder.setOrderType(param.getOrderType());
            if (requestKey != null) {
                storeOrder.setRequestKey(requestKey);
            }
            storeOrder.setTenantId(OrderRequestTenant.slot(TenantContextHolder.getTenantId()));

            boolean res = this.save(storeOrder);
            if (!res) {
                throw exception(ORDER_GEN_FAIL);
            }
            persistOptions(shopId, orderSn, quote);
        }


        // 减库存加销量
        this.deStockIncSale(productIds,numbers,specs, shopId);


        //保存购物车商品信息，异步执行
        storeOrderCartInfoService.saveCartInfo(storeOrder.getId(), storeOrder.getOrderId(),productIds,numbers,specs);

        ////todo 桌面点餐功能 商业版本才有 官网地址：https://www.yixiang.co异步更新桌面信息



        //增加状态
        storeOrderStatusService.create(uid,storeOrder.getId(), OrderLogEnum.CREATE_ORDER.getValue(),
                OrderLogEnum.CREATE_ORDER.getDesc());

        //堂食点餐不需要
        if(!OrderLogEnum.ORDER_TAKE_DESK.getValue().equals(param.getOrderType())) {
            //加入延时队列，30分钟自动取消
            try {
                RBlockingDeque<Object> blockingDeque = redissonClient.getBlockingDeque(ShopConstants.REDIS_ORDER_OUTTIME_UNPAY_QUEUE );
                RDelayedQueue<Object> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
                delayedQueue.offer(OrderMsg.builder().orderId(orderSn).build(), ShopConstants.ORDER_OUTTIME_UNPAY, TimeUnit.MINUTES);
                String s = TimeUnit.SECONDS.toSeconds(ShopConstants.ORDER_OUTTIME_UNPAY) + "分钟";
                log.info("添加延时队列成功 ，延迟时间：" + s + "订单编号：" + orderSn);
            } catch (Exception e) {
                log.error("添加延时队列失败：{}",e.getMessage());
            }
        }

        Map<String,Object> map = new HashMap<>();
        map.put("orderId",orderSn);
        return map;
    }

    private PricedGoods priceGoods(Long uid, long shopId, AppOrderParam param) {
        List<String> productIds = param.getProductId();
        List<String> numbers = param.getNumber();
        List<String> specs = param.getSpec();
        if (productIds == null || numbers == null || specs == null || productIds.isEmpty()
                || productIds.size() != numbers.size() || productIds.size() != specs.size()) {
            throw exception(PARAM_ERROR);
        }
        PricedGoods priced = new PricedGoods();
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            throw exception(LOCKED);
        }
        try {
            for (int i = 0; i < productIds.size(); i++) {
                String newSku = StrUtil.replace(specs.get(i), "|", ",");
                int count = Integer.parseInt(numbers.get(i));
                appStoreProductService.checkProductStock(uid, Long.valueOf(productIds.get(i)),
                        count, newSku, shopId);
                StoreProductAttrValueDO storeProductAttrValue = storeProductAttrValueService
                        .getOne(Wrappers.<StoreProductAttrValueDO>lambdaQuery()
                                .eq(StoreProductAttrValueDO::getSku, newSku)
                                .eq(StoreProductAttrValueDO::getProductId, productIds.get(i)));
                if (storeProductAttrValue == null || storeProductAttrValue.getPrice() == null) {
                    throw exception(new ErrorCode(400, "商品没有服务端价格"));
                }
                priced.totalNum += count;
                priced.amount = priced.amount.add(storeProductAttrValue.getPrice().multiply(BigDecimal.valueOf(count)));
            }
            return priced;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[checkProductStock][执行异常]", ex);
            throw exception(new ErrorCode(999999, ex.getMessage()));
        } finally {
            lock.unlock();
        }
    }

    private BigDecimal consumeCoupon(Long uid, long shopId, AppOrderParam param, BigDecimal goods) {
        BigDecimal couponPrice = lookupCoupon(uid, shopId, param, goods);
        if (couponPrice == null) {
            return null;
        }
        CouponUserDO couponUserDO = appCouponUserService.getById(param.getCouponId());
        if (couponUserDO == null) {
            throw exception(COUPON_NOT_CONDITION);
        }
        int used = appCouponUserService.getBaseMapper().update(null, Wrappers.<CouponUserDO>lambdaUpdate()
                .set(CouponUserDO::getStatus, ShopCommonEnum.IS_STATUS_1.getValue())
                .eq(CouponUserDO::getId, couponUserDO.getId())
                .eq(CouponUserDO::getUserId, uid.intValue())
                .eq(CouponUserDO::getShopId, ShopAccess.shopKey(shopId))
                .eq(CouponUserDO::getStatus, ShopCommonEnum.IS_STATUS_0.getValue()));
        if (used == 0) {
            throw exception(FORBIDDEN);
        }
        return couponPrice;
    }

    /**
     * 没有选择优惠券时返回 null。couponId 为 0 是旧结账页的空值，不是一张券。
     */
    private BigDecimal lookupCoupon(Long uid, long shopId, AppOrderParam param, BigDecimal goods) {
        if (!hasCoupon(param.getCouponId())) {
            return null;
        }
        CouponUserDO couponUserDO = appCouponUserService.getById(param.getCouponId());
        if (couponUserDO == null || couponUserDO.getValue() == null) {
            throw exception(COUPON_NOT_CONDITION);
        }
        if (!Integer.valueOf(uid.intValue()).equals(couponUserDO.getUserId())) {
            throw exception(FORBIDDEN);
        }
        ShopAccess.assertCouponBelongs(couponUserDO.getShopId(), shopId);
        if (ShopCommonEnum.IS_STATUS_1.getValue().equals(couponUserDO.getStatus())) {
            throw exception(FORBIDDEN);
        }
        if (couponUserDO.getLeast().compareTo(goods) > 0) {
            throw exception(COUPON_NOT_CONDITION);
        }
        return couponUserDO.getValue();
    }

    private boolean hasCoupon(String couponId) {
        return StrUtil.isNotBlank(couponId) && !"0".equals(couponId.trim());
    }

    private MalatangQuote.Quote quoteFor(long shopId, StoreShopDO shop, AppOrderParam param,
                                          BigDecimal goods, BigDecimal coupon) {
        List<ShopOptionCatalog.Option> options = new ArrayList<>();
        for (ShopOptionItemVO item : shopOptionService.listByShop(shopId)) {
            if (item == null || item.getId() == null) {
                continue;
            }
            options.add(new ShopOptionCatalog.Option(item.getId(), shopId, item.getKind(),
                    item.getName(), item.getPrice()));
        }
        ShopOptionCatalog.Option flavor = null;
        if (param.getFlavorOptionId() != null) {
            if (param.getFlavorOptionId() <= 0) {
                throw exception(PARAM_ERROR);
            }
            flavor = ShopOptionCatalog.require(shopId, param.getFlavorOptionId(), ShopOptionCatalog.FLAVOR, options);
        }
        List<MalatangQuote.AddonPick> addons = new ArrayList<>();
        if (param.getAddons() != null) {
            for (AppAddonPickParam pick : param.getAddons()) {
                if (pick == null || pick.getOptionId() == null || pick.getOptionId() <= 0) {
                    throw exception(PARAM_ERROR);
                }
                addons.add(new MalatangQuote.AddonPick(
                        ShopOptionCatalog.require(shopId, pick.getOptionId(), ShopOptionCatalog.ADDON, options),
                        pick.getNumber()));
            }
        }
        MalatangQuote.Quote quote = MalatangQuote.price(
                new MalatangQuote.ShopFees(shop.getPackingPrice(), shop.getDeliveryPrice()),
                param.getOrderType(), goods, flavor, addons, coupon);
        if (quote.payAmount == null || quote.payAmount.signum() < 0) {
            throw exception(new ErrorCode(400, "报价金额不正确"));
        }
        return quote;
    }

    private void persistOptions(long shopId, String orderSn, MalatangQuote.Quote quote) {
        if (quote.flavorOptionId != null && quote.flavorAmount != null) {
            StoreOrderOptionDO flavor = new StoreOrderOptionDO();
            flavor.setOrderId(orderSn);
            flavor.setShopId(shopId);
            flavor.setOptionId(quote.flavorOptionId);
            flavor.setKind(ShopOptionCatalog.FLAVOR);
            flavor.setName(quote.flavorName);
            flavor.setUnitPrice(quote.flavorAmount);
            flavor.setNumber(1);
            flavor.setAmount(quote.flavorAmount);
            storeOrderOptionMapper.insert(flavor);
        }
        for (MalatangQuote.AddonLine line : quote.addonLines) {
            StoreOrderOptionDO addon = new StoreOrderOptionDO();
            addon.setOrderId(orderSn);
            addon.setShopId(shopId);
            addon.setOptionId(line.optionId);
            addon.setKind(ShopOptionCatalog.ADDON);
            addon.setName(line.name);
            addon.setUnitPrice(line.unitPrice);
            addon.setNumber(line.number);
            addon.setAmount(line.amount);
            storeOrderOptionMapper.insert(addon);
        }
    }

    private void issueQueueNumber(AppStoreOrderQueryVo orderInfo) {
        if (orderInfo.getShopId() == null || orderInfo.getOrderId() == null) {
            return;
        }
        if (orderInfo.getNumberId() != null && orderInfo.getNumberId() > 0) {
            return;
        }
        List<OrderNumberDO> rows = orderNumberMapper.selectByShop(orderInfo.getShopId());
        List<QueueTickets.Ticket> tickets = new ArrayList<>();
        if (rows != null) {
            for (OrderNumberDO row : rows) {
                if (row.getShopId() == null || row.getNumberNo() == null) {
                    continue;
                }
                tickets.add(new QueueTickets.Ticket(row.getShopId(), row.getOrderId(), row.getNumberNo()));
            }
        }
        Integer number = QueueTickets.issue(true, null, orderInfo.getShopId(), tickets);
        if (number == null) {
            return;
        }
        orderNumberMapper.insert(OrderNumberDO.builder()
                .orderId(orderInfo.getOrderId())
                .shopId(orderInfo.getShopId())
                .numberNo(number)
                .build());
        int updated = storeOrderMapper.update(null, Wrappers.<StoreOrderDO>lambdaUpdate()
                .set(StoreOrderDO::getNumberId, number.longValue())
                .eq(StoreOrderDO::getOrderId, orderInfo.getOrderId())
                .eq(StoreOrderDO::getShopId, orderInfo.getShopId())
                .isNull(StoreOrderDO::getNumberId));
        if (updated > 0) {
            orderInfo.setNumberId(number);
        }
    }

    private List<AppOrderOptionVo> loadOrderOptions(String orderId, Long shopId) {
        List<AppOrderOptionVo> result = new ArrayList<>();
        if (orderId == null || shopId == null) {
            return result;
        }
        List<StoreOrderOptionDO> rows = storeOrderOptionMapper.selectByOrderAndShop(orderId, shopId);
        if (rows == null) {
            return result;
        }
        for (StoreOrderOptionDO row : rows) {
            if (row.getShopId() == null || !shopId.equals(row.getShopId())) {
                continue;
            }
            AppOrderOptionVo vo = new AppOrderOptionVo();
            vo.setKind(row.getKind());
            vo.setName(row.getName());
            vo.setUnitPrice(row.getUnitPrice());
            vo.setNumber(row.getNumber());
            vo.setAmount(row.getAmount());
            result.add(vo);
        }
        return result;
    }

    private static final class PricedGoods {
        private BigDecimal amount = BigDecimal.ZERO;
        private int totalNum;
    }

    private StoreOrderDO findByRequestKey(Long uid, long shopId, String requestKey) {
        return storeOrderMapper.selectByRequestKeyAnyState(uid, shopId, requestKey);
    }

    /**
     * 第三方支付
     * @param uid  用户id
     * @param param 订单参数
     * @return
     */
    @Override
    public Map<String, Object> pay(Long uid, AppPayParam param) {
        long shopId = ShopAccess.requirePositiveShopId(param.getShopId());
        AppStoreOrderQueryVo orderInfo = getOrderInfo(param.getUni(), uid);
        if (orderInfo != null) {
            ShopAccess.assertSameShop(orderInfo.getShopId(), shopId);
        }
        UserBillDO userBillDO =  billService.getOne(new LambdaQueryWrapper<UserBillDO>().eq(UserBillDO::getUid,uid)
                .eq(UserBillDO::getExtendField,param.getUni()));

        if (ObjectUtil.isNull(orderInfo) && ObjectUtil.isNull(userBillDO)) {
            throw exception(STORE_ORDER_NOT_EXISTS);
        }
        if(ObjectUtil.isNotNull(orderInfo) && orderInfo.getPaid().equals(OrderInfoEnum.PAY_STATUS_1.getValue())) {
            throw exception(ORDER_PAY_FINISH);
        }
        if(ObjectUtil.isNotNull(userBillDO) && userBillDO.getStatus().equals(OrderInfoEnum.PAY_STATUS_1.getValue())) {
            throw exception(ORDER_PAY_FINISH);
        }

        PayTypeEnum payType = PayTypeEnum.toType(param.getPaytype());
        if (PayTypeEnum.YUE == payType) {
            this.yuePay(param.getUni(), uid);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", "ok");
            return map;
        }
        // 微信、支付宝以及其它未接通渠道不发起支付，也不把订单标成已付。
        throw exception(ORDER_PAY_CHANNEL_CLOSED);
    }

    /**
     * 余额支付
     *
     * @param orderId 订单号
     * @param uid     用户id
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void yuePay(String orderId, Long uid) {
        AppStoreOrderQueryVo orderInfo = getOrderInfo(orderId, uid);
        if (ObjectUtil.isNull(orderInfo)) {
            throw exception(STORE_ORDER_NOT_EXISTS);
        }

        if (OrderInfoEnum.PAY_STATUS_1.getValue().equals(orderInfo.getPaid())) {
            throw exception(ORDER_PAY_FINISH);
        }

        AppUserQueryVo userInfo = userService.getAppUser(uid);

        if (userInfo.getNowMoney().compareTo(orderInfo.getPayPrice()) < 0) {
            throw exception(PAY_YUE_NOT);
        }

        userService.decPrice(uid, orderInfo.getPayPrice());

        //支付成功后处理
        this.paySuccess(orderInfo.getOrderId(), PayTypeEnum.YUE.getValue());
    }


    @Override
    public void paySuccess(String orderId, String payType) {
        paySuccess(orderId, payType, null);
    }

    /**
     * 支付通知入账。同一通知键重放不会再次付款或完成。
     * 微信、支付宝未接通，不会把订单标成已付。
     */
    @Override
    @TenantIgnore
    public void paySuccess(String orderId, String payType, String notifyKey) {
        String key = notifyKey;
        if (key == null || key.trim().isEmpty()) {
            key = PayChannels.localNotifyKey(payType, orderId);
        }
        String resolvedKey = key;
        try {
            PaymentReconciliation.Result result = new TransactionTemplate(transactionManager).execute(status ->
                    reconcilePayment(orderId, payType, resolvedKey));
            if (result != null && !result.settled()) {
                log.info("支付通知未入账 orderId={} payType={} disposition={}", orderId, payType, result.disposition());
                refuseUnsettled(result.disposition());
            }
        } catch (RuntimeException ex) {
            if (OrderRequestGate.isDuplicate(ex)) {
                log.info("支付通知重复，不再入账 orderId={} payType={}", orderId, payType);
                throw exception(ORDER_PAY_FINISH);
            }
            throw ex;
        }
    }

    /**
     * 未接通的渠道只记账并返回，避免回调一直重试。
     * 其它未入账结果要抛出去，让余额扣款跟这次入账一起回滚。
     */
    private void refuseUnsettled(PaymentReconciliation.Disposition disposition) {
        if (disposition == PaymentReconciliation.Disposition.UNCONNECTED) {
            return;
        }
        if (disposition == PaymentReconciliation.Disposition.LATE_HELD) {
            throw exception(ORDER_STATUS_ERROR);
        }
        throw exception(ORDER_PAY_FINISH);
    }

    private PaymentReconciliation.Result reconcilePayment(String orderId, String payType, String notifyKey) {
        return PaymentReconciliation.apply(notifyKey, payType, orderId, new PaymentReconciliation.Orders() {
            @Override
            public PaymentReconciliation.OrderState load(String id) {
                return loadPayable(id);
            }

            @Override
            public boolean markPaidIfUnpaid(String id, String type) {
                return markPaidOnce(id, type);
            }
        }, new PaymentReconciliation.Notices() {
            @Override
            public PaymentReconciliation.Disposition find(String key) {
                PayNoticeDO row = payNoticeMapper.selectOne(Wrappers.<PayNoticeDO>lambdaQuery()
                        .eq(PayNoticeDO::getNotifyKey, key)
                        .last("LIMIT 1"));
                if (row == null || row.getDisposition() == null) {
                    return null;
                }
                return PaymentReconciliation.Disposition.valueOf(row.getDisposition());
            }

            @Override
            public boolean claim(String key, PaymentReconciliation.Disposition disposition) {
                PayNoticeDO row = new PayNoticeDO();
                row.setNotifyKey(key);
                row.setOrderId(orderId);
                row.setPayType(payType);
                row.setDisposition(disposition.name());
                payNoticeMapper.insert(row);
                return true;
            }

            @Override
            public void replace(String key, PaymentReconciliation.Disposition disposition) {
                payNoticeMapper.update(null, Wrappers.<PayNoticeDO>lambdaUpdate()
                        .set(PayNoticeDO::getDisposition, disposition.name())
                        .eq(PayNoticeDO::getNotifyKey, key));
            }
        }, latePaymentHold, () -> completePayment(orderId, payType));
    }

    private PaymentReconciliation.OrderState loadPayable(String orderId) {
        UserBillDO userBillDO = billService.getOne(new LambdaQueryWrapper<UserBillDO>()
                .eq(UserBillDO::getExtendField, orderId)
                .last("LIMIT 1"));
        if (userBillDO != null) {
            boolean paid = ShopCommonEnum.IS_STATUS_1.getValue().equals(userBillDO.getStatus());
            return new PaymentReconciliation.OrderState(!paid, paid, false);
        }
        StoreOrderDO order = storeOrderMapper.selectOne(Wrappers.<StoreOrderDO>lambdaQuery()
                .eq(StoreOrderDO::getOrderId, orderId)
                .last("LIMIT 1"));
        if (order == null) {
            return null;
        }
        boolean paid = OrderInfoEnum.PAY_STATUS_1.getValue().equals(order.getPaid());
        boolean completed = OrderInfoEnum.STATUS_3.getValue().equals(order.getStatus());
        boolean refunding = order.getRefundStatus() != null
                && !OrderInfoEnum.REFUND_STATUS_0.getValue().equals(order.getRefundStatus());
        boolean awaiting = OrderInfoEnum.STATUS_0.getValue().equals(order.getStatus());
        return new PaymentReconciliation.OrderState(!paid && !completed && !refunding && awaiting, paid, completed);
    }

    private boolean markPaidOnce(String orderId, String payType) {
        UserBillDO userBillDO = billService.getOne(new LambdaQueryWrapper<UserBillDO>()
                .eq(UserBillDO::getExtendField, orderId)
                .last("LIMIT 1"));
        if (userBillDO != null) {
            return billService.update(Wrappers.<UserBillDO>lambdaUpdate()
                    .set(UserBillDO::getStatus, ShopCommonEnum.IS_STATUS_1.getValue())
                    .eq(UserBillDO::getId, userBillDO.getId())
                    .eq(UserBillDO::getStatus, ShopCommonEnum.IS_STATUS_0.getValue()));
        }
        return storeOrderMapper.update(null, Wrappers.<StoreOrderDO>lambdaUpdate()
                .set(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_1.getValue())
                .set(StoreOrderDO::getPayType, payType)
                .set(StoreOrderDO::getPayTime, LocalDateTime.now())
                .eq(StoreOrderDO::getOrderId, orderId)
                .eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_0.getValue())) > 0;
    }

    private void completePayment(String orderId, String payType) {
        UserBillDO userBillDO = billService.getOne(new LambdaQueryWrapper<UserBillDO>()
                .eq(UserBillDO::getExtendField, orderId)
                .last("LIMIT 1"));
        if (userBillDO != null) {
            if (BillDetailEnum.TYPE_1.getValue().equals(userBillDO.getType())) {
                userService.incMoney(userBillDO.getUid(), userBillDO.getNumber());
            }
            return;
        }
        AppStoreOrderQueryVo orderInfo = getOrderInfo(orderId, null);
        if (orderInfo == null) {
            return;
        }
        issueQueueNumber(orderInfo);
        userService.incPayCount(orderInfo.getUid());
        storeOrderStatusService.create(orderInfo.getUid(), orderInfo.getId(), OrderLogEnum.PAY_ORDER_SUCCESS.getValue(),
                OrderLogEnum.PAY_ORDER_SUCCESS.getDesc());

        MemberUserDO userInfo = userService.getUser(orderInfo.getUid());
        String payTypeMsg = PayTypeEnum.WEIXIN.getDesc();
        if (PayTypeEnum.YUE.getValue().equals(payType)) {
            payTypeMsg = PayTypeEnum.YUE.getDesc();
        } else if (PayTypeEnum.ALI.getValue().equals(payType)) {
            payTypeMsg = PayTypeEnum.ALI.getDesc();
        } else if (PayTypeEnum.CASH.getValue().equals(payType)) {
            payTypeMsg = PayTypeEnum.CASH.getDesc();
        }
        billService.expend(userInfo.getId(), "购买商品",
                BillDetailEnum.CATEGORY_1.getValue(),
                BillDetailEnum.TYPE_3.getValue(),
                orderInfo.getPayPrice().doubleValue(), userInfo.getNowMoney().doubleValue(),
                payTypeMsg + orderInfo.getPayPrice() + "元购买商品");

        if (!OrderLogEnum.ORDER_TAKE_DESK.getValue().equals(orderInfo.getOrderType()) &&
                userInfo.getLoginType().equals(AppFromEnum.ROUNTINE.getValue())) {
            List<StoreOrderCartInfoDO> storeOrderCartInfoDOList = storeOrderCartInfoService
                    .list(new LambdaQueryWrapper<StoreOrderCartInfoDO>()
                            .eq(StoreOrderCartInfoDO::getOid, orderInfo.getId()));
            List<String> names = storeOrderCartInfoDOList.stream().map(StoreOrderCartInfoDO::getTitle)
                    .collect(Collectors.toList());
            String productName = StrUtil.join(",", names);
            weixinNoticeProducer.sendNoticeMessage(orderInfo.getUid(), WechatTempateEnum.PAY_SUCCESS.getValue(),
                    WechatTempateEnum.SUBSCRIBE.getValue(), orderInfo.getOrderId(),
                    "", "", "", "", orderInfo.getId(), orderInfo.getNumberId(),
                    productName, orderInfo.getShopName());
        }
    }

    /**
     * 减库存增加销量
     *
     * @param productIds 商品id
     * @param numbers 商品数量
     * @param specs 商品规格
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deStockIncSale(List<String> productIds,List<String> numbers,List<String> specs, Long shopId) {


        log.info("========减库存增加销量start=========");
        //对库存加锁
        RLock lock = redissonClient.getLock(STOCK_LOCK_KEY);
        if (lock.tryLock()) {
            try {
                for (int i = 0;i < productIds.size();i++){
                    String newSku = StrUtil.replace(specs.get(i),"|",",");
                    appStoreProductService.decProductStock(Integer.valueOf(numbers.get(i)),
                            Long.valueOf(productIds.get(i)),
                            newSku, 0L, "", shopId);
                }
            }catch (Exception ex) {
                log.error("[deStockIncSale][执行异常]", ex);
                throw exception(new ErrorCode(999999,ex.getMessage()));
            } finally {
                lock.unlock();
            }
        }
    }


    /**
     * 订单列表
     *
     * @param uid   用户id
     * @param type  OrderStatusEnum
     * @param page  page
     * @param limit limit
     * @return list
     */
    @Override
    public List<AppStoreOrderQueryVo> orderList(Long uid, Long shopId, int type, int page, int limit) {
        long scopedShopId = ShopAccess.requirePositiveShopId(shopId);
        LambdaQueryWrapper<StoreOrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StoreOrderDO::getShopId, scopedShopId);
        if (uid != null) {
            wrapper.eq(StoreOrderDO::getUid, uid);
        }
        wrapper.eq(StoreOrderDO::getIsSystemDel,ShopCommonEnum.DELETE_0.getValue()).orderByDesc(StoreOrderDO::getId);
       // wrapper.eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_1.getValue())
                //.eq(StoreOrderDO::getRefundStatus, OrderInfoEnum.REFUND_STATUS_0.getValue())
                //.eq(StoreOrderDO::getStatus, OrderInfoEnum.STATUS_0.getValue())
               // .orderByDesc(StoreOrderDO::getId);
        switch (OrderStatusEnum.toType(type)) {
            case STATUS__1:
                break;
            //未支付
            case STATUS_0:
                wrapper.eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_0.getValue())
                        .eq(StoreOrderDO::getRefundStatus, OrderInfoEnum.REFUND_STATUS_0.getValue())
                        .eq(StoreOrderDO::getStatus, OrderInfoEnum.STATUS_0.getValue());
                break;
            //已经支付
            case STATUS_1:
                wrapper.eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_1.getValue())
                        .eq(StoreOrderDO::getRefundStatus, OrderInfoEnum.REFUND_STATUS_0.getValue())
                        .eq(StoreOrderDO::getStatus, OrderInfoEnum.STATUS_0.getValue());
                break;
            //待收货
            case STATUS_2:
                wrapper.eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_1.getValue())
                        .eq(StoreOrderDO::getRefundStatus, OrderInfoEnum.REFUND_STATUS_0.getValue())
                        .eq(StoreOrderDO::getStatus, OrderInfoEnum.STATUS_1.getValue());
                break;
//            //待评价
//            case STATUS_3:
//                wrapper.eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_1.getValue())
//                        .eq(StoreOrderDO::getRefundStatus, OrderInfoEnum.REFUND_STATUS_0.getValue())
//                        .eq(StoreOrderDO::getStatus, OrderInfoEnum.STATUS_2.getValue());
//                break;
            //已完成
            case STATUS_4:
                wrapper.eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_1.getValue())
                        .eq(StoreOrderDO::getRefundStatus, OrderInfoEnum.REFUND_STATUS_0.getValue())
                        .ge(StoreOrderDO::getStatus, OrderInfoEnum.STATUS_2.getValue());
                break;
            //退款
            case STATUS_MINUS_3:
                String[] strs = {"1", "2"};
                wrapper.in(StoreOrderDO::getRefundStatus, Arrays.asList(strs));
                break;
            default:
        }

        Page<StoreOrderDO> pageModel = new Page<>(page, limit);
        IPage<StoreOrderDO> pageList = storeOrderMapper.selectPage(pageModel, wrapper);
        List<AppStoreOrderQueryVo> list = StoreOrderConvert.INSTANCE.convertList01(pageList.getRecords());

        return list.stream().map(this::handleOrder).collect(Collectors.toList());

    }


    /**
     * 处理订单返回的状态
     *
     * @param order order
     * @return YxStoreOrderQueryVo
     */
    @Override
    public AppStoreOrderQueryVo handleOrder(AppStoreOrderQueryVo order) {
        LambdaQueryWrapper<StoreOrderCartInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StoreOrderCartInfoDO::getOid, order.getId());
        List<StoreOrderCartInfoDO> cartInfos = storeOrderCartInfoService.list(wrapper);

        order.setCartInfo(cartInfos);
        order.setOptions(loadOrderOptions(order.getOrderId(), order.getShopId()));

        StoreShopDO storeShopDO = appStoreShopService.getById(order.getShopId());
        order.setShop(StoreShopConvert.INSTANCE.convert02(storeShopDO));

        long count = storeOrderMapper.selectCount(new LambdaQueryWrapper<StoreOrderDO>()
                .eq(StoreOrderDO::getShopId,order.getShopId())
                .lt(StoreOrderDO::getCreateTime,order.getCreateTime())
                .eq(StoreOrderDO::getPaid, OrderInfoEnum.PAY_STATUS_1.getValue())
                .eq(StoreOrderDO::getRefundStatus, OrderInfoEnum.REFUND_STATUS_0.getValue())
                .eq(StoreOrderDO::getStatus, OrderInfoEnum.STATUS_0.getValue()));
        order.setPreNum(count);


        StatusDto statusDTO = new StatusDto();
        if (OrderStatusEnum.STATUS_0.getValue().equals(order.getPaid())) {
            //计算未支付到自动取消订 时间
            int offset = Integer.valueOf(String.valueOf(ShopConstants.ORDER_OUTTIME_UNPAY));
            //Date time = DateUtil.offsetMinute(order.getCreateTime()., offset);
            statusDTO.setYClass("nobuy");
            //statusDTO.setMsg(StrUtil.format("请在{}前完成支付", DateUtil.formatDateTime(time)));
            statusDTO.setType("0");
            statusDTO.setTitle("未支付");
        } else if (OrderInfoEnum.REFUND_STATUS_1.getValue().equals(order.getRefundStatus())) {
            statusDTO.setYClass("state-sqtk");
            statusDTO.setMsg("商家审核中,请耐心等待");
            statusDTO.setType("-1");
            statusDTO.setTitle("申请退款中");
        } else if (OrderInfoEnum.REFUND_STATUS_2.getValue().equals(order.getRefundStatus())) {
            statusDTO.setYClass("state-sqtk");
            statusDTO.setMsg("已为您退款,感谢您的支持");
            statusDTO.setType("-2");
            statusDTO.setTitle("已退款");
        } else if (OrderInfoEnum.STATUS_0.getValue().equals(order.getStatus())) {
            // 拼团 todo
            if (order.getPinkId() > 0) {
            } else {
                statusDTO.setYClass("state-nfh");
                statusDTO.setMsg("商家未发货,请耐心等待");
                statusDTO.setType("1");
                statusDTO.setTitle("制作中");
            }

        } else if (OrderInfoEnum.STATUS_1.getValue().equals(order.getStatus())) {
            if(OrderLogEnum.ORDER_TAKE_OUT.getValue().equals(order.getOrderType())){
                statusDTO.setTitle("配送中");
            }else{
                statusDTO.setTitle("待取餐");
            }
            statusDTO.setYClass("state-ysh");
            statusDTO.setMsg("服务商已发货");
            statusDTO.setType("2");

        } else if (OrderInfoEnum.STATUS_2.getValue().equals(order.getStatus())) {
            statusDTO.setTitle(receivedTitle(order.getOrderType()));
            statusDTO.setYClass("state-ypj");
            statusDTO.setMsg("已收货,快去评价一下吧");
            statusDTO.setType("3");
        } else if (OrderInfoEnum.STATUS_3.getValue().equals(order.getStatus())) {
            statusDTO.setYClass("state-ytk");
            statusDTO.setMsg("交易完成,感谢您的支持");
            statusDTO.setType("4");
            statusDTO.setTitle("交易完成");
        }

        if (PayTypeEnum.WEIXIN.getValue().equals(order.getPayType())) {
            statusDTO.setPayType("微信支付");
        } else if (PayTypeEnum.YUE.getValue().equals(order.getPayType())) {
            statusDTO.setPayType("余额支付");
        } else if (PayTypeEnum.ALI.getValue().equals(order.getPayType())) {
            statusDTO.setPayType("支付宝支付");
        }else {
            statusDTO.setPayType("积分支付");
        }

        order.setStatusDto(statusDTO);


        return order;
    }


    /**
     * 顾客下单时显式写成未支付、履约默认。不依赖表字段默认值。
     */
    static void initializeFulfillment(StoreOrderDO order) {
        order.setPaid(OrderInfoEnum.PAY_STATUS_0.getValue());
        order.setStatus(OrderInfoEnum.STATUS_0.getValue());
    }

    /**
     * 外卖 status=2 的顾客标题用「已送达」，其它类型仍是「已取餐」。
     */
    static String receivedTitle(String orderType) {
        if (OrderLogEnum.ORDER_TAKE_OUT.getValue().equals(orderType)) {
            return "已送达";
        }
        return "已取餐";
    }

    /**
     * 订单确认收货
     *
     * @param orderId 单号
     * @param uid     uid
     */
    @Override
    public void takeOrder(String orderId, Long uid, Long shopId) {
        AppStoreOrderQueryVo order = requireCustomerOrder(orderId, uid, shopId);
        if (TakeoutDeliveryStatus.legacyWriteBlocked(order.getOrderType())) {
            return;
        }

        if (OrderInfoEnum.PAY_STATUS_0.getValue().equals(order.getPaid())) {
            throw exception(ORDER_STATUS_ERROR);
        }

        if (OrderInfoEnum.STATUS_3.getValue().equals(order.getStatus())){
            throw exception(ORDER_STATUS_FINISH);
        }
        order = handleOrder(order);
//        if (order.getOrderType().equals(OrderLogEnum.ORDER_TAKE_OUT.getValue())
//                && !OrderStatusEnum.STATUS_2.getValue().toString().equals(order.get_status().get_type())) {
//            throw exception(ORDER_STATUS_ERROR);
//        }



        StoreOrderDO storeOrder = new StoreOrderDO();
        storeOrder.setStatus(OrderInfoEnum.STATUS_3.getValue());
        storeOrder.setId(order.getId());
        this.updateById(storeOrder);

        //增加状态
        storeOrderStatusService.create(order.getUid(),order.getId(), OrderLogEnum.TAKE_ORDER_DELIVERY.getValue(), OrderLogEnum.TAKE_ORDER_DELIVERY.getDesc());

        //奖励积分
        this.gainUserIntegral(order);


        //分销计算 todo

    }

    /**
     * 申请退款
     *
     * @param explain 退款备注
     * @param Img     图片
     * @param text    理由
     * @param orderId 订单号
     * @param uid     uid
     */
    @Override
    public void orderApplyRefund(String explain, String Img, String text, String orderId, Long uid, Long shopId) {
        AppStoreOrderQueryVo order = requireCustomerOrder(orderId, uid, shopId);

        if (OrderInfoEnum.REFUND_STATUS_2.getValue().equals(order.getRefundStatus())) {
            throw exception(ORDER_REFUNDED);
        }
        if (OrderInfoEnum.REFUND_STATUS_1.getValue().equals(order.getRefundStatus())) {
            throw exception(ORDER_REFUNDING);
        }


        StoreOrderDO storeOrder = new StoreOrderDO();
        storeOrder.setRefundStatus(OrderInfoEnum.REFUND_STATUS_1.getValue());
        storeOrder.setRefundReasonTime(LocalDateTime.now());
        storeOrder.setRefundReasonWapExplain(explain);
        storeOrder.setRefundReasonWapImg(Img);
        storeOrder.setRefundReasonWap(text);
        storeOrder.setId(order.getId());
        this.updateById(storeOrder);

        //增加状态
        storeOrderStatusService.create(order.getUid(),order.getId(),
                OrderLogEnum.REFUND_ORDER_APPLY.getValue(),
                "用户申请退款，原因：" + text);

        //todo 消息推送

    }



    /**
     * 删除订单
     *
     * @param orderId 单号
     * @param uid     uid
     */
    @Override
    public void removeOrder(String orderId, Long uid, Long shopId) {
        AppStoreOrderQueryVo order = requireCustomerOrder(orderId, uid, shopId);
        order = handleOrder(order);
        if (!OrderInfoEnum.STATUS_3.getValue().equals(order.getStatus())) {
            throw exception(ORDER_NOT_DELETE);
        }

        this.removeById(order.getId());

        //增加状态
        storeOrderStatusService.create(uid,order.getId(),
                OrderLogEnum.REMOVE_ORDER.getValue(),
                OrderLogEnum.REMOVE_ORDER.getDesc());
    }

    /**
     * 未付款取消订单
     *
     * @param orderId 订单号
     * @param uid     用户id
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void cancelOrder(String orderId, Long uid, Long shopId) {
        log.info("订单取消：({})",orderId);
        AppStoreOrderQueryVo order = requireCustomerOrder(orderId, uid, shopId);
        if (order.getPaid() != 0) {
            throw exception(ORDER_NOT_CANCEL);
        }


        this.regressionStock(order);

        this.regressionCoupon(order, 0);

        storeOrderMapper.deleteById(order.getId());
    }


    /**
     * 退回积分
     *
     * @param order 订单
     */
    private void regressionIntegral(AppStoreOrderQueryVo order, Integer type) {
        if (OrderInfoEnum.PAY_STATUS_1.getValue().equals(order.getPaid())
                || OrderStatusEnum.STATUS_MINUS_2.getValue().equals(order.getStatus())) {
            return;
        }

        if (order.getPayIntegral().compareTo(BigDecimal.ZERO) > 0) {
            order.setUseIntegral(order.getPayIntegral());
        }
        if (order.getUseIntegral().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (!OrderStatusEnum.STATUS_MINUS_2.getValue().equals(order.getStatus())
                && !OrderInfoEnum.REFUND_STATUS_2.getValue().equals(order.getRefundStatus())
                && order.getBackIntegral().compareTo(BigDecimal.ZERO) > 0) {
            return;
        }

        BigDecimal newIntegral = shopUserIntegralService.credit(order.getUid(), order.getShopId(), order.getUseIntegral());

        billService.income(order.getUid(), "积分回退", BillDetailEnum.CATEGORY_2.getValue(),
                BillDetailEnum.TYPE_8.getValue(),
                order.getUseIntegral().doubleValue(),
                newIntegral.doubleValue(),
                "购买商品失败,回退积分" + order.getUseIntegral(), order.getId().toString(), order.getShopId());

        //更新回退积分
        StoreOrderDO storeOrder = new StoreOrderDO();
        storeOrder.setBackIntegral(order.getUseIntegral());
        storeOrder.setId(order.getId());
        this.updateById(storeOrder);
    }

    /**
     * 退回优惠券
     *
     * @param order 订单 todo
     */
    private void regressionCoupon(AppStoreOrderQueryVo order, Integer type) {
        if (OrderInfoEnum.PAY_STATUS_1.getValue().equals(order.getPaid())
                || OrderStatusEnum.STATUS_MINUS_2.getValue().equals(order.getStatus())) {
            return;
        }

        if (order.getCouponId() != null && order.getCouponId() > 0) {

            CouponUserDO couponUser = appCouponUserService
                    .getOne(Wrappers.<CouponUserDO>lambdaQuery()
                            .eq(CouponUserDO::getId, order.getCouponId())
                            .eq(CouponUserDO::getStatus, ShopCommonEnum.IS_STATUS_1.getValue())
                            .eq(CouponUserDO::getShopId, ShopAccess.shopKey(order.getShopId()))
                            .eq(CouponUserDO::getUserId, order.getUid()));

            if (ObjectUtil.isNotNull(couponUser)) {
                couponUser.setStatus(ShopCommonEnum.IS_STATUS_0.getValue());
                appCouponUserService.updateById(couponUser);
            }
        }
    }

    /**
     * 退回库存
     *
     * @param order 订单
     */
    private void regressionStock(AppStoreOrderQueryVo order) {
        if (OrderInfoEnum.PAY_STATUS_1.getValue().equals(order.getPaid())
                || OrderStatusEnum.STATUS_MINUS_2.getValue().equals(order.getStatus())) {
            return;
        }

        LambdaQueryWrapper<StoreOrderCartInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StoreOrderCartInfoDO::getOid, order.getId());

        List<StoreOrderCartInfoDO> cartInfoList = storeOrderCartInfoService.list(wrapper);
        for (StoreOrderCartInfoDO cartInfo : cartInfoList) {
            String newSku = StrUtil.replace(cartInfo.getSpec(),"|",",");
            appStoreProductService.incProductStock(cartInfo.getNumber(), cartInfo.getProductId()
                    ,newSku, 0L, null, order.getShopId());
        }
    }
    

    /**
     * 奖励积分
     *
     * @param order 订单
     */
    private void gainUserIntegral(AppStoreOrderQueryVo order) {
        if (order.getGainIntegral().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newIntegral = shopUserIntegralService.credit(order.getUid(), order.getShopId(), order.getGainIntegral());
            billService.income(order.getUid(), "购买商品赠送积分", BillDetailEnum.CATEGORY_2.getValue(),
                    BillDetailEnum.TYPE_9.getValue(),
                    order.getGainIntegral().doubleValue(),
                    newIntegral.doubleValue(),
                    "购买商品赠送" + order.getGainIntegral() + "积分", order.getId().toString(), order.getShopId());
        }
    }

    private AppStoreOrderQueryVo requireCustomerOrder(String orderId, Long uid, Long shopId) {
        long scopedShopId = ShopAccess.requirePositiveShopId(shopId);
        AppStoreOrderQueryVo order = this.getOrderInfo(orderId, uid);
        if (ObjectUtil.isNull(order)) {
            throw exception(STORE_ORDER_NOT_EXISTS);
        }
        ShopAccess.assertSameShop(order.getShopId(), scopedShopId);
        return order;
    }



    /**
     * 计算奖励的积分
     *
     * @param productIds
     * @return double
     */
    private BigDecimal getGainIntegral(List<String> productIds, long shopId) {
        BigDecimal gainIntegral = BigDecimal.ZERO;
        int scopedShopId = ShopAccess.toIntShop(shopId);
        for (int i = 0;i < productIds.size();i++){
            StoreProductDO storeProductDO = appStoreProductService.getOne(Wrappers.<StoreProductDO>lambdaQuery()
                    .eq(StoreProductDO::getId, productIds.get(i))
                    .eq(StoreProductDO::getShopId, scopedShopId));
            if (storeProductDO == null) {
                throw exception(FORBIDDEN);
            }
            if(storeProductDO.getGiveIntegral().intValue() == 0){
                continue;
            }
            gainIntegral = NumberUtil.add(gainIntegral, storeProductDO.getGiveIntegral());
        }
        return gainIntegral;
    }













}
