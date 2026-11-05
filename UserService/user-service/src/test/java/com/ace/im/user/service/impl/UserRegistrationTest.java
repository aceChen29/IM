package com.ace.im.user.service.impl;

import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.constant.SessionTypeConstant;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.user.loadbalancer.NettyServiceLocator;
import com.ace.im.user.model.dto.UserRegisterRequest;
import com.ace.im.user.model.entity.Session;
import com.ace.im.user.model.entity.User;
import com.ace.im.user.model.entity.UserSession;
import com.ace.im.user.service.SessionService;
import com.ace.im.user.service.UserSessionService;
import com.ace.im.user.utils.EmailUtil;
import com.ace.im.user.utils.OssUtils;
import com.ace.im.user.utils.SnowflakeDynamicUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationTest {
    @Mock SessionService sessions;
    @Mock UserSessionService members;
    @Mock SnowflakeDynamicUtil ids;
    @Mock EmailUtil email;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    @Mock NettyServiceLocator locator;
    @Mock OssUtils oss;
    @Mock LambdaQueryChainWrapper<User> query;
    UserServiceImpl service;
    UserRegisterRequest request;

    @BeforeEach
    void setUp() {
        service = spy(new UserServiceImpl(sessions, ids, email, redis, locator, oss, members));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("new@example.com")).thenReturn("123456");
        doReturn(query).when(service).lambdaQuery();
        when(query.eq(any(), any())).thenReturn(query);
        when(query.exists()).thenReturn(false);
        doAnswer(invocation -> {
            invocation.<User>getArgument(0).setUserId(123L);
            return true;
        }).when(service).save(any(User.class));
        when(ids.nextId()).thenReturn(456L);
        request = new UserRegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setCode("123456");
        request.setNickname("新用户");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void registrationCreatesAiSessionAndBothMembers() {
        when(sessions.save(any(Session.class))).thenReturn(true);
        when(members.saveBatch(anyCollection())).thenReturn(true);

        var response = service.register(request);

        assertEquals(123L, response.getUserId());
        assertEquals("new@example.com", response.getEmail());
        ArgumentCaptor<Session> session = ArgumentCaptor.forClass(Session.class);
        verify(sessions).save(session.capture());
        assertEquals(456L, session.getValue().getSessionId());
        assertEquals(SessionTypeConstant.ROBOT_TYPE, session.getValue().getType());
        assertEquals(CommonConstant.SESSION_STATUS, session.getValue().getStatus());
        assertNotNull(session.getValue().getCreatedTime());
        ArgumentCaptor<Collection<UserSession>> rows = ArgumentCaptor.forClass((Class) Collection.class);
        verify(members).saveBatch(rows.capture());
        var relations = rows.getValue().stream().toList();
        assertEquals(2, relations.size());
        assertEquals(123L, relations.get(0).getUserId());
        assertEquals(CommonConstant.AI_ID, relations.get(1).getUserId());
        for (UserSession relation : relations) {
            assertEquals(456L, relation.getSessionId());
            assertEquals(CommonConstant.USER_ROLE_NORMAL, relation.getRole());
            assertEquals(CommonConstant.SESSION_STATUS, relation.getStatus());
            assertNotNull(relation.getCreatedTime());
        }
        verify(redis).delete("new@example.com");
    }

    @Test
    void sessionSaveFailurePropagatesWithoutConsumingVerificationCode() {
        when(sessions.save(any(Session.class))).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.register(request));
        verifyNoInteractions(members);
        verify(redis, never()).delete(anyString());
    }

    @Test
    void memberSaveFailurePropagatesWithoutConsumingVerificationCode() {
        when(sessions.save(any(Session.class))).thenReturn(true);
        when(members.saveBatch(anyCollection())).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.register(request));
        verify(redis, never()).delete(anyString());
    }
}
