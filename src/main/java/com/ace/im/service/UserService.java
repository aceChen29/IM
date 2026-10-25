package com.ace.im.service;

import com.ace.im.model.dto.UserLoginCodeRequest;
import com.ace.im.model.dto.UserLoginPasswordRequest;
import com.ace.im.model.dto.UserRegisterRequest;
import com.ace.im.model.entity.User;
import com.ace.im.model.vo.LoginAndRegisterResponse;
import com.ace.im.model.vo.TokenResponse;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.validation.Valid;

/**
* @author cy
* @description 针对表【user】的数据库操作Service
* @createDate 2026-10-24 13:53:29
*/
public interface UserService extends IService<User> {

    void sendCapcha(String targetEmail);

    LoginAndRegisterResponse register(@Valid UserRegisterRequest userRegisterRequest);

    LoginAndRegisterResponse loginPassword(@Valid UserLoginPasswordRequest userLoginPasswordRequest);

    LoginAndRegisterResponse loginCode(@Valid UserLoginCodeRequest userLoginCodeRequest);

    Boolean logout(String subject);

    TokenResponse refreshToken(String refreshToken);
}
