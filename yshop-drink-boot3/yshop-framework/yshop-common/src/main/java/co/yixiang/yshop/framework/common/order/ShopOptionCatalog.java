package co.yixiang.yshop.framework.common.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 门店口味和加料。价格只认这家店已经保存的配置。
 * 没有价格就拒绝，不补 0，也不拿别的店的价格来用。
 */
public final class ShopOptionCatalog {

    public static final String FLAVOR = "FLAVOR";
    public static final String ADDON = "ADDON";

    private ShopOptionCatalog() {
    }

    public static final class Option {
        public final Long id;
        public final long shopId;
        public final String kind;
        public final String name;
        public final BigDecimal price;

        public Option(Long id, long shopId, String kind, String name, BigDecimal price) {
            this.id = id;
            this.shopId = shopId;
            this.kind = kind;
            this.name = name;
            this.price = price;
        }
    }

    public static void validate(List<Option> options) {
        if (options == null) {
            return;
        }
        List<String> seen = new ArrayList<>();
        for (Option option : options) {
            if (option == null || option.name == null || option.name.trim().isEmpty()) {
                throw exception(BAD_REQUEST);
            }
            if (!FLAVOR.equals(option.kind) && !ADDON.equals(option.kind)) {
                throw exception(BAD_REQUEST);
            }
            if (option.price == null || option.price.signum() < 0) {
                throw exception(BAD_REQUEST);
            }
            String key = option.kind + "\n" + option.name.trim();
            if (seen.contains(key)) {
                throw exception(BAD_REQUEST);
            }
            seen.add(key);
        }
    }

    public static List<Option> ofShop(long shopId, List<Option> options) {
        List<Option> rows = new ArrayList<>();
        if (options == null) {
            return rows;
        }
        for (Option option : options) {
            if (option != null && option.shopId == shopId && option.price != null) {
                rows.add(option);
            }
        }
        return rows;
    }

    public static Option require(long shopId, long optionId, String kind, List<Option> options) {
        Option found = null;
        if (options != null) {
            for (Option option : options) {
                if (option != null && option.id != null && option.id == optionId) {
                    found = option;
                    break;
                }
            }
        }
        if (found == null) {
            throw exception(BAD_REQUEST);
        }
        if (found.shopId != shopId) {
            throw exception(FORBIDDEN);
        }
        if (kind == null || !kind.equals(found.kind) || found.price == null) {
            throw exception(BAD_REQUEST);
        }
        return found;
    }

}
