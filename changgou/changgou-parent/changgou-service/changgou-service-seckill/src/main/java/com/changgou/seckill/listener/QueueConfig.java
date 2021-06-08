package com.changgou.seckill.listener;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class QueueConfig {
    @Bean
    public Queue delaySeckillQueue() {
        return QueueBuilder.durable("delaySeckillQueue")
                .withArgument("x-dead-letter-exchange", "seckillExchange") //当前队列的消息一旦过期，进入到死信队列交换机
                .withArgument("x-dead-letter-routing-key", "seckillQueue") //将死信队列的数据路由到指定的队列中
                .build();
    }


    /*真正的监听队列
     * */
    @Bean
    public Queue seckillQueue() {
        return new Queue("seckillQueue");
    }

    /*秒杀交换机
     * */
    @Bean
    public Exchange seckillExchange() {
        return new DirectExchange("seckillExchange");
    }

    /*真正监听的队列绑定秒杀交换机
    * */
    @Bean
    public Binding seckillQueueBindingExchange(Queue seckillQueue,Exchange seckillExchange){
        return BindingBuilder.bind(seckillQueue).to(seckillExchange).with("seckillQueue").noargs();
    }

}
