package com.ace.im.realtime;

import com.ace.im.user.api.client.UserServiceClient;
import com.ace.im.ai.api.client.AiServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = {UserServiceClient.class, AiServiceClient.class})
public class RealTimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealTimeServiceApplication.class, args);
    }
}
