package com.ace.im.user.controller;



import com.ace.im.common.common.BaseResponse;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.common.ResultUtils;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.common.model.dto.PageRequest;
import com.ace.im.user.model.dto.AddFriendRequest;
import com.ace.im.user.model.dto.ApplyFriendDTO;
import com.ace.im.user.model.dto.ModifyFriendApplicationRequest;
import com.ace.im.user.model.dto.ModifyFriendApplicationResponse;
import com.ace.im.user.model.entity.Friend;
import com.ace.im.user.model.vo.FriendDTO;
import com.ace.im.user.model.vo.FriendDetailVO;
import com.ace.im.common.model.vo.PageResponse;
import com.ace.im.user.service.ApplyFriendService;
import com.ace.im.user.service.FriendService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 联系人Controller
 * <p>
 * 功能说明：
 * - 提供好友相关的REST API接口
 * - 包含好友申请、好友管理、好友查询等功能
 * - 支持分页查询和关键字搜索
 */
@Slf4j
@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {
    private final FriendService friendService;
    private final ApplyFriendService applyFriendService;


/**
 * 搜索用户（手机号或邮箱）
 *
 * @param userId  用户ID
 * @param keyword 搜索关键字（手机号或邮箱）
 * @return 用户详情
 */
    @GetMapping("/{userId}/user/search")
    public BaseResponse<?> searchUser(
            @PathVariable("userId") String userId,
            @RequestParam(value = "keyword") String keyword) {
        try {
            FriendDetailVO friendDetail = friendService.searchUserByKeyword(userId, keyword);
            return ResultUtils.success(friendDetail);
        } catch (BusinessException e) {
            log.error("搜索用户失败，用户ID：{}，关键字：{}，原因：{}", userId, keyword, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("搜索用户失败，用户ID：{}，关键字：{}，原因：{}", userId, keyword, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取联系人列表
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @param key         查询关键字
     * @return 联系人列表（分页）
     */
    @GetMapping("/{userId}/friend")
    public BaseResponse<?> getFriends(
            @PathVariable("userId") String userId,
            PageRequest pageRequest,
            @RequestParam(value = "key", defaultValue = "") String key) {
        try {
            // 查询分页数据
            IPage<FriendDTO> friendsPage = friendService.getFriends(userId, pageRequest, key);

            // 使用 PageResponse 统一返回格式
            return ResultUtils.success(PageResponse.of(friendsPage));
        } catch (BusinessException e) {
            log.error("获取好友列表失败，用户ID：{}，原因：{}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取好友列表失败，用户ID：{}，原因：{}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }
    //发送好友申请
    /**
     * 发送好友申请
     *
     * @param userId        发送者用户ID
     * @param receiveuserId 接收者用户ID
     * @param request         申请信息
     * @return 是否成功
     */
    @PostMapping("/{userId}/friend/{receiveuserId}")
    public BaseResponse<?> sendFriendRequest(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId,
            @Valid @RequestBody AddFriendRequest request) {
        try {
            Long senderId = Long.valueOf(userId);
            Long receiverId = Long.valueOf(receiveuserId);
            Long applyFriendId = applyFriendService.sendFriendRequest(senderId, receiverId, request.getMsg());
            return ResultUtils.success(applyFriendId != null);
        } catch (NumberFormatException e) {
            log.error("发送好友申请失败，用户ID格式错误，发送者：{}，接收者：{}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("发送好友申请失败，发送者：{}，接收者：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("发送好友申请失败，发送者：{}，接收者：{}，原因：{}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 获取当前用户发出和收到的好友申请列表。 */
    @GetMapping("/{userId}/apply")
    public BaseResponse<?> getApplyList(
            @PathVariable("userId") String userId,
            PageRequest pageRequest) {
        try {
            Long parsedUserId = Long.valueOf(userId);
            IPage<ApplyFriendDTO> page = applyFriendService.getReceivedRequestsWithUserInfo(parsedUserId, pageRequest);
            return ResultUtils.success(PageResponse.of(page));
        } catch (NumberFormatException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("获取好友申请列表失败，用户ID：{}，原因：{}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取好友申请列表失败，用户ID：{}", userId, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 获取当前用户收到的未读好友申请数量。 */
    @GetMapping("/{userId}/applyCount")
    public BaseResponse<?> getUnreadApplyCount(@PathVariable("userId") String userId) {
        try {
            int count = applyFriendService.getUnreadCount(Long.valueOf(userId));
            return ResultUtils.success(Map.of("count", count));
        } catch (NumberFormatException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("获取未读好友申请数量失败，用户ID：{}，原因：{}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取未读好友申请数量失败，用户ID：{}", userId, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 删除好友及双方共同的单聊会话。 */
    @DeleteMapping("/{userId}/friend/{receiveuserId}")
    public BaseResponse<?> deleteFriend(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId) {
        try {
            boolean deleted = friendService.deleteFriend(Long.valueOf(userId), Long.valueOf(receiveuserId));
            return ResultUtils.success(deleted);
        } catch (NumberFormatException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("删除好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("删除好友失败，用户：{}，好友：{}", userId, receiveuserId, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 拉黑好友。 */
    @PostMapping("/{userId}/block/{receiveuserId}")
    public BaseResponse<?> blockFriend(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId) {
        try {
            boolean blocked = friendService.blockFriend(Long.valueOf(userId), Long.valueOf(receiveuserId));
            return ResultUtils.success(blocked);
        } catch (NumberFormatException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("拉黑好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("拉黑好友失败，用户：{}，好友：{}", userId, receiveuserId, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 取消拉黑好友。 */
    @DeleteMapping("/{userId}/block/{receiveuserId}")
    public BaseResponse<?> unblockFriend(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId) {
        try {
            boolean unblocked = friendService.unblockFriend(Long.valueOf(userId), Long.valueOf(receiveuserId));
            return ResultUtils.success(unblocked);
        } catch (NumberFormatException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("取消拉黑好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("取消拉黑好友失败，用户：{}，好友：{}", userId, receiveuserId, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 通过、拒绝或标记已读好友申请。 */
    @PostMapping("/{userId}/application/{status}")
    public BaseResponse<?> modifyFriendApplicationStatus(
            @PathVariable("userId") String userId,
            @PathVariable("status") Integer status,
            @Valid @RequestBody ModifyFriendApplicationRequest request) {
        try {
            Long receiverId = Long.valueOf(userId);
            List<Long> senderIds = request.getReceiveuserIds().stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
            ModifyFriendApplicationResponse response =
                    applyFriendService.modifyApplicationStatus(receiverId, senderIds, status);
            return ResultUtils.success(response != null ? response : true);
        } catch (NumberFormatException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (IllegalArgumentException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不允许修改为该状态值");
        } catch (BusinessException e) {
            log.error("修改好友申请状态失败，用户：{}，状态：{}，原因：{}", userId, status, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("修改好友申请状态失败，用户：{}，状态：{}", userId, status, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 获取指定用户的资料、好友状态及双方单聊会话 ID。 */
    @GetMapping("/{userId}/friend/{friendId}")
    public BaseResponse<?> getFriendDetail(
            @PathVariable("userId") String userId,
            @PathVariable("friendId") String friendId) {
        try {
            FriendDetailVO detail = friendService.getFriendDetails(userId, friendId);
            return ResultUtils.success(detail);
        } catch (BusinessException e) {
            log.error("获取好友详情失败，用户：{}，好友：{}，原因：{}", userId, friendId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取好友详情失败，用户：{}，好友：{}", userId, friendId, e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

}
