package com.ace.im.offlinedata.mapper;


import com.ace.im.common.model.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author cy
* @description 针对表【message】的数据库操作Mapper
* @createDate 2026-10-27 22:36:43
* @Entity com.ace.im.common.model.entity.Message
*/
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

}




