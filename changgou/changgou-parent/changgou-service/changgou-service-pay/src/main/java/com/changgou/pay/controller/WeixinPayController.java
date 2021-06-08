package com.changgou.pay.controller;

import com.alibaba.fastjson.JSON;

import com.jayway.jsonpath.internal.Utils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sun.font.GlyphLayout;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
模拟微信支付成功，向MQ队列中发送成功的信号
 */
@RestController
@RequestMapping("/weixin/pay")
public class WeixinPayController implements Serializable {


    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private Environment env;

    /*秒杀的支付信号
    * */
    @RequestMapping("/seckilltest")
    public String test() {
        //动态的从attach参数中获取数据
        Map<String, String> attach = new HashMap<>();
        attach.put("return_code", "SUCCESS");
        attach.put("return_msg", "OK");
        attach.put("fukuan", "SUCCESS");
        attach.put("username", "zyn968");
        attach.put("queue", "queue.seckillorder");//队列名称
        attach.put("routingkey", "queue.seckillorder");//路由key
        attach.put("exchange", "exchange.seckillorder");
        String str = JSON.toJSONString(attach);
        // {routingkey=queue.seckillorder, exchange=exchange.seckillorder, queue=queue.seckillorder, username=szitheima}
        rabbitTemplate.convertAndSend(attach.get("exchange"), attach.get("routingkey"), str);

        return null;
    }

    /*普通订单的支付信号
    * */
    @RequestMapping("/ordertest")
    public String ordertest() {
        //动态的从attach参数中获取数据
        Map<String, String> attach = new HashMap<>();
        attach.put("return_code", "SUCCESS");
        attach.put("return_msg", "OK");
        attach.put("out_trade_no", "1163030316416237512");//订单编号，这里写死了
        attach.put( "transaction_id", "4200000345201908186864005884");//事务编号，这里写死了
        attach.put("username", "szitheima");
        attach.put("queue", "queue.order");//队列名称
        attach.put("routingkey", "queue.order");//路由key
        attach.put("exchange", "exchange.order");
        String str = JSON.toJSONString(attach);
        // {routingkey=queue.seckillorder, exchange=exchange.seckillorder, queue=queue.seckillorder, username=szitheima}
        rabbitTemplate.convertAndSend(attach.get("exchange"), attach.get("routingkey"), str);

        return null;
    }

}
