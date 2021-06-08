package com.changgou.seckill.listener;

import com.alibaba.fastjson.JSON;
import com.changgou.seckill.dao.SeckillGoodsMapper;
import com.changgou.seckill.dao.SeckillOrderMapper;
import com.changgou.seckill.pojo.SeckillGoods;
import com.changgou.seckill.pojo.SeckillOrder;
import com.changgou.seckill.pojo.SeckillStatus;
import com.changgou.seckill.service.SeckillOrderService;
import entity.SystemConstants;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Example;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Component
@RabbitListener(queues = "seckillQueue")
public class DelaySeckillMessageListener {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderService seckillOrderService;

    @RabbitHandler
    public void consumer(@Payload String message) {
        System.out.println("消费端接收数据=======" + new Date());
        if (message != null) {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                System.out.println("回滚时间" + simpleDateFormat.format(new Date()));

                //获取用户的排队信息
                SeckillStatus seckillStatus = JSON.parseObject(message, SeckillStatus.class);

                //如果此时Redis没有排队信息,则订单已经处理，如果有，则没有完成支付，关闭订单
                String username = seckillStatus.getUsername();
                Object userQueueStatus = redisTemplate.boundHashOps("UserQueueStatus").get(seckillStatus.getUsername());

                if (userQueueStatus != null) {
                    //关闭订单
                    //关闭微信订单  判断微信关闭订单的状态(1,已支付:调用方法 更新数据到数据库中.2 调用成功:(关闭订单成功:执行删除订单的业务 ) 3.系统错误: 人工处理.   )

                    //5.判断业务状态是否成功  如果 不成功 1.删除预订单 2.恢复库存 3.删除排队标识 4.删除状态信息
                    SeckillOrder seckillOrder = (SeckillOrder) redisTemplate.boundHashOps(SystemConstants.SEC_KILL_ORDER_KEY).get(username);

                    redisTemplate.boundHashOps(SystemConstants.SEC_KILL_ORDER_KEY).delete(username);


                    // 2.恢复库存  压入商品的超卖的问题的队列中
                    redisTemplate.boundListOps(SystemConstants.SEC_KILL_CHAOMAI_LIST_KEY_PREFIX + seckillOrder.getSeckillId()).leftPush(seckillOrder.getSeckillId());


                    //2.恢复库存  获取商品的数据 商品的库存+1

                    SeckillGoods seckillGoods = (SeckillGoods) redisTemplate.boundHashOps(SystemConstants.SEC_KILL_GOODS_PREFIX + seckillStatus.getTime()).get(seckillOrder.getSeckillId());
                    if (seckillGoods == null) {//说明你买的是最后一个商品 在redis中被删除掉了
                        seckillGoods = seckillGoodsMapper.selectByPrimaryKey(seckillOrder.getSeckillId());
                    }


                    Long increment = redisTemplate.boundHashOps(SystemConstants.SECK_KILL_GOODS_COUNT_KEY).increment(seckillOrder.getSeckillId(), 1);

                    seckillGoods.setStockCount(increment.intValue());

                    //3 删除 防止重复排队的标识
                    redisTemplate.boundHashOps(SystemConstants.SEC_KILL_QUEUE_REPEAT_KEY).delete(username);
                    //4 删除 排队标识
                    redisTemplate.boundHashOps(SystemConstants.SEC_KILL_USER_STATUS_KEY).delete(username);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
