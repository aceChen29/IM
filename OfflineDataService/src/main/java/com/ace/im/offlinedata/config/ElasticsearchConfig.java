package com.ace.im.offlinedata.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/** ES 7.12.1 客户端配置，地址读取当前环境的 spring.elasticsearch.uris。 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient(
            @Value("${spring.elasticsearch.uris}") String[] uris) {
        HttpHost[] hosts = Arrays.stream(uris)
                .map(String::trim)
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);
        return new RestHighLevelClient(RestClient.builder(hosts));
    }

    /** 复用高级客户端的连接，避免 Boot 额外创建低级客户端；由高级客户端统一关闭。 */
    @Bean(destroyMethod = "")
    public RestClient restClient(RestHighLevelClient client) {
        return client.getLowLevelClient();
    }
}
