package com.ace.im.user.loadbalancer;


import org.springframework.cloud.client.ServiceInstance;

import java.util.HashMap;
import java.util.List;

/**
 * 根据 userId 从候选 RealTimeService 实例中选出一个 ServiceInstance。
 * 一致性哈希先得到被选实例的 URI，再用 URI 找回完整的 ServiceInstance。
 */
public class UrlHashLoadBalancer {


    /** 实例列表不变时，相同 userId 通常返回同一个实例。 */
    public ServiceInstance select(List<ServiceInstance> instances, String userId) {
        ConsistentHash consistentHash = new ConsistentHash(instances);
        HashMap<String, ServiceInstance> map = consistentHash.map;
        return map.get(consistentHash.getServer(userId));
    }
}
