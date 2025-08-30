package com.seckill.system.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置
 *
 * 定义请求交换机、队列、死信交换机与队列，以及消息转换与手动ACK容器工厂。
 */
@Configuration
public class RabbitMQConfig {

	public static final String EXCHANGE_SECKILL = "seckill.exchange";
	public static final String QUEUE_SECKILL = "seckill.request.queue";
	public static final String ROUTING_SECKILL = "seckill.request";

	public static final String DLX_EXCHANGE = "seckill.dlx";
	public static final String DLQ_SECKILL = "seckill.request.dlq";
	public static final String DLQ_ROUTING = "seckill.request.dlq";

	@Bean
	public DirectExchange seckillExchange() {
		return ExchangeBuilder.directExchange(EXCHANGE_SECKILL).durable(true).build();
	}

	@Bean
	public DirectExchange dlxExchange() {
		return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
	}

	@Bean
	public Queue seckillQueue() {
		return QueueBuilder.durable(QUEUE_SECKILL)
			.withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
			.withArgument("x-dead-letter-routing-key", DLQ_ROUTING)
			.build();
	}

	@Bean
	public Queue seckillDlq() {
		return QueueBuilder.durable(DLQ_SECKILL).build();
	}

	@Bean
	public Binding seckillBinding() {
		return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(ROUTING_SECKILL);
	}

	@Bean
	public Binding seckillDlqBinding() {
		return BindingBuilder.bind(seckillDlq()).to(dlxExchange()).with(DLQ_ROUTING);
	}

	@Bean
	public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public SimpleRabbitListenerContainerFactory manualAckContainerFactory(ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(4);
		factory.setMaxConcurrentConsumers(16);
		factory.setMessageConverter(jackson2JsonMessageConverter());
		return factory;
	}
}
