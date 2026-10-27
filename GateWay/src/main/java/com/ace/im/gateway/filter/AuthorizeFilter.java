package com.ace.im.gateway.filter;

import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.utils.JwtUtil;

import com.ace.im.common.common.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
/*
设置白名单，过滤器中exclude白名单
看请求头中有没有双token 去做校验
设置 过滤器优先级 order实现order接口， 数字越小优先级越高
* */
public class AuthorizeFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate stringRedisTemplate;

    // 白名单路径：不需要认证的接口
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/api/user/login/code",
            "/api/user/register",
            "/api/user/sendCaptcha",
            "/api/user/login/password",
            "/api/user/refresh"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 白名单路径直接放行
        if (EXCLUDE_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 获取 Access-Token 和 Refresh-Token
        String accessToken = request.getHeaders().getFirst("Access-Token");
        String refreshToken = request.getHeaders().getFirst("Refresh-Token");

        try {
            // 1. 尝试用 Access-Token 验证
            if (accessToken != null && !accessToken.isEmpty()) {
                Claims acClaims = JwtUtil.parse(accessToken);
                if (acClaims != null) {
                    String userId = acClaims.getSubject();
                    String redisAccessToken = stringRedisTemplate.opsForValue()
                            .get(CommonConstant.ACCESS_TOKEN_PREFIX + userId);
                    if (accessToken.equals(redisAccessToken)) {
                        // Token 合法，放行
                        return chain.filter(exchange);
                    }
                }
            }

            // 2. 如果 Access-Token 无效，尝试用 Refresh-Token 判断是否需要刷新
            if (refreshToken != null && !refreshToken.isEmpty()) {
                Claims rfClaims = JwtUtil.parse(refreshToken);
                if (rfClaims != null) {
                    String userId = rfClaims.getSubject();
                    String redisRefreshToken = stringRedisTemplate.opsForValue()
                            .get(CommonConstant.REFRESH_TOKEN_PREFIX + userId);
                    if (refreshToken.equals(redisRefreshToken)) {
                        // Refresh-Token 有效，但 Access-Token 已失效 → 触发前端刷新
                        return buildErrorResponse(exchange, ErrorCode.TOKEN_EXPIRED);
                    }
                }
            }

            // 3. 两者都无效 → 未登录
            return buildErrorResponse(exchange, ErrorCode.NOT_LOGIN_ERROR);

        } catch (Exception e) {
            log.error("JWT 校验系统异常", e);
            return buildErrorResponse(exchange, ErrorCode.SYSTEM_ERROR, "登录状态确认失败");
        }
    }

    /**
     * 构建错误响应（通用）
     */
    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, ErrorCode errorCode) {
        return buildErrorResponse(exchange, errorCode, errorCode.getMessage());
    }

    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, ErrorCode errorCode, String message) {
        String jsonResponse = String.format("{\"code\":%d,\"message\":\"%s\"}", errorCode.getCode(), message);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));

        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // 或根据 errorCode 映射 HttpStatus
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // 优先级较高
    }
}