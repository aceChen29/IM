package com.ace.im.user.utils;


import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 为当前 UserService 实例生成雪花 ID。多实例部署时，每个实例须使用不同的节点 ID 组合。
 */
@Component
public class SnowflakeDynamicUtil {

    private final Snowflake snowflake;

    public SnowflakeDynamicUtil(@Value("${snowflake.workerId}") long workerId,
                                @Value("${snowflake.datacenterId}") long dataCenterId) {
        this.snowflake = IdUtil.getSnowflake(workerId, dataCenterId);
    }

    /**
     * 生成雪花 ID
     *
     * @return 雪花 ID
     */
    public long nextId() {
        return snowflake.nextId();
    }
}
