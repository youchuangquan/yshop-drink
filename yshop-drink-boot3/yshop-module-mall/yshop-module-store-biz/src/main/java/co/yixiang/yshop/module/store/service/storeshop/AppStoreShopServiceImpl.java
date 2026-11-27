package co.yixiang.yshop.module.store.service.storeshop;

import co.yixiang.yshop.module.store.controller.app.storeshop.vo.AppStoreShopVO;
import co.yixiang.yshop.module.store.dal.dataobject.storeshop.StoreShopDO;
import co.yixiang.yshop.module.store.dal.mysql.storeshop.StoreShopMapper;
import co.yixiang.yshop.module.store.service.shopoption.ShopOptionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 门店管理 Service 实现类
 *
 * @author yshop
 */
@Service
@Validated
public class AppStoreShopServiceImpl extends ServiceImpl<StoreShopMapper, StoreShopDO> implements AppStoreShopService {

    @Resource
    private StoreShopMapper shopMapper;
    @Resource
    private ShopOptionService shopOptionService;


    /**
     * 获取门店
     * @param lon 经度
     * @param lat 纬度
     * @param name 店铺名称
     * @return
     */
    @Override
    public List<AppStoreShopVO> getStoreList(double lon, double lat,String name,Integer shopId) {
        List<AppStoreShopVO> list = shopMapper.getStoreList(lon,lat,name,shopId);
        if (list == null) {
            return null;
        }
        for (AppStoreShopVO shop : list) {
            attachOptions(shop);
        }
        return list;
    }

    public void attachOptions(AppStoreShopVO shop) {
        if (shop == null || shop.getId() == null || shop.getId() <= 0) {
            return;
        }
        shop.setOptions(shopOptionService.listByShop(shop.getId()));
    }
}
