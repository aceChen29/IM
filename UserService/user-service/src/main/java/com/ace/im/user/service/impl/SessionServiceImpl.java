package com.ace.im.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ace.im.user.model.entity.Session;
import com.ace.im.user.service.SessionService;
import com.ace.im.user.mapper.SessionMapper;
import org.springframework.stereotype.Service;

/**
* @author cy
* @description 针对表【session】的数据库操作Service实现
* @createDate 2026-10-28 22:50:42
*/
@Service
public class SessionServiceImpl extends ServiceImpl<SessionMapper, Session>
    implements SessionService{

}




