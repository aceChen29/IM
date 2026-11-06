package com.ace.im.redpacket.mapper;

import com.ace.im.redpacket.model.entity.RedPacketReceive;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 红包领取记录 Mapper
 */
@Mapper
public interface RedPacketReceiveMapper extends BaseMapper<RedPacketReceive> {

}