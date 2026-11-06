package com.ace.im.redpacket.service;


import com.ace.im.common.model.vo.RedPacketSendVO;
import com.ace.im.redpacket.model.dto.RedPacketReceiveRequest;
import com.ace.im.redpacket.model.dto.RedPacketSendRequest;
import com.ace.im.redpacket.model.vo.ReceiveResultVO;
import com.ace.im.redpacket.model.vo.RedPacketDetailVO;
import com.ace.im.redpacket.model.vo.RedPacketBasicVO;
import jakarta.validation.Valid;

/**
 * 红包服务接口
 *
 * @author shanyangcode
 */
public interface RedPacketService {

    /**
     * 发送红包
     *
     * @param request 红包发送请求
     * @return 红包发送结果（包含红包 ID 和消息 ID）
     */
    RedPacketSendVO sendRedPacket(RedPacketSendRequest request);


    void handleRedPacketExpiration(Long id);

    ReceiveResultVO receiveRedPacket(@Valid RedPacketReceiveRequest request);

    void handleRedPacketReceive(Long userId, Long redPacketId, Long receivedAmount, Long receiveTime);

    void handleRedPacketCompleted(Long redPacketId);

    RedPacketDetailVO getRedPacketDetail(Long redPacketId, int pageNum, int pageSize);

    RedPacketBasicVO getRedPacketBasicInfo(Long redPacketId);
}
