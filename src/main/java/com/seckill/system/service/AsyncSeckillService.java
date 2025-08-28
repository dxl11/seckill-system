package com.seckill.system.service;

import com.seckill.system.entity.Result;

/**
 * 异步秒杀服务接口
 *
 * @author seckill-system
 * @version 1.0.0
 * @since 2025-08-28
 */
public interface AsyncSeckillService {

    /**
     * 异步提交秒杀请求
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @return 提交结果
     */
    Result<String> submitSeckillRequest(Long userId, Long productId, Integer quantity);

    /**
     * 查询秒杀请求状态
     *
     * @param requestId 请求ID
     * @return 请求状态
     */
    Result<String> getRequestStatus(String requestId);

    /**
     * 批量提交秒杀请求
     *
     * @param requests 秒杀请求列表
     * @return 批量提交结果
     */
    Result<String> batchSubmitSeckillRequest(java.util.List<SeckillRequest> requests);

    /**
     * 获取用户秒杀请求历史
     *
     * @param userId 用户ID
     * @return 请求历史
     */
    Result<java.util.List<SeckillRequest>> getUserRequestHistory(Long userId);

    /**
     * 秒杀请求实体
     */
    class SeckillRequest {
        private Long userId;
        private Long productId;
        private Integer quantity;
        private String requestId;
        private Long timestamp;

        public SeckillRequest() {}

        public SeckillRequest(Long userId, Long productId, Integer quantity) {
            this.userId = userId;
            this.productId = productId;
            this.quantity = quantity;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }
}
