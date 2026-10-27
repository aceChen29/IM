package com.ace.im.user.model.vo;


import lombok.Data;

@Data

public class TokenResponse {

    private String accessToken;

    private String refreshToken;
}