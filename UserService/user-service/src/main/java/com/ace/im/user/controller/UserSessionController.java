package com.ace.im.user.controller;

import com.ace.im.common.common.BaseResponse;
import com.ace.im.common.common.ResultUtils;
import com.ace.im.user.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserSessionController {
    private final UserSessionService userSessionService;
    @GetMapping("/get/receivers")
    public List<Long> getUserIdBySessionId(@RequestParam("sessionId") Long sessionId) {
        return userSessionService.getUserIdBySessionId(sessionId);
    }
    @GetMapping("/get/sessions")
    List<Long> getSessionIdsByUserId(@RequestParam("userId")  Long userId) {
        return userSessionService.getSessionIdsByUserId(userId);
    }
}
