package com.ace.im.user.mapper;

import com.ace.im.user.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author cy
* @description 针对表【user】的数据库操作Mapper
* @createDate 2026-10-24 13:53:29
* @Entity generator.domain.User
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {

}




