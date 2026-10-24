package com.ace.im.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.ace.im.common.ErrorCode;
import com.ace.im.constants.UserConstant;
import com.ace.im.exception.BusinessException;
import com.ace.im.model.dto.UserLoginCodeRequest;
import com.ace.im.model.dto.UserLoginPasswordRequest;
import com.ace.im.model.dto.UserRegisterRequest;
import com.ace.im.model.vo.LoginAndRegisterResponse;
import com.ace.im.utils.EmailUtil;
import com.ace.im.utils.RandomCodeUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ace.im.model.entity.User;
import com.ace.im.service.UserService;
import com.ace.im.mapper.UserMapper;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
* @author cy
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-10-24 13:53:29
*/
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{
    private final EmailUtil emailUtil;
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public void sendCapcha(String targetEmail) {
        // 检查是否在5分钟内已发送验证码
        String redisCode = stringRedisTemplate.opsForValue().get(targetEmail);
        if(StringUtils.isNotBlank(redisCode))
            throw new BusinessException(ErrorCode.LOGIN_SEND_CODE_ERROR);
        String randomCode = RandomCodeUtil.getRandomCode();
        emailUtil.sendEmail(targetEmail, randomCode);
        stringRedisTemplate.opsForValue().set(targetEmail, randomCode, UserConstant.CAPTCHA_EXPIRE_TIME, TimeUnit.MINUTES);

    }

    @Override
    public LoginAndRegisterResponse register(UserRegisterRequest userRegisterRequest) {
        String email = userRegisterRequest.getEmail();
        String password = userRegisterRequest.getPassword();
        String randomCode = userRegisterRequest.getCode();
        String redisCode = stringRedisTemplate.opsForValue().get(email);
        //校验验证码
        if(StringUtils.isBlank(redisCode) || !redisCode.equals(randomCode))
            throw new BusinessException(ErrorCode.LOGIN_ERROR_CODE);
        //校验密码
        if(!password.equals(userRegisterRequest.getConfirmPassword()))
            throw new BusinessException(ErrorCode.LoginPasswordError);
        //校验是否注册过
        if(lambdaQuery().eq(User::getEmail, email).exists())
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);

        User user = new User();
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(password));
        user.setNickname(userRegisterRequest.getNickname());
        try{
            if(!save(user))
                throw new BusinessException(ErrorCode.REGISTER_ERROR);
        }catch (DuplicateKeyException e) {
            // 处理“检查不存在”之后，另一请求抢先注册的情况
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        LoginAndRegisterResponse loginAndRegisterResponse = new LoginAndRegisterResponse();
        BeanUtil.copyProperties(user, loginAndRegisterResponse);
        stringRedisTemplate.delete(email);
        return loginAndRegisterResponse;
    }

    @Override
    public LoginAndRegisterResponse loginPassword(UserLoginPasswordRequest userLoginPasswordRequest) {
        String email = userLoginPasswordRequest.getEmail();
        String password = userLoginPasswordRequest.getPassword();
        User user = lambdaQuery().eq(User::getEmail, email).one();
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_ERROR);
        }
        LoginAndRegisterResponse response = new LoginAndRegisterResponse();
        BeanUtil.copyProperties(user, response);
        return response;
    }

    @Override
    public LoginAndRegisterResponse loginCode(UserLoginCodeRequest userLoginCodeRequest) {
        String email = userLoginCodeRequest.getEmail();
        User user = lambdaQuery().eq(User::getEmail, email).one();
        if(user == null)
            throw new BusinessException(ErrorCode.USER_NOT_EXISTS);
        String randomCode = userLoginCodeRequest.getCode();
        String redisCode = stringRedisTemplate.opsForValue().get(email);

        if(StringUtils.isBlank(redisCode) || !randomCode.equals(redisCode))
            throw new BusinessException(ErrorCode.LOGIN_ERROR_CODE);
        stringRedisTemplate.delete(email);
        LoginAndRegisterResponse loginAndRegisterResponse = new LoginAndRegisterResponse();
        BeanUtil.copyProperties(user, loginAndRegisterResponse);
        return loginAndRegisterResponse;
    }
}




