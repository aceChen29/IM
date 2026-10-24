package com.ace.im.controller;

import com.ace.im.common.BaseResponse;
import com.ace.im.common.ResultUtils;
import com.ace.im.constants.UserConstant;
import com.ace.im.model.dto.UserLoginCodeRequest;
import com.ace.im.model.dto.UserLoginPasswordRequest;
import com.ace.im.model.dto.UserRegisterRequest;
import com.ace.im.model.vo.LoginAndRegisterResponse;
import com.ace.im.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/user")
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService userService;
    //发送验证码
    @GetMapping("/sendCaptcha")
    //在controller上校验 校验的是传进来的参数， 在类上写校验，校验的是类的对应属性和validated注解配合
    public BaseResponse<String> sendCaptcha(@NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @RequestParam String targetEmail){
        userService.sendCapcha(targetEmail);
        return ResultUtils.success(UserConstant.SEND_EMAIL_SUCCESS);// 这里调用别人的服务 无论成功失败都先成功
    }

    //注册
    @PostMapping("/register")


    //@Valid @RequestBody UserRegisterRequest request
    //@RequestBody @Valid UserRegisterRequest request
    //处理时，Spring 会先通过 @RequestBody 把 JSON 转成对象，再根据 @Valid 校验对象字段。源码中两个注解谁写在前面，不改变这个流程。
    public BaseResponse<LoginAndRegisterResponse> register(@Valid @RequestBody UserRegisterRequest userRegisterRequest){
        return ResultUtils.success(userService.register(userRegisterRequest));
    }

    @PostMapping("/login/password")
    public BaseResponse<LoginAndRegisterResponse> loginPassword(@Valid @RequestBody UserLoginPasswordRequest userLoginPasswordRequest) {
        return ResultUtils.success(userService.loginPassword(userLoginPasswordRequest));
    }
    @PostMapping("/login/code")
    public BaseResponse<LoginAndRegisterResponse> loginCode(@Valid @RequestBody UserLoginCodeRequest userLoginCodeRequest) {
        return ResultUtils.success(userService.loginCode(userLoginCodeRequest));
    }
}
