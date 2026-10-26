package com.ace.im.realtime.utils;


import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;

/**
 * 雪花 ID 生成工具类
 *
 用于处理realtimeSevice多实例时 消息id不重复
 */
public class SnowflakeDynamicUtil {

    /** 工作节点 ID：区分同一数据中心内的不同服务实例，避免实例间生成重复 ID。 */
    @Value("${snowflake.workerId}")
    private static long workerId;

    /** 数据中心 ID：区分不同数据中心；与 workerId 组合后共同标识一个生成节点。 */
    @Value("${snowflake.datacenterId}")
    private static long dataCenterId;

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(workerId, dataCenterId);

    /**
     * 生成雪花 ID
     *
     * @return 雪花 ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }
}
