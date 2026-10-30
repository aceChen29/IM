package com.ace.im.user.mapper;

import com.ace.im.user.model.entity.Session;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author cy
* @description 针对表【session】的数据库操作Mapper
* @createDate 2026-10-28 22:50:42
* @Entity com.ace.im.user.model.entity.Session
*/
@Mapper
public interface SessionMapper extends BaseMapper<Session> {

}




