package com.ace.im.ai.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true")
public class MilvusConfig {
    // Spring容器关闭时调用客户端的close()，释放连接资源。
    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(
            @Value("${ai.milvus.uri}") String uri, @Value("${ai.milvus.token:}") String token) {
        return new MilvusClientV2(
                // 创建Milvus连接配置的构建器；下面的链式调用逐项设置参数。
                ConnectConfig.builder()
                        // Milvus服务地址，例如http://127.0.0.1:19530；填写宿主机实际映射的端口。
                        .uri(uri)
                        // Milvus认证凭据，通常为用户名:密码；当前未开启鉴权，传空字符串。
                        .token(token)
                        // 建立连接最多等待3000毫秒（3秒）。
                        .connectTimeoutMs(3000)
                        // 单次RPC调用默认最多等待5000毫秒（5秒）；超时不保证服务端未执行。
                        .rpcDeadlineMs(5000)
                        // 生成ConnectConfig配置对象，再交给MilvusClientV2创建客户端。
                        .build());
    }
}