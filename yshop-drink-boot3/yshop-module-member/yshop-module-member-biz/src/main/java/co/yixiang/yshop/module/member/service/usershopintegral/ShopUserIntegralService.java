package co.yixiang.yshop.module.member.service.usershopintegral;

import java.math.BigDecimal;

public interface ShopUserIntegralService {

    BigDecimal balance(Long uid, Long shopId);

    BigDecimal credit(Long uid, Long shopId, BigDecimal amount);

    boolean tryDebit(Long uid, Long shopId, BigDecimal amount);

}
