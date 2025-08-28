package com.seckill.system.service.impl;

import com.seckill.system.dao.ProductDao;
import com.seckill.system.entity.Product;
import com.seckill.system.entity.Result;
import com.seckill.system.exception.BusinessException;
import com.seckill.system.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品服务实现类
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {
    
    @Autowired
    private ProductDao productDao;
    
    @Override
    public Result<Product> getProductById(Long productId) {
        try {
            if (productId == null) {
                return Result.error("商品ID不能为空");
            }
            
            Product product = productDao.selectById(productId);
            if (product == null) {
                return Result.error("商品不存在");
            }
            
            return Result.success(product);
            
        } catch (Exception e) {
            log.error("查询商品失败，productId: {}", productId, e);
            return Result.error("查询商品失败");
        }
    }
    
    @Override
    public Result<List<Product>> getAllProducts() {
        try {
            List<Product> products = productDao.selectAll();
            return Result.success(products);
            
        } catch (Exception e) {
            log.error("查询所有商品失败", e);
            return Result.error("查询商品失败");
        }
    }
    
    @Override
    public Result<List<Product>> getSeckillProducts() {
        try {
            List<Product> products = productDao.selectSeckillProducts();
            return Result.success(products);
            
        } catch (Exception e) {
            log.error("查询秒杀商品失败", e);
            return Result.error("查询秒杀商品失败");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> addProduct(Product product) {
        try {
            if (product == null) {
                return Result.error("商品信息不能为空");
            }
            
            // 参数校验
            if (product.getProductName() == null || product.getProductName().trim().isEmpty()) {
                return Result.error("商品名称不能为空");
            }
            if (product.getPrice() == null || product.getPrice().doubleValue() <= 0) {
                return Result.error("商品价格必须大于0");
            }
            
            // 设置默认值
            product.setCreateTime(LocalDateTime.now());
            product.setUpdateTime(LocalDateTime.now());
            if (product.getStatus() == null) {
                product.setStatus(0); // 默认下架
            }
            
            int result = productDao.insert(product);
            if (result > 0) {
                log.info("添加商品成功，productId: {}", product.getProductId());
                return Result.success("添加商品成功");
            } else {
                return Result.error("添加商品失败");
            }
            
        } catch (Exception e) {
            log.error("添加商品失败", e);
            throw new BusinessException("添加商品失败");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateProduct(Product product) {
        try {
            if (product == null || product.getProductId() == null) {
                return Result.error("商品信息不能为空");
            }
            
            // 检查商品是否存在
            Product existingProduct = productDao.selectById(product.getProductId());
            if (existingProduct == null) {
                return Result.error("商品不存在");
            }
            
            product.setUpdateTime(LocalDateTime.now());
            
            int result = productDao.update(product);
            if (result > 0) {
                log.info("更新商品成功，productId: {}", product.getProductId());
                return Result.success("更新商品成功");
            } else {
                return Result.error("更新商品失败");
            }
            
        } catch (Exception e) {
            log.error("更新商品失败，productId: {}", product.getProductId(), e);
            throw new BusinessException("更新商品失败");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteProduct(Long productId) {
        try {
            if (productId == null) {
                return Result.error("商品ID不能为空");
            }
            
            // 检查商品是否存在
            Product existingProduct = productDao.selectById(productId);
            if (existingProduct == null) {
                return Result.error("商品不存在");
            }
            
            int result = productDao.deleteById(productId);
            if (result > 0) {
                log.info("删除商品成功，productId: {}", productId);
                return Result.success("删除商品成功");
            } else {
                return Result.error("删除商品失败");
            }
            
        } catch (Exception e) {
            log.error("删除商品失败，productId: {}", productId, e);
            throw new BusinessException("删除商品失败");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateProductStatus(Long productId, Integer status) {
        try {
            if (productId == null) {
                return Result.error("商品ID不能为空");
            }
            if (status == null || (status != 0 && status != 1 && status != 2)) {
                return Result.error("商品状态无效");
            }
            
            // 检查商品是否存在
            Product existingProduct = productDao.selectById(productId);
            if (existingProduct == null) {
                return Result.error("商品不存在");
            }
            
            int result = productDao.updateStatus(productId, status);
            if (result > 0) {
                log.info("更新商品状态成功，productId: {}, status: {}", productId, status);
                return Result.success("更新商品状态成功");
            } else {
                return Result.error("更新商品状态失败");
            }
            
        } catch (Exception e) {
            log.error("更新商品状态失败，productId: {}, status: {}", productId, status, e);
            throw new BusinessException("更新商品状态失败");
        }
    }
}
