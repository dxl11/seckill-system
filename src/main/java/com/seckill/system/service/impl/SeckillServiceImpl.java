package com.seckill.system.service.impl;

import com.seckill.system.aspect.DistributedRateLimit;
import com.seckill.system.dao.ProductDao;
import com.seckill.system.dao.ProductStockDao;
import com.seckill.system.dao.SeckillOrderDao;
import com.seckill.system.entity.Product;
import com.seckill.system.entity.ProductStock;
import com.seckill.system.entity.Result;
import com.seckill.system.entity.SeckillOrder;
import com.seckill.system.exception.BusinessException;
import com.seckill.system.service.SeckillService;
import com.seckill.system.util.DistributedLockUtil;
import com.seckill.system.util.RedisLuaUtil;
import com.seckill.system.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务实现类
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Service
@Slf4j
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ProductDao productDao;

    @Autowired
    private ProductStockDao productStockDao;

    @Autowired
    private SeckillOrderDao seckillOrderDao;

    @Autowired
    private DistributedLockUtil distributedLockUtil;

    @Autowired
    private RedisLuaUtil redisLuaUtil;

    /**
     * 商品库存缓存前缀
     */
    private static final String STOCK_CACHE_PREFIX = "seckill:stock:";

    /**
     * 用户秒杀记录缓存前缀
     */
    private static final String USER_SECKILL_PREFIX = "seckill:user:";

    /**
     * 商品秒杀状态缓存前缀
     */
    private static final String PRODUCT_SECKILL_PREFIX = "seckill:product:";

    /**
     * 库存锁定缓存前缀
     */
    private static final String STOCK_LOCK_PREFIX = "seckill:lock:";

    @Override
    @DistributedRateLimit(key = "'seckill:product:' + #productId", limit = 100, window = 60, block = false)
    @Transactional(rollbackFor = Exception.class)
    public Result<String> doSeckill(Long userId, Long productId, Integer quantity) {
        try {
            // 1. 参数校验
            if (userId == null || productId == null || quantity == null || quantity <= 0) {
                return Result.error("参数错误");
            }

            // 2. 检查用户是否已经秒杀过该商品
            if (hasUserSeckilled(userId, productId)) {
                return Result.error("您已经参与过该商品的秒杀");
            }

            // 3. 使用分布式锁保护秒杀过程
            String lockKey = STOCK_LOCK_PREFIX + productId;
            return distributedLockUtil.executeWithLock(lockKey, 5, 10, TimeUnit.SECONDS, () -> {
                return executeSeckillLogic(userId, productId, quantity);
            });

        } catch (Exception e) {
            log.error("秒杀失败，userId: {}, productId: {}, quantity: {}", userId, productId, quantity, e);
            throw new BusinessException("秒杀失败，请稍后重试");
        }
    }

    /**
     * 执行秒杀核心逻辑
     */
    private Result<String> executeSeckillLogic(Long userId, Long productId, Integer quantity) {
        // 1. 检查商品是否在秒杀中
        Product product = productDao.selectById(productId);
        if (product == null) {
            return Result.error("商品不存在");
        }
        if (product.getStatus() != 2) {
            return Result.error("商品不在秒杀中");
        }
        if (product.getSeckillStartTime() == null || product.getSeckillEndTime() == null) {
            return Result.error("商品秒杀时间未设置");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(product.getSeckillStartTime()) || now.isAfter(product.getSeckillEndTime())) {
            return Result.error("不在秒杀时间范围内");
        }

        // 2. 使用Lua脚本原子性扣减Redis库存
        String stockKey = STOCK_CACHE_PREFIX + productId;
        Long newStock = redisLuaUtil.decreaseStock(stockKey, quantity);
        if (newStock < 0) {
            return Result.error("库存不足");
        }

        // 3. 扣减数据库库存
        int updateResult = productStockDao.decreaseStock(productId, quantity);
        if (updateResult <= 0) {
            // 数据库库存不足，回滚Redis
            redisLuaUtil.increaseStock(stockKey, quantity);
            return Result.error("库存不足");
        }

        // 4. 创建订单
        SeckillOrder order = createSeckillOrder(userId, product, quantity);
        int insertResult = seckillOrderDao.insert(order);
        if (insertResult <= 0) {
            // 创建订单失败，回滚库存
            redisLuaUtil.increaseStock(stockKey, quantity);
            productStockDao.increaseStock(productId, quantity);
            return Result.error("创建订单失败");
        }

        // 5. 记录用户秒杀记录到Redis
        recordUserSeckill(userId, productId, order.getOrderId());

        log.info("用户{}秒杀商品{}成功，订单号：{}，购买数量：{}", userId, productId, order.getOrderId(), quantity);
        return Result.success("秒杀成功，订单号：" + order.getOrderId());
    }

    /**
     * 检查用户是否已经秒杀过该商品
     */
    private boolean hasUserSeckilled(Long userId, Long productId) {
        String userSeckillKey = USER_SECKILL_PREFIX + userId + ":" + productId;
        
        // 先检查Redis缓存
        if (redisUtil.hasKey(userSeckillKey)) {
            return true;
        }
        
        // Redis中没有，检查数据库
        SeckillOrder order = seckillOrderDao.selectByUserIdAndProductId(userId, productId);
        if (order != null) {
            // 同步到Redis
            recordUserSeckill(userId, productId, order.getOrderId());
            return true;
        }
        
        return false;
    }

    /**
     * 记录用户秒杀记录
     */
    private void recordUserSeckill(Long userId, Long productId, Long orderId) {
        String userSeckillKey = USER_SECKILL_PREFIX + userId + ":" + productId;
        redisUtil.set(userSeckillKey, orderId, 24, TimeUnit.HOURS);
    }

    /**
     * 创建秒杀订单
     */
    private SeckillOrder createSeckillOrder(Long userId, Product product, Integer quantity) {
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setProductId(product.getProductId());
        order.setProductName(product.getProductName());
        order.setSeckillPrice(product.getSeckillPrice());
        order.setQuantity(quantity);
        order.setTotalAmount(product.getSeckillPrice().multiply(new BigDecimal(quantity)));
        order.setStatus(0); // 待支付
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }

    @Override
    public Result<String> getSeckillResult(Long userId, Long productId) {
        try {
            if (userId == null || productId == null) {
                return Result.error("参数错误");
            }

            // 先从Redis查询
            String userSeckillKey = USER_SECKILL_PREFIX + userId + ":" + productId;
            Object orderId = redisUtil.get(userSeckillKey);

            if (orderId != null) {
                return Result.success("秒杀成功，订单号：" + orderId);
            }

            // Redis中没有，查询数据库
            SeckillOrder order = seckillOrderDao.selectByUserIdAndProductId(userId, productId);
            if (order != null) {
                // 同步到Redis
                recordUserSeckill(userId, productId, order.getOrderId());
                return Result.success("秒杀成功，订单号：" + order.getOrderId());
            } else {
                return Result.success("未参与秒杀或秒杀失败");
            }

        } catch (Exception e) {
            log.error("查询秒杀结果失败，userId: {}, productId: {}", userId, productId, e);
            return Result.error("查询失败");
        }
    }

    @Override
    public Result<String> preloadStock(Long productId) {
        try {
            if (productId == null) {
                return Result.error("商品ID不能为空");
            }

            // 从数据库查询商品库存
            ProductStock stock = productStockDao.selectByProductId(productId);
            if (stock == null) {
                return Result.error("商品库存信息不存在");
            }

            // 预热Redis缓存
            String stockKey = STOCK_CACHE_PREFIX + productId;
            redisUtil.set(stockKey, stock.getAvailableStock(), 24, TimeUnit.HOURS);

            // 设置商品秒杀状态到Redis
            String productSeckillKey = PRODUCT_SECKILL_PREFIX + productId;
            redisUtil.set(productSeckillKey, true, 24, TimeUnit.HOURS);

            log.info("商品{}库存预热成功，库存数量：{}", productId, stock.getAvailableStock());
            return Result.success("库存预热成功");

        } catch (Exception e) {
            log.error("库存预热失败，productId: {}", productId, e);
            return Result.error("库存预热失败");
        }
    }

    @Override
    public Result<Integer> getStock(Long productId) {
        try {
            if (productId == null) {
                return Result.error("商品ID不能为空");
            }

            // 先从Redis查询
            String stockKey = STOCK_CACHE_PREFIX + productId;
            Object stock = redisUtil.get(stockKey);

            if (stock != null) {
                return Result.success((Integer) stock);
            }

            // Redis中没有，从数据库查询
            ProductStock productStock = productStockDao.selectByProductId(productId);
            if (productStock != null) {
                // 同步到Redis
                redisUtil.set(stockKey, productStock.getAvailableStock(), 24, TimeUnit.HOURS);
                return Result.success(productStock.getAvailableStock());
            }

            return Result.success(0);

        } catch (Exception e) {
            log.error("查询库存失败，productId: {}", productId, e);
            return Result.error("查询库存失败");
        }
    }
}
