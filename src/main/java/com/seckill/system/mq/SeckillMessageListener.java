package com.seckill.system.mq;

import com.rabbitmq.client.Channel;
import com.seckill.system.entity.Result;
import com.seckill.system.service.SeckillService;
import com.seckill.system.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.seckill.system.config.RabbitMQConfig.QUEUE_SECKILL;

/**
 * 秒杀消息消费者（手动ACK）
 *
 * 参数：SeckillRequestMessage
 * 返回：无
 * 异常：捕获并按策略重试或路由至DLQ
 */
@Component
@Slf4j
public class SeckillMessageListener {

	@Autowired
	private SeckillService seckillService;

	@Autowired
	private RedisUtil redisUtil;

	private static final String IDEMPOTENT_KEY_PREFIX = "seckill:msg:processed:";
	private static final String RETRY_KEY_PREFIX = "seckill:msg:retry:";
	private static final int MAX_RETRY = 3;

	@RabbitListener(queues = QUEUE_SECKILL, containerFactory = "manualAckContainerFactory")
	public void onMessage(SeckillRequestMessage body, Message message, Channel channel) throws Exception {
		String requestId = body.getRequestId();
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		try {
			// 幂等：已处理直接ACK
			String processedKey = IDEMPOTENT_KEY_PREFIX + requestId;
			if (redisUtil.hasKey(processedKey)) {
				channel.basicAck(deliveryTag, false);
				return;
			}

			// 业务处理
			Result<String> result = seckillService.doSeckill(body.getUserId(), body.getProductId(), body.getQuantity());
			if (result.getCode() == 200) {
				// 记录幂等标记
				redisUtil.set(processedKey, 1, 24, TimeUnit.HOURS);
				channel.basicAck(deliveryTag, false);
				log.info("消费成功，requestId={}, order={}", requestId, result.getData());
			} else {
				handleRetryOrDlq(requestId, message, channel, deliveryTag, result.getMessage());
			}
		} catch (Exception ex) {
			log.error("消费异常，requestId={}", requestId, ex);
			handleRetryOrDlq(requestId, message, channel, deliveryTag, ex.getMessage());
		}
	}

	private void handleRetryOrDlq(String requestId, Message message, Channel channel, long deliveryTag, String reason) throws Exception {
		String retryKey = RETRY_KEY_PREFIX + requestId;
		int times = 0;
		Object cnt = redisUtil.get(retryKey);
		if (cnt != null) {
			times = Integer.parseInt(cnt.toString());
		}
		times++;
		if (times <= MAX_RETRY) {
			redisUtil.set(retryKey, times, 24, TimeUnit.HOURS);
			// 重回队列（短暂退避）
			channel.basicNack(deliveryTag, false, true);
			log.warn("消息重试，第{}次，requestId={}, reason={}", times, requestId, reason);
		} else {
			// 超过最大重试，投递DLQ
			channel.basicReject(deliveryTag, false);
			log.error("消息进入DLQ，requestId={}, reason={}", requestId, reason);
		}
	}
}
