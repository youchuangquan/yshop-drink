package co.yixiang.yshop.framework.common.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 麻辣烫报价。打包费、配送费、口味和加料只加门店已经配置的金额。
 * 没配置就留空，不加 0，也不接受调用方传来的口味价。
 */
public final class MalatangQuote {

    public static final String TAKE_IN = "takein";
    public static final String TAKE_OUT = "takeout";

    private MalatangQuote() {
    }

    public static final class ShopFees {
        public final BigDecimal packingPrice;
        public final BigDecimal deliveryPrice;

        public ShopFees(BigDecimal packingPrice, BigDecimal deliveryPrice) {
            this.packingPrice = packingPrice;
            this.deliveryPrice = deliveryPrice;
        }
    }

    public static final class AddonPick {
        public final ShopOptionCatalog.Option option;
        public final Integer number;

        public AddonPick(ShopOptionCatalog.Option option, Integer number) {
            this.option = option;
            this.number = number;
        }
    }

    public static final class AddonLine {
        public final long optionId;
        public final String name;
        public final BigDecimal unitPrice;
        public final int number;
        public final BigDecimal amount;

        public AddonLine(long optionId, String name, BigDecimal unitPrice, int number, BigDecimal amount) {
            this.optionId = optionId;
            this.name = name;
            this.unitPrice = unitPrice;
            this.number = number;
            this.amount = amount;
        }
    }

    public static final class Quote {
        public final BigDecimal goodsAmount;
        public final Long flavorOptionId;
        public final String flavorName;
        public final BigDecimal flavorAmount;
        public final List<AddonLine> addonLines;
        public final BigDecimal addonAmount;
        public final BigDecimal packingAmount;
        public final BigDecimal deliveryAmount;
        public final BigDecimal payAmount;

        public Quote(BigDecimal goodsAmount, Long flavorOptionId, String flavorName, BigDecimal flavorAmount,
                     List<AddonLine> addonLines, BigDecimal addonAmount, BigDecimal packingAmount,
                     BigDecimal deliveryAmount, BigDecimal payAmount) {
            this.goodsAmount = goodsAmount;
            this.flavorOptionId = flavorOptionId;
            this.flavorName = flavorName;
            this.flavorAmount = flavorAmount;
            this.addonLines = addonLines;
            this.addonAmount = addonAmount;
            this.packingAmount = packingAmount;
            this.deliveryAmount = deliveryAmount;
            this.payAmount = payAmount;
        }
    }

    /**
     * 自取和外卖在配置了打包费时收一次。堂食不收。
     * 配送费只在外卖收取，而且必须是门店上的配送价。没配就拒绝。
     */
    public static Quote price(ShopFees fees, String orderType, BigDecimal goods,
                              ShopOptionCatalog.Option flavor, List<AddonPick> addons, BigDecimal coupon) {
        if (goods == null || fees == null) {
            throw exception(BAD_REQUEST);
        }
        if (!TAKE_IN.equals(orderType) && !TAKE_OUT.equals(orderType) && !"desk".equals(orderType)) {
            throw exception(BAD_REQUEST);
        }
        BigDecimal pay = goods;
        Long flavorOptionId = null;
        String flavorName = null;
        BigDecimal flavorAmount = null;
        if (flavor != null) {
            if (flavor.price == null) {
                throw exception(BAD_REQUEST);
            }
            flavorOptionId = flavor.id;
            flavorName = flavor.name;
            flavorAmount = flavor.price;
            pay = pay.add(flavorAmount);
        }
        List<AddonLine> lines = new ArrayList<>();
        BigDecimal addonAmount = null;
        if (addons != null && !addons.isEmpty()) {
            addonAmount = BigDecimal.ZERO;
            for (AddonPick pick : addons) {
                if (pick == null || pick.option == null || pick.option.price == null
                        || pick.option.id == null || pick.number == null || pick.number < 1) {
                    throw exception(BAD_REQUEST);
                }
                BigDecimal amount = pick.option.price.multiply(BigDecimal.valueOf(pick.number.longValue()));
                lines.add(new AddonLine(pick.option.id, pick.option.name, pick.option.price, pick.number, amount));
                addonAmount = addonAmount.add(amount);
            }
            pay = pay.add(addonAmount);
        }
        BigDecimal packingAmount = null;
        if (TAKE_IN.equals(orderType) || TAKE_OUT.equals(orderType)) {
            packingAmount = fees.packingPrice;
            if (packingAmount != null) {
                pay = pay.add(packingAmount);
            }
        }
        BigDecimal deliveryAmount = null;
        if (TAKE_OUT.equals(orderType)) {
            if (fees.deliveryPrice == null) {
                throw exception(BAD_REQUEST);
            }
            deliveryAmount = fees.deliveryPrice;
            pay = pay.add(deliveryAmount);
        }
        if (coupon != null) {
            pay = pay.subtract(coupon);
        }
        return new Quote(goods, flavorOptionId, flavorName, flavorAmount,
                Collections.unmodifiableList(lines), addonAmount, packingAmount, deliveryAmount, pay);
    }

}
