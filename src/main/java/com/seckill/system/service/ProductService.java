package com.seckill.system.service;

import com.seckill.system.entity.Product;
import com.seckill.system.entity.Result;

import java.util.List;

/**
 * 商品服务接口
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
public interface ProductService {
    
    /**
     * 根据ID查询商品
     * 
     * @param productId 商品ID
     * @return 商品信息
     */
    Result<Product> getProductById(Long productId);
    
    /**
     * 查询所有商品
     * 
     * @return 商品列表
     */
    Result<List<Product>> getAllProducts();
    
    /**
     * 查询秒杀中的商品
     * 
     * @return 秒杀商品列表
     */
    Result<List<Product>> getSeckillProducts();
    
    /**
     * 添加商品
     * 
     * @param product 商品信息
     * @return 添加结果
     */
    Result<String> addProduct(Product product);
    
    /**
     * 更新商品
     * 
     * @param product 商品信息
     * @return 更新结果
     */
    Result<String> updateProduct(Product product);
    
    /**
     * 删除商品
     * 
     * @param productId 商品ID
     * @return 删除结果
     */
    Result<String> deleteProduct(Long productId);
    
    /**
     * 更新商品状态
     * 
     * @param productId 商品ID
     * @param status 商品状态
     * @return 更新结果
     */
    Result<String> updateProductStatus(Long productId, Integer status);
}
