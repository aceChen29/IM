package com.ace.im.user.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;

/**
 * Redis配置类
 *
 * 功能说明：
 * - 配置Redis Template
 * - 配置Lua脚本Bean
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate Bean
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 好友申请过期任务扫描Lua脚本
     *
     * 功能：原子性地从Redis ZSET中取出已到期的好友申请ID并删除
     *
     * 参数：
     * - KEYS[1]: ZSET键名（friend-request-expire-zset）
     * - ARGV[1]: 当前时间戳（毫秒），如果<=0则使用Redis TIME命令获取
     * - ARGV[2]: 最大获取条数
     *
     * 返回：已到期的好友申请ID列表
     */
    @Bean
    public DefaultRedisScript<List> scanExpiredFriendRequestsScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/scan-expired-friend-requests.lua"));
        script.setResultType(List.class);
        return script;
    }
}
