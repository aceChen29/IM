package com.ace.im.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.constant.SessionTypeConstant;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.user.constants.FriendStatusEnum;
import com.ace.im.user.constants.UserConstant;
import com.ace.im.user.constants.UserStateEnum;
import com.ace.im.user.mapper.FriendMapper;
import com.ace.im.user.mapper.ApplyFriendMapper;
import com.ace.im.common.model.dto.PageRequest;
import com.ace.im.user.model.entity.ApplyFriend;
import com.ace.im.user.model.entity.Friend;
import com.ace.im.user.model.entity.Session;
import com.ace.im.user.model.entity.User;
import com.ace.im.user.model.entity.UserSession;
import com.ace.im.user.model.vo.FriendDTO;
import com.ace.im.user.model.vo.FriendDetailVO;
import com.ace.im.user.model.dto.ModifyFriendApplicationResponse;
import com.ace.im.user.model.dto.NewSessionNotificationDTO;
import com.ace.im.user.service.FriendService;
import com.ace.im.user.service.NotificationService;
import com.ace.im.user.service.SessionService;
import com.ace.im.user.service.UserService;
import com.ace.im.user.service.UserSessionService;
import com.ace.im.user.utils.SnowflakeDynamicUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 好友关系服务实现。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FriendServiceImpl extends ServiceImpl<FriendMapper, Friend> implements FriendService {

    private static final String FRIEND_STATUS_KEY_PREFIX = "msg:validate:friend:status:";

    private final UserService userService;
    private final UserSessionService userSessionService;
    private final SessionService sessionService;
    private final ApplyFriendMapper applyFriendMapper;
    private final NotificationService notificationService;
    private final SnowflakeDynamicUtil snowflakeDynamicUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public FriendDetailVO searchUserByKeyword(String userId, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索关键字不能为空");
        }

        User user;
        if (keyword.matches(UserConstant.EMAIL_REGEX)) {
            user = userService.lambdaQuery().eq(User::getEmail, keyword).one();
        } else if (keyword.matches(UserConstant.PHONE_REGEX)) {
            user = userService.lambdaQuery().eq(User::getPhone, keyword).one();
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入有效手机号或邮箱");
        }
        validateFriendUser(user);
        return getFriendDetails(userId, user.getUserId().toString());
    }

    @Override
    public FriendDetailVO getFriendDetails(String userId, String friendId) {
        Long currentUserId;
        Long targetUserId;
        try {
            currentUserId = Long.valueOf(userId);
            targetUserId = Long.valueOf(friendId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        }

        User user = userService.getById(targetUserId);
        validateFriendUser(user);

        FriendDetailVO detail = new FriendDetailVO();
        BeanUtil.copyProperties(user, detail);
        detail.setUserId(user.getUserId().toString());
        detail.setSignature(user.getDescription());

        Friend friend = lambdaQuery()
                .eq(Friend::getUserId, currentUserId)
                .eq(Friend::getFriendId, targetUserId)
                .one();
        detail.setStatus(friend == null ? FriendStatusEnum.NON_FRIEND.getCode() : friend.getStatus());
        detail.setSessionId(friend == null || friend.getSessionId() == null
                ? null : friend.getSessionId().toString());
        return detail;
    }
    /** 按关键字筛选好友，返回好友关系和单聊会话信息。 */
    @Override
    public IPage<FriendDTO> getFriends(String userId, PageRequest pageRequest, String key) {
        Long currentUserId;
        try {
            currentUserId = Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        }
        if (pageRequest == null || pageRequest.getPageNum() <= 0 || pageRequest.getPageSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数无效");
        }
        validateFriendUser(userService.getById(currentUserId));

        int pageNum = pageRequest.getPageNum();
        int pageSize = pageRequest.getPageSize();
        Page<FriendDTO> page = new Page<>(pageNum, pageSize);

        List<Friend> friendList = lambdaQuery()
                .eq(Friend::getUserId, currentUserId)
                .ne(Friend::getStatus, FriendStatusEnum.DELETED.getCode())
                .orderByDesc(Friend::getCreatedTime)
                .list();
        if (friendList.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }

        List<Long> friendIds = friendList.stream().map(Friend::getFriendId).collect(Collectors.toList());
        List<User> userList = userService.lambdaQuery()
                .in(User::getUserId, friendIds)
                .and(key != null && !key.isBlank(), w -> w
                        .like(User::getNickname, key)
                        .or().like(User::getPhone, key)
                        .or().like(User::getUserId, key)
                        .or().like(User::getEmail, key))
                .list();
        Map<Long, User> usersById = userList.stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        List<FriendDTO> friendDTOs = friendList.stream()
                .filter(friend -> usersById.containsKey(friend.getFriendId())) //保留条件为 true 的元素，排除条件为 false 的元素。
                .map(friend -> {
                    User friendUser = usersById.get(friend.getFriendId());
                    FriendDTO dto = BeanUtil.copyProperties(friendUser, FriendDTO.class);
                    dto.setUserId(friendUser.getUserId().toString());
                    dto.setSignature(friendUser.getDescription());
                    dto.setStatus(friend.getStatus());
                    dto.setSessionId(friend.getSessionId() == null ? null : friend.getSessionId().toString());
                    return dto;
                })
                .toList();

        page.setTotal(friendDTOs.size());
        long fromIndex = ((long) pageNum - 1) * pageSize;
        if (fromIndex >= friendDTOs.size()) {
            page.setRecords(List.of());
        } else {
            int start = (int) fromIndex;
            page.setRecords(friendDTOs.subList(start, Math.min(start + pageSize, friendDTOs.size())));
        }
        return page;
    }

    /**
     * 验证好友用户是否存在及其状态
     *
     * @param friendUser 好友的User实体
     */
    private void validateFriendUser(User friendUser) {
        if (friendUser == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXISTS);
        }

        // 用户状态：0 正常，1 封禁，2 注销
        if (Objects.equals(friendUser.getState(), UserStateEnum.BANNED.getCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户已被封禁");
        }
        if (Objects.equals(friendUser.getState(), UserStateEnum.CANCELLED.getCode())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该用户已注销");
        }
    }

    /** 按教程顺序删除申请、好友关系和共同单聊会话。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFriend(Long userId, Long friendId) {
        if (userId == null || userId < 0 || friendId == null || friendId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }

        Friend friend = lambdaQuery()
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .one();
        if (friend == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "好友关系不存在");
        }

        deleteApplyFriendRecords(userId, friendId);
        deleteFriendRecords(userId, friendId);
        deleteSessionRecords(userId, friendId);
        evictFriendCache(userId, friendId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean blockFriend(Long userId, Long friendId) {
        if (userId == null || userId < 0 || friendId == null || friendId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }

        Friend friend = lambdaQuery()
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .one();
        if (friend == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "好友关系不存在");
        }

        LambdaUpdateWrapper<Friend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Friend::getStatus, FriendStatusEnum.BLOCKED.getCode())
                .set(Friend::getUpdatedTime, LocalDateTime.now())
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        boolean result = this.update(updateWrapper);
        evictFriendCache(userId, friendId);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unblockFriend(Long userId, Long friendId) {
        if (userId == null || userId < 0 || friendId == null || friendId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }

        Friend friend = lambdaQuery()
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .one();
        if (friend == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "好友关系不存在");
        }
        if (!Objects.equals(friend.getStatus(), FriendStatusEnum.BLOCKED.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该好友未被拉黑");
        }

        LambdaUpdateWrapper<Friend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Friend::getStatus, FriendStatusEnum.NORMAL.getCode())
                .set(Friend::getUpdatedTime, LocalDateTime.now())
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        boolean result = this.update(updateWrapper);
        evictFriendCache(userId, friendId);
        return result;
    }

    /** 删除 A 到 B、B 到 A 两个方向的申请。 */
    private void deleteApplyFriendRecords(Long userId, Long friendId) {
        LambdaQueryWrapper<ApplyFriend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .nested(nested -> nested.eq(ApplyFriend::getSenderId, userId)
                        .eq(ApplyFriend::getReceiverId, friendId))
                .or()
                .nested(nested -> nested.eq(ApplyFriend::getSenderId, friendId)
                        .eq(ApplyFriend::getReceiverId, userId)));
        applyFriendMapper.delete(wrapper);
    }

    /** 删除 A 到 B、B 到 A 两个方向的好友关系。 */
    private void deleteFriendRecords(Long userId, Long friendId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .nested(nested -> nested.eq(Friend::getUserId, userId)
                        .eq(Friend::getFriendId, friendId))
                .or()
                .nested(nested -> nested.eq(Friend::getUserId, friendId)
                        .eq(Friend::getFriendId, userId)));
        baseMapper.delete(wrapper);
    }

    /** 找到双方共同的单聊会话，先删成员关系，再删会话。 */
    private void deleteSessionRecords(Long userId, Long friendId) {
        List<Long> sessionIds1 = userSessionService.lambdaQuery()
                .select(UserSession::getSessionId)
                .eq(UserSession::getUserId, userId)
                .list().stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());
        List<Long> sessionIds2 = userSessionService.lambdaQuery()
                .select(UserSession::getSessionId)
                .eq(UserSession::getUserId, friendId)
                .list().stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());
        sessionIds1.retainAll(sessionIds2);
        if (sessionIds1.isEmpty()) {
            return;
        }

        List<Long> singleChatSessionIds = sessionService.lambdaQuery()
                .select(Session::getSessionId)
                .in(Session::getSessionId, sessionIds1)
                .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE)
                .list().stream()
                .map(Session::getSessionId)
                .collect(Collectors.toList());
        if (singleChatSessionIds.isEmpty()) {
            return;
        }

        userSessionService.remove(new LambdaQueryWrapper<UserSession>()
                .in(UserSession::getSessionId, singleChatSessionIds));
        sessionService.remove(new LambdaQueryWrapper<Session>()
                .in(Session::getSessionId, singleChatSessionIds));
    }

    /** 删除双向好友状态缓存；失败时记录日志，不中断数据库删除。 */
    private void evictFriendCache(Long userId, Long friendId) {
        try {
            String key1 = FRIEND_STATUS_KEY_PREFIX + userId + ":" + friendId;
            String key2 = FRIEND_STATUS_KEY_PREFIX + friendId + ":" + userId;
            stringRedisTemplate.delete(Arrays.asList(key1, key2));
            log.info("已清除好友关系缓存: {} <-> {}", userId, friendId);
        } catch (Exception e) {
            log.warn("清除好友关系缓存失败: {} <-> {}", userId, friendId, e);
        }
    }

    /** 通过申请后建立单聊关系，并向申请人推送新会话通知。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModifyFriendApplicationResponse addFriend(User recipient, Long friendId) {
        if (friendId == null || friendId < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }
        if (recipient == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "接收者用户不存在");
        }

        User applicant = userService.getById(friendId);
        if (applicant == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "好友申请者不存在");
        }
        boolean exists = lambdaQuery()
                .eq(Friend::getUserId, recipient.getUserId())
                .eq(Friend::getFriendId, friendId)
                .exists();
        if (exists) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已经是好友关系");
        }

        // 当前 friend 表新增了 session_id；先创建会话，再将同一个 ID 写入双向好友关系。
        Long sessionId = createSession();
        createFriendRelations(recipient.getUserId(), friendId, sessionId);
        createUserSessions(recipient.getUserId(), friendId, sessionId);
        sendNewSessionNotification(friendId, recipient, sessionId);
        return buildModifyFriendApplicationResponse(applicant, sessionId);
    }

    private void createFriendRelations(Long userId, Long friendId, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        Friend friend1 = new Friend();
        friend1.setUserId(userId);
        friend1.setFriendId(friendId);
        friend1.setSessionId(sessionId);
        friend1.setStatus(FriendStatusEnum.NORMAL.getCode());
        friend1.setCreatedTime(now);
        friend1.setUpdatedTime(now);

        Friend friend2 = new Friend();
        friend2.setUserId(friendId);
        friend2.setFriendId(userId);
        friend2.setSessionId(sessionId);
        friend2.setStatus(FriendStatusEnum.NORMAL.getCode());
        friend2.setCreatedTime(now);
        friend2.setUpdatedTime(now);

        if (baseMapper.insert(friend1) <= 0 || baseMapper.insert(friend2) <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "添加好友关系失败");
        }
    }

    private Long createSession() {
        Long sessionId = snowflakeDynamicUtil.nextId();
        Date now = new Date();
        Session session = new Session();
        session.setSessionId(sessionId);
        session.setName("");
        session.setType(SessionTypeConstant.SIGNAL_TYPE);
        session.setStatus(0);
        session.setCreatedTime(now);
        session.setUpdatedTime(now);
        if (!sessionService.save(session)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建会话失败");
        }
        return sessionId;
    }

    private void createUserSessions(Long userId, Long friendId, Long sessionId) {
        Date now = new Date();
        UserSession userSession1 = new UserSession();
        userSession1.setUserId(userId);
        userSession1.setSessionId(sessionId);
        userSession1.setRole(2);
        userSession1.setStatus(0);
        userSession1.setCreatedTime(now);
        userSession1.setUpdatedTime(now);

        UserSession userSession2 = new UserSession();
        userSession2.setUserId(friendId);
        userSession2.setSessionId(sessionId);
        userSession2.setRole(2);
        userSession2.setStatus(0);
        userSession2.setCreatedTime(now);
        userSession2.setUpdatedTime(now);

        if (!userSessionService.save(userSession1) || !userSessionService.save(userSession2)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建用户会话关系失败");
        }
    }

    private void sendNewSessionNotification(Long recipientId, User sender, Long sessionId) {
        try {
            NewSessionNotificationDTO notification = new NewSessionNotificationDTO();
            notification.setSessionName(sender.getNickname());
            notification.setAvatar(sender.getAvatar());
            notificationService.pushNewSession(sender.getUserId(), recipientId, sessionId,
                    SessionTypeConstant.SIGNAL_TYPE, notification);
            log.info("发送新会话通知成功，接收者ID={}，会话ID={}", recipientId, sessionId);
        } catch (Exception e) {
            log.warn("发送新会话通知失败，接收者ID={}，会话ID={}", recipientId, sessionId, e);
        }
    }

    private ModifyFriendApplicationResponse buildModifyFriendApplicationResponse(User applicant, Long sessionId) {
        ModifyFriendApplicationResponse response = new ModifyFriendApplicationResponse();
        response.setUserId(String.valueOf(applicant.getUserId()));
        response.setSessionId(String.valueOf(sessionId));
        response.setSessionType(SessionTypeConstant.SIGNAL_TYPE);
        response.setSessionName(applicant.getNickname());
        response.setAvatar(applicant.getAvatar());
        return response;
    }
}
