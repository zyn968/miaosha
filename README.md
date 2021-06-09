# miaosha
秒杀功能：
![image](https://github.com/zyn968/miaosha/blob/master/pic/miaosha.png)
秒杀技术实现核心思想是运用缓存减少数据库瞬间的访问压力！读取商品详细信息时运用缓存，当用户点击抢购时减少缓存中的库存数量，当库存数为0时或活动期结束时，同步到数据库。 产生的秒杀预订单也不会立刻写到数据库中，而是先写到缓存，当用户付款成功后再写入数据库。

1.秒杀商品压入缓存(商品信息预热)
![image](https://github.com/zyn968/miaosha/blob/master/pic/yaru.png)
我们这里秒杀商品列表和秒杀商品详情都是从Redis中取出来的，所以我们首先要将符合参与秒杀的商品定时查询出来，并将数据存入到Redis缓存中。

数据存储类型我们可以选择Hash类型
2.多线程抢单
![image](https://github.com/zyn968/miaosha/blob/master/pic/qiangdan.png)
采用多线程下单，如果用户符合抢单资格，只需要记录用户抢单数据，存入队列，多线程从队列中进行消费即可，存入队列采用左压，多线程下单采用右取的方式。
![image](https://github.com/zyn968/miaosha/blob/master/pic/qiangdan2.png)
用户每次下单的时候，都让他们先进行排队，然后采用多线程的方式创建订单，排队我们可以采用Redis的队列实现，多线程下单我们可以采用Spring的异步实现。

3.防止秒杀重复排队
用户每次抢单的时候，一旦排队，我们设置一个自增值，让该值的初始值为1，每次进入抢单的时候，对它进行递增，如果值>1，则表明已经排队,不允许重复排队,如果重复排队，则对外抛出异常，并抛出异常信息100表示已经正在排队。

4.并发超卖问题的解决
超卖问题，这里是指多人抢购同一商品的时候，多人同时判断是否有库存，如果只剩一个，则都会判断有库存，此时会导致超卖现象产生，也就是一个商品下了多个订单的现象。
![image](https://github.com/zyn968/miaosha/blob/master/pic/chaomai.png)
针对单机的情况，加锁变成同步块可以解决该问题。但是实际情况是集群服务器，一个tomcat并不能对另外的tomcat产生影响，并不能解决服务器集群情况下的超卖问题。
思路：能够实现精准判断商品是否还有，就能够解决该问题。
对数据库添加行级锁？的问题：
可以解决超卖问题，但是每次都要访问mysql数据库，在大并发的情况下对数据库的压力过大。在高并发下，会有很多这样的修改(update)，每个请求都需要等待"锁"，某些请求可能永远都获取不到锁，这种请求就会卡在那里，直到超时。同时，由于这种写请求很多，会造成大量的请求超时，连锁反应就是应用系统连接数被耗光，直至系统异常crash。即使重启系统，由于请求量大，系统也会立马挂掉。
解决方法：
在redis中开一个list队列，将每商品一个独立队列，个数个商品id（什么都行）放进队列。如果下单，如果能取到，证明可以下单；如果取不到，不能下单，清空排队的队列（此举是为了防止用户没抢到，仍在排队的队列，以后都没法进行秒杀了。可能存在并发问题，清空队列后有请求刚刚好排进队伍，可以对队伍设置超时35m，订单最多30分钟）。
同时由于redis内部是单线程的，也避免了多个线程同时抢到一个。

5.数据库同步精准问题
方案1：在对MySQL数据库进行同步之前，先在redis中判断商品数量队列中的数量，小于等于0的时候再对商品剩余数量进行同步，同时再对数据库进行同步。
方案2：用redis进行自增（-1），当redis商品队列的数量为0时，在同步到MySQL中。
PS:自增和队列两种方法，推荐使用自增方法，该方法在redis中存放的数据较小，对redis的压力较小。

支付状态通过回调地址发送给MQ之后，我们需要在秒杀系统中监听支付信息，如果用户已支付，则修改用户订单状态，如果支付失败，则直接删除订单，回滚库存。
普通订单微服务监听队列Queue1，秒杀微服务监听队列Queue2，这样就能实现秒杀与普通支付不冲突。

6.延时队列解决超时回滚问题
TTL
RabbitMQ可以针对队列设置x-expires(则队列中所有的消息都有相同的过期时间)或者针对Message设置x-message-ttl(对消息进行单独设置，每条消息TTL可以不同)，来控制消息的生存时间，如果超时(两者同时设置以最先到期的时间为准)，则消息变为dead letter(死信)。
Dead Letter Exchanges（DLX）
RabbitMQ的Queue可以配置x-dead-letter-exchange和x-dead-letter-routing-key（可选）两个参数，如果队列内出现了dead letter，则按照这两个参数重新路由转发到指定的队列。
x-dead-letter-exchange：出现dead letter之后将dead letter重新发送到指定exchange
x-dead-letter-routing-key：出现dead letter之后将dead letter重新按照指定的routing-key发送。
![image](https://github.com/zyn968/miaosha/blob/master/pic/yanshi.png)
延时队列实现订单关闭回滚库存：
1.创建一个过期队列  Queue1
2.接收消息的队列    Queue2
3.中转交换机
4.监听Queue2
	1)SeckillStatus->检查Redis中是否有订单信息
	2)如果有订单信息，调用删除订单回滚库存
	3)如果关闭订单时，用于已支付，修改订单状态即可
	4)如果关闭订单时，发生了别的错误，记录日志，人工处理

缓存击穿的解决方案
1.当缓存中没有命中的时候，从数据库中获取
2.当数据库中也没有数据的时候，我们直接将null 作为值设置redis中的key上边。
3.此时如果没有数据，一般情况下都需要设置一个过期时间，例如：5分钟失效。（为了避免过多的KEY 存储在redis中）
4.返回给用户，
5.用户再次访问时，已经有KEY了。此时KEY的值是null而已，这样就可以在缓存中命中，解决了缓存穿透的问题。
![image](https://github.com/zyn968/miaosha/blob/master/pic/jichuan.png)
注意：缓存空对象会有两个问题：
第一，空值做了缓存，意味着缓存层中存了更多的键，需要更多的内存空间 ( 如果是攻击，问题更严重 )，比较有效的方法是针对这类数据设置一个较短的过期时间，让其自动剔除。
第二，缓存层和存储层的数据会有一段时间窗口的不一致，可能会对业务有一定影响。例如过期时间设置为 5分钟，如果此时存储层添加了这个数据，那此段时间就会出现缓存层和存储层数据的不一致，此时可以利用消息系统或者其他方式清除掉缓存层中的空对象。

Redis缓存雪崩问题解决
如果缓存集中在一段时间内失效，发生大量的缓存穿透，所有的查询都落在数据库上，造成了缓存雪崩。
这个没有完美解决办法，但可以分析用户行为，尽量让失效时间点均匀分布。
限流 加锁排队
在缓存失效后，通过对某一个key加锁或者是队列 来控制key的线程访问的数量。例如：某一个key 只允许一个线程进行 操作。
限流
在缓存失效后，某一个key 做count统计限流，达到一定的阈值，直接丢弃，不再查询数据库。例如：令牌桶算法。等等。
数据预热
在缓存失效应当尽量避免某一段时间，可以先进行数据预热，比如某些热门的商品。提前在上线之前，或者开放给用户使用之前，先进行loading 缓存中，这样用户使用的时候，直接从缓存中获取。要注意的是，要更加业务来进行过期时间的设置 ，尽量均匀。
做缓存降级（二级缓存策略）
![image](https://github.com/zyn968/miaosha/blob/master/pic/huancun.png)
当分布式缓存失效的时候，可以采用本地缓存，本地缓存没有再查询数据库。这种方式，可以避免很多数据分布式缓存没有，就直接打到数据库的情况。
基本的思路：通过redis缓存+mybatis的二级缓存整合ehcache来实现。EhCache 是一个纯Java的进程内缓存框架，具有快速、精干等特点，是Hibernate中默认的CacheProvider。

gateway网关
网关作用：
1.整合微服务，形成一套系统
2.实现日志统一记录
3.实现用户操作跟踪
4.限流操作
5.用户权限认证
客户端请求会先交给网关，再由网关进行路由断言，实现对微服务的访问。

不同的微服务一般有不同的网络地址，外部客户端往往需要调用多个接口，如果直接与各微服务通信，会有：
存在跨域问题；认证复杂，每个服务需要独立认证；多次请求不同微服务，增加复杂性；难以重构，难以重新划分微服务；某些微服务有防火墙，直接访问有困难；端口暴露太多，增加了服务器受攻击面。
LoadBalancerClient 路由过滤器(客户端负载均衡)
可以使用LoadBalancerClientFilter来实现负载均衡调用。LoadBalancerClientFilter会作用在url以lb开头的路由。
默认是轮询

gateway的限流:
gateway有限流作用，但还是需要nginxj：
一般用nginx实现第一轮的并发流量抵御，再由网关对各个微服务的并发量进行抵御。
nginx限制总的流量，放的流量还是比较大，网关保护微服务，防止雪崩。
![image](https://github.com/zyn968/miaosha/blob/master/pic/wangguan.png)
限流算法： 令牌桶算法
1.所有请求在处理之前要拿到一个可用令牌
2.根据限流大小，以一定速率往桶里加令牌
3.桶超过最大令牌限制，新加的令牌就被丢弃或拒绝
4.拿到令牌后进行业务，处理完后，令牌删除
5.令牌有最低限额，当达到，请求处理完不会删除，
/***************************************************************************************************/

整体基于itheima畅购商城项目进行修改:
1.对原项目的微信支付微服务进行了精简，删除了微信二维码生成和支付的过程，可以通过支付微服务的Controller直接模拟微信支付的成功和失败，进而模拟秒杀项目。
2.在原有的代码基础上完成了订单超时回滚的功能，默认回滚时间为60s,可根据实际需要进行修改。
3.秒杀服务监听启动会报错，将原代码的抛出改为try catch
注意：
1.在秒杀微服务中是定时从MySQL数据库中读取秒杀商品信息到redis中去的，但是原代码设定是读取使用者当前电脑的时间，而数据库中的起止时间为2019年。
解决方法：可以在数据库中修改时间或者修改原代码的时间生成代码。本项目直接在数据库中对秒杀商品的起止时间进行了修改（改为运行代码此刻对应的时间区间）。
2.有很多人使用navicat连接数据库会失败。注意自己VMware的网关设置和子网掩码设置。提供的虚拟机默认ip为192.168.211.132，
可以将NAT模式下对应的网关进行设置，具体操作为：编辑->虚拟网络编辑器->选择NAT模式->子网ip:192.168.211.0 子网掩码：255.255.255.0
