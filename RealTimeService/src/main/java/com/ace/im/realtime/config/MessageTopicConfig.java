package com.ace.im.realtime.config;

import com.ace.im.common.constant.CommonConstant;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MessageTopicConfig {
    @Value("${im.topic.partitions:6}")
    private int partitions;

    @Bean
    public NewTopic storeTopic() {
        return TopicBuilder.name(CommonConstant.KAFKA_MESSAGE_TOPIC_STORE)
                .partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic pushTopic() {
        return TopicBuilder.name(CommonConstant.KAFKA_MESSAGE_TOPIC_PUSH)
                .partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic storeTopicDlt() {
        return TopicBuilder.name(CommonConstant.KAFKA_MESSAGE_TOPIC_STORE + ".DLT")
                .partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic pushTopicDlt() {
        return TopicBuilder.name(CommonConstant.KAFKA_MESSAGE_TOPIC_PUSH + ".DLT")
                .partitions(partitions).replicas(1).build();
    }
}