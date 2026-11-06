package com.ace.im.redpacket;

import com.ace.im.common.config.MybatisPlusConfig;
import com.ace.im.common.exception.GlobalExceptionHandler;
import com.ace.im.user.api.client.UserServiceClient;
import com.ace.im.user.api.fallback.UserServiceClientFallbackFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@EnableFeignClients(clients = UserServiceClient.class)
@Import({UserServiceClientFallbackFactory.class, MybatisPlusConfig.class,
        GlobalExceptionHandler.class})
@SpringBootApplication
public class RedPacketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedPacketServiceApplication.class, args);
    }
}
