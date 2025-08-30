package com.seckill.system.mq;

import com.seckill.system.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 秒杀消息生产者
 *
 * 参数：SeckillRequestMessage 消息体
 * 返回：boolean 是否发送成功
 * 异常：包装并记录消息发送异常
 */
@Component
@Slf4j
public class SeckillMessageProducer {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	/**
	 * 发送秒杀请求消息
	 *
	 * @param message 消息体
	 * @return 是否发送成功
	 */
	public boolean send(SeckillRequestMessage message) {
		try {
			CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
			rabbitTemplate.convertAndSend(
				RabbitMQConfig.EXCHANGE_SECKILL,
				RabbitMQConfig.ROUTING_SECKILL,
				message,
				msg -> {
					msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
					msg.getMessageProperties().setCorrelationId(correlationData.getId());
					msg.getMessageProperties().setMessageId(message.getRequestId());
					return msg;
				},
				correlationData
			);
			log.info("发送秒杀消息成功，requestId={}", message.getRequestId());
			return true;
		} catch (Exception e) {
			log.error("发送秒杀消息失败，requestId={}", message != null ? message.getRequestId() : null, e);
			return false;
		}
	}
}
