package com.ace.im.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.BCrypt;

import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.user.constants.UserConstant;

import com.ace.im.user.loadbalancer.NettyServiceLocator;
import com.ace.im.user.model.dto.UpdateAvatarRequest;
import com.ace.im.user.model.dto.UserLoginCodeRequest;
import com.ace.im.user.model.dto.UserLoginPasswordRequest;
import com.ace.im.user.model.dto.UserRegisterRequest;
import com.ace.im.user.model.vo.LoginAndRegisterResponse;
import com.ace.im.user.model.vo.TokenResponse;
import com.ace.im.user.model.vo.UploadUrlResponse;
import com.ace.im.user.utils.EmailUtil;
import com.ace.im.common.utils.JwtUtil;
import com.ace.im.user.utils.OssUtils;
import com.ace.im.user.utils.RandomCodeUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ace.im.user.model.entity.User;
import com.ace.im.user.service.UserService;
import com.ace.im.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author cy
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-10-24 13:53:29
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{
    private final EmailUtil emailUtil;
    private final StringRedisTemplate stringRedisTemplate;

    private  final NettyServiceLocator serviceInstanceUtil;

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
        LoginAndRegisterResponse loginAndRegisterResponse = getLoginAndRegisterResponse(user);

        return loginAndRegisterResponse;
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
        LoginAndRegisterResponse loginAndRegisterResponse = getLoginAndRegisterResponse(user);
        return loginAndRegisterResponse;
    }

    private @NonNull LoginAndRegisterResponse getLoginAndRegisterResponse(User user) {
        String userId = user.getUserId().toString();
        String accessToken = JwtUtil.generate(userId, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        String refreshToken = JwtUtil.generate(userId, CommonConstant.REFRESH_TOKEN_EXPIRE_TIME, CommonConstant.REFRESH_TOKEN_UNIT);
        stringRedisTemplate.opsForValue().set(CommonConstant.ACCESS_TOKEN_PREFIX + userId, accessToken, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        stringRedisTemplate.opsForValue().set(CommonConstant.REFRESH_TOKEN_PREFIX + userId, refreshToken, CommonConstant.REFRESH_TOKEN_EXPIRE_TIME, CommonConstant.REFRESH_TOKEN_UNIT);

        LoginAndRegisterResponse loginAndRegisterResponse = new LoginAndRegisterResponse();
        BeanUtil.copyProperties(user, loginAndRegisterResponse);
        String nettyUri = serviceInstanceUtil.getServiceInstance(loginAndRegisterResponse.getUserId().toString());
        loginAndRegisterResponse.setNettyUri(nettyUri);
        loginAndRegisterResponse.setAccessToken(accessToken);
        loginAndRegisterResponse.setRefreshToken(refreshToken);
        String timestamp = stringRedisTemplate.opsForValue().getAndDelete(CommonConstant.OFFLINE_KEY_REDIS + userId);

        if(StringUtils.isBlank(timestamp))
            log.debug("用户 {} 无离线时间记录", userId);
        else{
            loginAndRegisterResponse.setOfflineTime(Long.valueOf(timestamp));
            log.info("用户 {} 上线，离线时间: {}", userId, timestamp);
        }
        return loginAndRegisterResponse;
    }

    //后续退出需要先通过jwt校验用户身份，利用拦截器拦截对应接口
    @Override
    public Boolean logout(String userId) {
        stringRedisTemplate.delete(CommonConstant.ACCESS_TOKEN_PREFIX + userId);
        stringRedisTemplate.delete(CommonConstant.REFRESH_TOKEN_PREFIX + userId);
        return true;
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        Claims claims = JwtUtil.parse(refreshToken);
        String userId = claims.getSubject();
        String redisRefreshToken = stringRedisTemplate.opsForValue().get(CommonConstant.REFRESH_TOKEN_PREFIX + userId);
        if(StringUtils.isBlank(redisRefreshToken) || !redisRefreshToken.equals(refreshToken))
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        String accessToken = JwtUtil.generate(userId, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        stringRedisTemplate.opsForValue().set(CommonConstant.ACCESS_TOKEN_PREFIX + userId, accessToken, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshToken);

        return tokenResponse;
    }
// 实例宕机或者用户网络断开，前端通过刷新可以再获取一个realtimeService实例
    @Override
    public String refreshUri(Long userId) {
        return serviceInstanceUtil.getServiceInstance(String.valueOf(userId));
    }


    private final OssUtils ossUtils;
    @Override
    //向服务端申请 MinIO 临时上传地址和文件访问地址。客户端拿到地址后，自己用 PUT 把文件上传到 MinIO


    public UploadUrlResponse uploadUrl(String fileName) {
        UploadUrlResponse uploadUrlResponse = new UploadUrlResponse();
        // 生成限时有效的 MinIO PUT 上传地址，前端用该地址直接上传图片
        String uploadUrl = ossUtils.uploadUrl(CommonConstant.BUCKET_NAME, fileName, CommonConstant.PICTURE_EXPIRE_TIME);
        //拼出访问 MinIO 中这个文件的 HTTP 地址
        String downUrl = ossUtils.downUrl(CommonConstant.BUCKET_NAME, fileName);
        uploadUrlResponse.setUploadUrl(uploadUrl);
        uploadUrlResponse.setDownloadUrl(downUrl);
        return uploadUrlResponse;
    }

    //当前代码里，userId 由前端传入 但更合适的做法是不让前端决定要改哪个用户：后端从已验证的登录 Token 中取得 userId，请求体只传 uri。
    //todo 目前网关虽然校验 Token，却没有核对 Token 中的用户 ID 与请求体的 userId 是否一致；按现有代码，改动请求体就可能指定别人的 ID。
    @Override
    public Boolean updateAvatar(UpdateAvatarRequest updateAvatarRequest) {
        Long userId = updateAvatarRequest.getUserId();
        User user = getById(userId);
        if(user == null) return false;
        user.setAvatar(updateAvatarRequest.getUri());
        return updateById(user);
    }




}




