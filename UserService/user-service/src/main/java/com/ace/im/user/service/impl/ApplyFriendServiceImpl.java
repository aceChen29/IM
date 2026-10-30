package com.ace.im.user.service.impl;

import cn.hutool.json.JSONUtil;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.common.model.dto.PageRequest;
import com.ace.im.user.constants.FriendApplicationStatusEnum;
import com.ace.im.user.constants.KafkaTopicConstant;
import com.ace.im.user.model.dto.ApplyFriendDTO;
import com.ace.im.user.model.dto.FriendApplicationNotificationDTO;
import com.ace.im.user.model.dto.FriendRequestCreationEvent;
import com.ace.im.user.model.dto.ModifyFriendApplicationResponse;
import com.ace.im.user.mapper.ApplyFriendMapper;
import com.ace.im.user.model.entity.ApplyFriend;
import com.ace.im.user.model.entity.Friend;
import com.ace.im.user.model.entity.User;
import com.ace.im.user.service.FriendService;
import com.ace.im.user.service.NotificationService;
import com.ace.im.user.service.UserService;
import com.ace.im.user.service.ApplyFriendService;
import com.ace.im.user.utils.SnowflakeDynamicUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
* @author cy
* @description 针对表【apply_friend】的数据库操作Service实现
* @createDate 2026-10-28 21:10:51
*/
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplyFriendServiceImpl extends ServiceImpl<ApplyFriendMapper, ApplyFriend> implements ApplyFriendService {
    private static final long FRIEND_REQUEST_EXPIRATION_HOURS = 24L;
    private static final int IS_RECEIVER_NO = 0;
    private static final int IS_RECEIVER_YES = 1;

    private final UserService userService;
    private final FriendService friendService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SnowflakeDynamicUtil snowflakeDynamicUtil;
    /** 保存或重新发起好友申请，并向 Kafka 发送通知和过期任务事件。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendFriendRequest(Long senderId, Long receiverId, String msg) {
        User sendUser = userService.getById(senderId);
        User receiveUser = userService.getById(receiverId);
        if (sendUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "发送方不存在");
        } else if (receiveUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "接收方不存在");
        }
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能添加自己");
        }

        boolean isFriend = friendService.lambdaQuery().eq(Friend::getUserId, senderId)
                .eq(Friend::getFriendId, receiverId).exists();
        if (isFriend) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已经是好友，无需重复添加");
        }

        ApplyFriend apply = lambdaQuery().eq(ApplyFriend::getSenderId, senderId)
                .eq(ApplyFriend::getReceiverId, receiverId).one();
        if (apply == null) {
            return handleNewFriendApplication(senderId, receiverId, msg, sendUser);
        } else if (apply.getStatus() == FriendApplicationStatusEnum.ACCEPTED.getCode()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已经是好友，无需重复添加");
        } else {
            return handleExistingFriendApplication(apply, msg, sendUser);
        }
    }

    /** 新申请：生成 ID，保存申请，发送通知及过期登记事件。 */
    private Long handleNewFriendApplication(Long senderId, Long receiverId, String message, User sender) {
        ApplyFriend apply = new ApplyFriend();
        Long applyFriendId = snowflakeDynamicUtil.nextId();
        apply.setApplyFriendId(applyFriendId);
        apply.setSenderId(senderId);
        apply.setReceiverId(receiverId);
        apply.setMessage(message);
        apply.setStatus(FriendApplicationStatusEnum.UNREAD.getCode());
        apply.setCreatedTime(LocalDateTime.now());
        apply.setUpdatedTime(LocalDateTime.now());
        if (!save(apply)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建好友申请失败");
        }

        sendFriendApplicationNotification(receiverId, sender, message);
        registerExpirationTask(applyFriendId);
        return applyFriendId;
    }

    /** 已有申请：保留原 ID，重置状态和附言，再发送通知及过期登记事件。 */
    private Long handleExistingFriendApplication(ApplyFriend apply, String message, User sender) {
        boolean updated = this.update(new LambdaUpdateWrapper<ApplyFriend>()
                .set(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode())
                .set(ApplyFriend::getMessage, message)
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getApplyFriendId, apply.getApplyFriendId()));
        if (!updated) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新好友申请失败");
        }

        sendFriendApplicationNotification(apply.getReceiverId(), sender, message);
        registerExpirationTask(apply.getApplyFriendId());
        return apply.getApplyFriendId();
    }

    /** 由 NotificationService 组装系统通知并发送到 Kafka。 */
    private void sendFriendApplicationNotification(Long receiverId, User sender, String message) {
        FriendApplicationNotificationDTO notification = new FriendApplicationNotificationDTO();
        notification.setApplyUserId(sender.getUserId());
        notification.setApplyUserName(sender.getNickname());
        notification.setApplyFriendAvatar(sender.getAvatar());
        notification.setMessage(message);
        notificationService.pushNewApply(receiverId, notification);
    }

    /** 发送好友申请创建事件，由后续消费者登记 24 小时后的过期任务。 */
    private void registerExpirationTask(Long applyFriendId) {
        long createTime = System.currentTimeMillis();
        FriendRequestCreationEvent expiration = new FriendRequestCreationEvent();
        expiration.setApplyFriendId(applyFriendId);
        expiration.setCreateTime(createTime);
        expiration.setExpireTime(createTime + FRIEND_REQUEST_EXPIRATION_HOURS * 60 * 60 * 1000);
        try {
            kafkaTemplate.send(KafkaTopicConstant.TOPIC_FRIEND_REQUEST_CREATION, applyFriendId.toString(),
                    JSONUtil.toJsonStr(expiration)).whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("登记好友申请过期任务失败，applyFriendId={}", applyFriendId, error);
                        } else {
                            log.info("登记好友申请过期任务成功，applyFriendId={}", applyFriendId);
                        }
                    });
        } catch (Exception e) {
            log.warn("好友申请已保存，但登记过期任务失败，applyFriendId={}", applyFriendId, e);
        }
    }

    /** 查询好友申请并补充另一方的用户信息。 */
    @Override
    public IPage<ApplyFriendDTO> getReceivedRequestsWithUserInfo(Long userId, PageRequest pageRequest) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        }

        int pageNum = pageRequest.getPageNum();
        int pageSize = pageRequest.getPageSize();
        IPage<ApplyFriend> applyFriendPage = getReceivedRequests(userId, pageNum, pageSize);
        List<ApplyFriendDTO> dtoList = mapApplyFriendsToDTO(applyFriendPage.getRecords(), userId);

        Page<ApplyFriendDTO> dtoPage = new Page<>(pageNum, pageSize, applyFriendPage.getTotal());
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    /** 教程中的列表同时包含当前用户发出和收到的申请。 */
    public IPage<ApplyFriend> getReceivedRequests(Long userId, int pageNum, int pageSize) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        }

        Page<ApplyFriend> page = new Page<>(pageNum, pageSize);
        return lambdaQuery()
                .and(wrapper -> wrapper.eq(ApplyFriend::getSenderId, userId)
                        .or()
                        .eq(ApplyFriend::getReceiverId, userId))
                .orderByDesc(ApplyFriend::getUpdatedTime)
                .page(page);
    }

    /** 一次性查询本页涉及的用户，避免每条申请单独查询用户。 */
    private List<ApplyFriendDTO> mapApplyFriendsToDTO(List<ApplyFriend> applyFriends, Long userId) {
        if (applyFriends == null || applyFriends.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> targetUserIds = new HashSet<>();
        for (ApplyFriend applyFriend : applyFriends) {
            Long targetId = applyFriend.getSenderId().equals(userId)
                    ? applyFriend.getReceiverId()
                    : applyFriend.getSenderId();
            if (targetId != null) {
                targetUserIds.add(targetId);
            }
        }

        Map<Long, User> userMap = new HashMap<>();
        if (!targetUserIds.isEmpty()) {
            List<User> users = userService.listByIds(targetUserIds);
            if (users != null) {
                for (User user : users) {
                    userMap.put(user.getUserId(), user);
                }
            }
        }

        List<ApplyFriendDTO> dtoList = new ArrayList<>(applyFriends.size());
        for (ApplyFriend applyFriend : applyFriends) {
            ApplyFriendDTO dto = new ApplyFriendDTO();
            dto.setMsg(applyFriend.getMessage());
            dto.setStatus(applyFriend.getStatus());
            dto.setTime(applyFriend.getUpdatedTime());

            Long targetUserId = applyFriend.getSenderId().equals(userId)
                    ? applyFriend.getReceiverId()
                    : applyFriend.getSenderId();
            User targetUser = userMap.get(targetUserId);
            if (targetUser != null) {
                dto.setUserId(String.valueOf(targetUser.getUserId()));
                dto.setNickname(targetUser.getNickname());
                dto.setAvatar(targetUser.getAvatar());
                dto.setIsReceiver(applyFriend.getSenderId().equals(userId) ? IS_RECEIVER_NO : IS_RECEIVER_YES);
            }
            dtoList.add(dto);
        }
        return dtoList;
    }

    @Override
    public int getUnreadCount(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        }

        LambdaQueryWrapper<ApplyFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApplyFriend::getReceiverId, userId)
                .eq(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode());
        return Math.toIntExact(baseMapper.selectCount(queryWrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModifyFriendApplicationResponse modifyApplicationStatus(Long receiverId,
                                                                    List<Long> senderIds,
                                                                    Integer status) {
        if (receiverId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接收者ID不能为空");
        }
        if (senderIds == null || senderIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "发送者ID列表不能为空");
        }
        if (status == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态不能为空");
        }

        FriendApplicationStatusEnum statusEnum = FriendApplicationStatusEnum.fromCode(status);
        return switch (statusEnum) {
            case ACCEPTED -> handleAcceptApplication(receiverId, senderIds);
            case REJECTED -> handleRejectApplication(receiverId, senderIds);
            case READ -> {
                handleReadApplication(receiverId, senderIds);
                yield null;
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许修改为该状态值");
        };
    }

    private ModifyFriendApplicationResponse handleRejectApplication(Long receiverId, List<Long> senderIds) {
        if (senderIds.size() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "拒绝状态只能包含一个发送者");
        }
        ApplyFriend apply = findApplyFriendBySenderAndReceiver(senderIds.get(0), receiverId);
        if (apply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "好友申请不存在");
        }
        handleFriendRequest(apply, receiverId, false);
        return null;
    }

    private void handleReadApplication(Long receiverId, List<Long> senderIds) {
        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus, FriendApplicationStatusEnum.READ.getCode())
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getReceiverId, receiverId)
                .in(ApplyFriend::getSenderId, senderIds)
                .eq(ApplyFriend::getStatus, FriendApplicationStatusEnum.UNREAD.getCode());
        if (baseMapper.update(null, updateWrapper) <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "标记已读失败");
        }
    }

    private ModifyFriendApplicationResponse handleAcceptApplication(Long receiverId, List<Long> senderIds) {
        if (senderIds.size() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "通过状态只能包含一个发送者");
        }
        ApplyFriend apply = findApplyFriendBySenderAndReceiver(senderIds.get(0), receiverId);
        if (apply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "好友申请不存在");
        }
        ModifyFriendApplicationResponse response = handleFriendRequest(apply, receiverId, true);
        if (response == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "处理好友申请失败");
        }
        return response;
    }

    private ApplyFriend findApplyFriendBySenderAndReceiver(Long senderId, Long receiverId) {
        return lambdaQuery()
                .eq(ApplyFriend::getSenderId, senderId)
                .eq(ApplyFriend::getReceiverId, receiverId)
                .one();
    }

    /** 仅申请接收者可以处理尚未处理且未过期的申请。 */
    private ModifyFriendApplicationResponse handleFriendRequest(ApplyFriend applyFriend,
                                                                  Long receiverId,
                                                                  boolean accept) {
        if (!Objects.equals(applyFriend.getReceiverId(), receiverId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权处理该好友申请");
        }
        if (!Objects.equals(applyFriend.getStatus(), FriendApplicationStatusEnum.UNREAD.getCode())
                && !Objects.equals(applyFriend.getStatus(), FriendApplicationStatusEnum.READ.getCode())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该好友申请已处理或已过期");
        }

        LambdaUpdateWrapper<ApplyFriend> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ApplyFriend::getStatus,
                        accept ? FriendApplicationStatusEnum.ACCEPTED.getCode()
                                : FriendApplicationStatusEnum.REJECTED.getCode())
                .set(ApplyFriend::getUpdatedTime, LocalDateTime.now())
                .eq(ApplyFriend::getApplyFriendId, applyFriend.getApplyFriendId())
                .in(ApplyFriend::getStatus,
                        FriendApplicationStatusEnum.UNREAD.getCode(),
                        FriendApplicationStatusEnum.READ.getCode());
        boolean updated = this.update(updateWrapper);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该好友申请已处理或已过期");
        }
        if (accept) {
            User receiver = userService.getById(receiverId);
            return friendService.addFriend(receiver, applyFriend.getSenderId());
        }
        return null;
    }
}




