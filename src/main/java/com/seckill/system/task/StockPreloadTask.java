package com.seckill.system.task;

import com.seckill.system.dao.ProductDao;
import com.seckill.system.dao.ProductStockDao;
import com.seckill.system.entity.Product;
import com.seckill.system.entity.ProductStock;
import com.seckill.system.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存预热定时任务
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Component
@Slf4j
public class StockPreloadTask {

    @Autowired
    private ProductDao productDao;

    @Autowired
    private ProductStockDao productStockDao;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 库存缓存前缀
     */
    private static final String STOCK_CACHE_PREFIX = "seckill:stock:";

    /**
     * 商品秒杀状态缓存前缀
     */
    private static final String PRODUCT_SECKILL_PREFIX = "seckill:product:";

    /**
     * 缓存过期时间（小时）
     */
    private static final long CACHE_EXPIRE_HOURS = 24;

    /**
     * 每天凌晨2点执行库存预热
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void preloadStockTask() {
        log.info("开始执行库存预热任务");
        try {
            // 查询所有秒杀中的商品
            List<Product> seckillProducts = productDao.selectSeckillProducts();
            
            for (Product product : seckillProducts) {
                try {
                    preloadProductStock(product);
                } catch (Exception e) {
                    log.error("预热商品库存失败，productId: {}", product.getProductId(), e);
                }
            }
            
            log.info("库存预热任务执行完成，共处理{}个商品", seckillProducts.size());
            
        } catch (Exception e) {
            log.error("库存预热任务执行异常", e);
        }
    }

    /**
     * 每小时执行一次库存同步
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 3600000毫秒
    public void syncStockTask() {
        log.debug("开始执行库存同步任务");
        try {
            // 查询所有秒杀中的商品
            List<Product> seckillProducts = productDao.selectSeckillProducts();
            
            for (Product product : seckillProducts) {
                try {
                    syncProductStock(product);
                } catch (Exception e) {
                    log.error("同步商品库存失败，productId: {}", product.getProductId(), e);
                }
            }
            
            log.debug("库存同步任务执行完成，共处理{}个商品", seckillProducts.size());
            
        } catch (Exception e) {
            log.error("库存同步任务执行异常", e);
        }
    }

    /**
     * 预热单个商品库存
     *
     * @param product 商品信息
     */
    public void preloadProductStock(Product product) {
        Long productId = product.getProductId();
        
        try {
            // 查询商品库存
            ProductStock stock = productStockDao.selectByProductId(productId);
            if (stock == null) {
                log.warn("商品库存信息不存在，productId: {}", productId);
                return;
            }

            // 预热Redis缓存
            String stockKey = STOCK_CACHE_PREFIX + productId;
            redisUtil.set(stockKey, stock.getAvailableStock(), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

            // 设置商品秒杀状态到Redis
            String productSeckillKey = PRODUCT_SECKILL_PREFIX + productId;
            redisUtil.set(productSeckillKey, true, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

            log.info("商品{}库存预热成功，库存数量：{}", productId, stock.getAvailableStock());
            
        } catch (Exception e) {
            log.error("预热商品库存异常，productId: {}", productId, e);
        }
    }

    /**
     * 同步单个商品库存
     *
     * @param product 商品信息
     */
    public void syncProductStock(Product product) {
        Long productId = product.getProductId();
        
        try {
            // 查询商品库存
            ProductStock stock = productStockDao.selectByProductId(productId);
            if (stock == null) {
                log.warn("商品库存信息不存在，productId: {}", productId);
                return;
            }

            // 同步到Redis缓存
            String stockKey = STOCK_CACHE_PREFIX + productId;
            redisUtil.set(stockKey, stock.getAvailableStock(), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

            log.debug("商品{}库存同步成功，库存数量：{}", productId, stock.getAvailableStock());
            
        } catch (Exception e) {
            log.error("同步商品库存异常，productId: {}", productId, e);
        }
    }

    /**
     * 手动预热指定商品库存
     *
     * @param productId 商品ID
     * @return 是否预热成功
     */
    public boolean manualPreloadStock(Long productId) {
        try {
            Product product = productDao.selectById(productId);
            if (product == null) {
                log.warn("商品不存在，productId: {}", productId);
                return false;
            }

            preloadProductStock(product);
            return true;
            
        } catch (Exception e) {
            log.error("手动预热商品库存异常，productId: {}", productId, e);
            return false;
        }
    }

    /**
     * 清理过期缓存
     */
    @Scheduled(cron = "0 30 3 * * ?") // 每天凌晨3点30分执行
    public void cleanExpiredCache() {
        log.info("开始执行缓存清理任务");
        try {
            // 这里可以添加清理过期缓存的逻辑
            // 比如清理过期的秒杀记录、用户行为等
            log.info("缓存清理任务执行完成");
            
        } catch (Exception e) {
            log.error("缓存清理任务执行异常", e);
        }
    }
}
