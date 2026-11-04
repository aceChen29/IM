package com.ace.im.offlinedata.model.dto;


import lombok.Data;

@Data
public class SessionSummaryRequest {


    private Integer hours;


    private Long sessionId;


    private Long senderId;


}