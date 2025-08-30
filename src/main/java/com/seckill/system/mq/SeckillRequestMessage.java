package com.seckill.system.mq;

import java.io.Serializable;

/**
 * 秒杀请求消息
 *
 * 参数：userId, productId, quantity, requestId
 * 返回：无
 * 异常：无（消息传输）
 */
public class SeckillRequestMessage implements Serializable {
	private Long userId;
	private Long productId;
	private Integer quantity;
	private String requestId;

	public SeckillRequestMessage() {}

	public SeckillRequestMessage(Long userId, Long productId, Integer quantity, String requestId) {
		this.userId = userId;
		this.productId = productId;
		this.quantity = quantity;
		this.requestId = requestId;
	}

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }
	public Long getProductId() { return productId; }
	public void setProductId(Long productId) { this.productId = productId; }
	public Integer getQuantity() { return quantity; }
	public void setQuantity(Integer quantity) { this.quantity = quantity; }
	public String getRequestId() { return requestId; }
	public void setRequestId(String requestId) { this.requestId = requestId; }
}
