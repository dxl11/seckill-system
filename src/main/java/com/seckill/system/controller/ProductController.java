package com.seckill.system.controller;

import com.seckill.system.entity.Product;
import com.seckill.system.entity.Result;
import com.seckill.system.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品管理控制器
 * 
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@RestController
@RequestMapping("/api/product")
@Slf4j
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    /**
     * 根据ID查询商品
     * 
     * @param productId 商品ID
     * @return 商品信息
     */
    @GetMapping("/{productId}")
    public Result<Product> getProductById(@PathVariable Long productId) {
        log.info("查询商品，productId: {}", productId);
        return productService.getProductById(productId);
    }
    
    /**
     * 查询所有商品
     * 
     * @return 商品列表
     */
    @GetMapping("/list")
    public Result<List<Product>> getAllProducts() {
        log.info("查询所有商品");
        return productService.getAllProducts();
    }
    
    /**
     * 查询秒杀中的商品
     * 
     * @return 秒杀商品列表
     */
    @GetMapping("/seckill")
    public Result<List<Product>> getSeckillProducts() {
        log.info("查询秒杀商品");
        return productService.getSeckillProducts();
    }
    
    /**
     * 添加商品
     * 
     * @param product 商品信息
     * @return 添加结果
     */
    @PostMapping("/add")
    public Result<String> addProduct(@RequestBody Product product) {
        log.info("添加商品，product: {}", product);
        return productService.addProduct(product);
    }
    
    /**
     * 更新商品
     * 
     * @param product 商品信息
     * @return 更新结果
     */
    @PutMapping("/update")
    public Result<String> updateProduct(@RequestBody Product product) {
        log.info("更新商品，product: {}", product);
        return productService.updateProduct(product);
    }
    
    /**
     * 删除商品
     * 
     * @param productId 商品ID
     * @return 删除结果
     */
    @DeleteMapping("/{productId}")
    public Result<String> deleteProduct(@PathVariable Long productId) {
        log.info("删除商品，productId: {}", productId);
        return productService.deleteProduct(productId);
    }
    
    /**
     * 更新商品状态
     * 
     * @param productId 商品ID
     * @param status 商品状态
     * @return 更新结果
     */
    @PutMapping("/{productId}/status")
    public Result<String> updateProductStatus(@PathVariable Long productId, @RequestParam Integer status) {
        log.info("更新商品状态，productId: {}, status: {}", productId, status);
        return productService.updateProductStatus(productId, status);
    }
}
