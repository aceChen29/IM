package com.ace.im.model.vo;


import lombok.Data;

@Data

public class TokenResponse {

    private String accessToken;

    private String refreshToken;
}