package com.ace.im.user.controller;


import com.ace.im.common.common.BaseResponse;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.common.ResultUtils;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.user.constants.UserConstant;

import com.ace.im.user.model.dto.UpdateAvatarRequest;
import com.ace.im.user.model.dto.UserLoginCodeRequest;
import com.ace.im.user.model.dto.UserLoginPasswordRequest;
import com.ace.im.user.model.dto.UserRegisterRequest;
import com.ace.im.user.model.vo.LoginAndRegisterResponse;
import com.ace.im.user.model.vo.TokenResponse;
import com.ace.im.user.model.vo.UploadUrlResponse;
import com.ace.im.user.service.UserService;
import com.ace.im.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    //@Valid @RequestBody UserRegisterRequest request
    //@RequestBody @Valid UserRegisterRequest request
    //处理时，Spring 会先通过 @RequestBody 把 JSON 转成对象，再根据 @Valid 校验对象字段。源码中两个注解谁写在前面，不改变这个流程。
    //注册
    @PostMapping("/register")
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

    @GetMapping("/logout")
    public BaseResponse<Boolean> logout(HttpServletRequest request) {
        String accessToken = request.getHeader("Access-Token");
        Claims claims = JwtUtil.parse(accessToken);
        if(StringUtils.isBlank(accessToken))
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        return ResultUtils.success(userService.logout(claims.getSubject()));
    }
    @PostMapping("/refresh")
    public BaseResponse<TokenResponse> refreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("Refresh-Token");
        if(StringUtils.isBlank(refreshToken))
            throw new BusinessException(ErrorCode.PARAMS_ERROR);

        return ResultUtils.success(userService.refreshToken(refreshToken));
    }

    //实现断线重连接口 不用重新登陆
    @GetMapping("/refresh/uri")
    public BaseResponse<String> refreshUri(@RequestParam Long userId) {
        return ResultUtils.success(userService.refreshUri(userId));
    }

    /**
     * 为文件（例如头像）申请上传地址。预期返回临时的 MinIO PUT 上传地址和上传后的文件访问地址。
     * 客户端随后直接把文件上传到 MinIO；此接口本身不接收文件内容。
     *
     * @param fileName 要上传的文件名，用于确定 MinIO 中的对象名称
     * @return 上传地址和文件访问地址
     */
    @GetMapping("/uploadUrl")
    public BaseResponse<UploadUrlResponse> getUploadUrl(@RequestParam String fileName) {
        return ResultUtils.success(userService.uploadUrl(fileName));
    }

    /**
     * 文件上传完成后，提交头像地址并更新用户资料中的头像字段。
     * 请求中的 uri 通常是上一步返回的 downloadUrl，userId 指明要更新的用户。
     *
     * @param updateAvatarRequest 包含用户 ID 和新头像地址
     * @return 是否更新成功
     */
    @PostMapping("/update/avatar")
    public BaseResponse<Boolean> updateAvatar(@RequestBody UpdateAvatarRequest updateAvatarRequest)  {
        return ResultUtils.success(userService.updateAvatar(updateAvatarRequest));
    }
}
