package com.ace.im.redpacket.mapper;

import com.ace.im.redpacket.model.entity.BalanceLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;


import org.apache.ibatis.annotations.Mapper;

/**
 * 余额变动记录 Mapper
 */
@Mapper
public interface BalanceLogMapper extends BaseMapper<BalanceLog> {
}