package co.yixiang.yshop.module.store.service.shopoption;

import co.yixiang.yshop.framework.common.order.ShopOptionCatalog;
import co.yixiang.yshop.framework.common.shop.ShopAccess;
import co.yixiang.yshop.module.store.controller.admin.storeshop.vo.ShopOptionItemVO;
import co.yixiang.yshop.module.store.dal.dataobject.shopoption.ShopOptionDO;
import co.yixiang.yshop.module.store.dal.mysql.shopoption.ShopOptionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ShopOptionServiceImpl implements ShopOptionService {

    @Resource
    private ShopOptionMapper shopOptionMapper;

    @Override
    public List<ShopOptionItemVO> listByShop(Long shopId) {
        long scoped = ShopAccess.requirePositiveShopId(shopId);
        List<ShopOptionDO> rows = shopOptionMapper.selectByShop(scoped);
        List<ShopOptionCatalog.Option> loaded = new ArrayList<>();
        if (rows != null) {
            for (ShopOptionDO row : rows) {
                if (row.getShopId() == null) {
                    continue;
                }
                loaded.add(new ShopOptionCatalog.Option(row.getId(), row.getShopId(), row.getKind(),
                        row.getName(), row.getPrice()));
            }
        }
        List<ShopOptionItemVO> result = new ArrayList<>();
        for (ShopOptionCatalog.Option option : ShopOptionCatalog.ofShop(scoped, loaded)) {
            ShopOptionItemVO item = new ShopOptionItemVO();
            item.setId(option.id);
            item.setKind(option.kind);
            item.setName(option.name);
            item.setPrice(option.price);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replace(Long shopId, List<ShopOptionItemVO> items) {
        long scoped = ShopAccess.requirePositiveShopId(shopId);
        if (items == null) {
            return;
        }
        List<ShopOptionCatalog.Option> incoming = new ArrayList<>();
        for (ShopOptionItemVO item : items) {
            if (item == null) {
                incoming.add(null);
                continue;
            }
            incoming.add(new ShopOptionCatalog.Option(null, scoped, item.getKind(), item.getName(), item.getPrice()));
        }
        ShopOptionCatalog.validate(incoming);
        shopOptionMapper.deleteByShop(scoped);
        for (ShopOptionCatalog.Option option : incoming) {
            ShopOptionDO row = new ShopOptionDO();
            row.setShopId(scoped);
            row.setKind(option.kind);
            row.setName(option.name.trim());
            row.setPrice(option.price);
            shopOptionMapper.insert(row);
        }
    }

}
