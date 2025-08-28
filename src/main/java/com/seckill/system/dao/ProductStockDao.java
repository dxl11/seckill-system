package com.seckill.system.dao;

import com.seckill.system.entity.ProductStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品库存DAO接口
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
@Mapper
public interface ProductStockDao {

    /**
     * 根据商品ID查询库存
     *
     * @param productId 商品ID
     * @return 库存信息
     */
    ProductStock selectByProductId(@Param("productId") Long productId);

    /**
     * 插入库存记录
     *
     * @param stock 库存信息
     * @return 影响行数
     */
    int insert(ProductStock stock);

    /**
     * 更新库存
     *
     * @param stock 库存信息
     * @return 影响行数
     */
    int update(ProductStock stock);

    /**
     * 扣减库存
     *
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 影响行数
     */
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 增加库存
     *
     * @param productId 商品ID
     * @param quantity 增加数量
     * @return 影响行数
     */
    int increaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
