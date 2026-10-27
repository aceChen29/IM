package com.ace.im.user.service;

import com.ace.im.user.model.entity.UserSession;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserSessionService extends IService<UserSession> {
    List<Long> getUserIdBySessionId(Long sessionId);
}
