package co.yixiang.yshop.module.member.service.usershopintegral;

import co.yixiang.yshop.framework.common.shop.ShopPointLedger;
import co.yixiang.yshop.module.member.dal.dataobject.usershopintegral.ShopUserIntegralDO;
import co.yixiang.yshop.module.member.dal.mysql.usershopintegral.ShopUserIntegralMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ShopUserIntegralServiceImpl implements ShopUserIntegralService {

    @Resource
    private ShopUserIntegralMapper shopUserIntegralMapper;

    @Override
    public BigDecimal balance(Long uid, Long shopId) {
        return ShopPointLedger.balance(store(), uid, shopId);
    }

    @Override
    public BigDecimal credit(Long uid, Long shopId, BigDecimal amount) {
        return ShopPointLedger.credit(store(), uid, shopId, amount);
    }

    @Override
    public boolean tryDebit(Long uid, Long shopId, BigDecimal amount) {
        return ShopPointLedger.tryDebit(store(), uid, shopId, amount);
    }

    private ShopPointLedger.Store store() {
        return new ShopPointLedger.Store() {
            @Override
            public BigDecimal find(long uid, long shopId) {
                return shopUserIntegralMapper.selectIntegral(uid, shopId);
            }

            @Override
            public int add(long uid, long shopId, BigDecimal amount) {
                return shopUserIntegralMapper.add(uid, shopId, amount);
            }

            @Override
            public void insert(long uid, long shopId, BigDecimal amount) {
                ShopUserIntegralDO row = new ShopUserIntegralDO();
                row.setUid(uid);
                row.setShopId(shopId);
                row.setIntegral(amount);
                shopUserIntegralMapper.insert(row);
            }

            @Override
            public int deduct(long uid, long shopId, BigDecimal amount) {
                return shopUserIntegralMapper.deduct(uid, shopId, amount);
            }
        };
    }

}
