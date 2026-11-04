package com.ace.im.offlinedata;

import com.ace.im.common.exception.GlobalExceptionHandler;
import com.ace.im.user.api.client.UserServiceClient;
import com.ace.im.ai.api.client.AiServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableFeignClients(basePackageClasses = {UserServiceClient.class, AiServiceClient.class})
@Import(GlobalExceptionHandler.class)
public class OfflineDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfflineDataServiceApplication.class, args);
    }
}
