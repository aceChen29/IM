package com.ace.im.user.controller;

import com.ace.im.common.common.BaseResponse;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.common.ResultUtils;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.user.model.dto.CreateGroupRequest;
import com.ace.im.user.model.dto.CreateGroupResponse;
import com.ace.im.user.model.dto.InviteGroupRequest;
import com.ace.im.user.model.dto.InviteGroupResponse;
import com.ace.im.user.service.GroupService;
import com.ace.im.user.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 群组接口。 */
@Slf4j
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final SessionService sessionService;
    private final GroupService groupService;

    @PostMapping
    public BaseResponse<?> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        try {
            CreateGroupResponse response = sessionService.createGroup(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("创建群聊失败，原因：{}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("创建群聊失败，原因：{}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    @PostMapping("/invite")
    public BaseResponse<?> inviteGroup(@Valid @RequestBody InviteGroupRequest request) {
        try {
            InviteGroupResponse response = groupService.inviteGroup(request);
            return ResultUtils.success(response);
        } catch (BusinessException e) {
            log.error("群聊邀请失败，原因：{}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("群聊邀请失败，原因：{}", e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }
}
