package com.ace.im.user.service.impl;

import com.ace.im.user.mapper.UserSessionMapper;
import com.ace.im.user.model.entity.UserSession;
import com.ace.im.user.service.UserSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserSessionServiceImpl extends ServiceImpl<UserSessionMapper, UserSession> implements UserSessionService {
    @Override
    public List<Long> getUserIdBySessionId(Long sessionId) {
        List<Long> userIds = lambdaQuery()
                .select(UserSession::getUserId)
                .eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getStatus, 0)
                .list()
                .stream()
                .map(UserSession::getUserId).toList();
        return userIds;
    }

    @Override
    public List<Long> getSessionIdsByUserId(Long userId) {
        List<Long> sessionIds = lambdaQuery().select(UserSession::getSessionId).eq(UserSession::getUserId, userId)
                .list().stream().map(UserSession::getSessionId).toList();
        return sessionIds;
    }
}
