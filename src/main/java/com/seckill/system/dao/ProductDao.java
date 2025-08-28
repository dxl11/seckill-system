package com.seckill.system.dao;

import com.seckill.system.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品DAO接口
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Mapper
public interface ProductDao {

    /**
     * 根据ID查询商品
     *
     * @param productId 商品ID
     * @return 商品信息
     */
    Product selectById(@Param("productId") Long productId);

    /**
     * 查询所有商品
     *
     * @return 商品列表
     */
    List<Product> selectAll();

    /**
     * 查询秒杀中的商品
     *
     * @return 秒杀商品列表
     */
    List<Product> selectSeckillProducts();

    /**
     * 插入商品
     *
     * @param product 商品信息
     * @return 影响行数
     */
    int insert(Product product);

    /**
     * 更新商品
     *
     * @param product 商品信息
     * @return 影响行数
     */
    int update(Product product);

    /**
     * 删除商品
     *
     * @param productId 商品ID
     * @return 影响行数
     */
    int deleteById(@Param("productId") Long productId);

    /**
     * 更新商品状态
     *
     * @param productId 商品ID
     * @param status 商品状态
     * @return 影响行数
     */
    int updateStatus(@Param("productId") Long productId, @Param("status") Integer status);
}
