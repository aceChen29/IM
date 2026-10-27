package com.ace.im.user.loadbalancer;



import com.ace.im.common.constant.CommonConstant;
import jakarta.annotation.Resource;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 为用户查找一个 RealTimeService 实例，并拼接实例地址与 WebSocket 路径。
 * 流程：从 Nacos 获取实例列表 -> 按 userId 选一个实例 -> 拼接主机、端口和 WebSocket 路径。
 */
@Service
public class NettyServiceLocator {


    @Resource
    private DiscoveryClient discoveryClient;


    /**
     * 相同 userId 在实例列表不变时通常会选到同一个实例；没有实例时返回 null。
     * 当前返回值形如 host:port/ws/netty，不包含 ws:// 协议前缀。
     */
    public String getServiceInstance(String userId){
        List<ServiceInstance> instances = discoveryClient.getInstances(CommonConstant.DISCOVERY_CLIENT_NAME);
        if (instances.isEmpty()) {
            return null;
        }
        // 把用户 ID 交给负载均衡器，由它从这些实例中选出一个。
        ServiceInstance instance = new UrlHashLoadBalancer().select(instances, userId);

        // getPort() 是 Nacos 登记的服务端口；当前配置为 HTTP 8102，而 Netty WebSocket 监听 9101。
        return instance.getHost() + ":" + instance.getPort() + CommonConstant.NETTY_SERVICE_URI;
    }
}
