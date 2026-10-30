package com.ace.im.user.mapper;

import com.ace.im.user.model.entity.Friend;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author cy
* @description 针对表【friend】的数据库操作Mapper
* @createDate 2026-10-28 21:09:49
* @Entity com.ace.im.user.model.entity.Friend
*/
@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

}




