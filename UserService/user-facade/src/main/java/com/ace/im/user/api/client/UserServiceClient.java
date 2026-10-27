package com.ace.im.user.api.client;

import com.ace.im.common.common.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 供其他微服务调用 UserService 的 HTTP 客户端。
 * 这里仅声明 UserService 已经提供的接口；具体业务实现仍在 user-service。
 */
@FeignClient(name = "UserService")
public interface UserServiceClient {

    /** 根据用户 ID 重新获取实时服务地址，对应 UserController.refreshUri。 */
    @GetMapping("/api/user/refresh/uri")
    BaseResponse<String> refreshNettyUri(@RequestParam("userId") Long userId);

    @GetMapping("/api/user/get/receivers")
    List<Long> getUserIdBySessionId(@RequestParam("sessionId")  Long sessionId);//按照教程不反悔baseResponse
}
