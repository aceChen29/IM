package com.ace.im.user.service;

import com.ace.im.common.model.dto.PageRequest;
import com.ace.im.user.model.entity.Friend;
import com.ace.im.user.model.entity.User;
import com.ace.im.user.model.dto.ModifyFriendApplicationResponse;
import com.ace.im.user.model.vo.FriendDTO;
import com.ace.im.user.model.vo.FriendDetailVO;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/** 好友关系服务。 */
public interface FriendService extends IService<Friend> {

    /** 根据邮箱或手机号搜索用户，并返回与当前用户的好友关系。 */
    FriendDetailVO searchUserByKeyword(String userId, String keyword);

    /** 获取指定用户的资料、与当前用户的好友状态及单聊会话 ID。 */
    FriendDetailVO getFriendDetails(String userId, String friendId);

    IPage<FriendDTO> getFriends(String userId, PageRequest pageRequest, String key);

    /** 删除双方好友关系、申请记录及共同的单聊会话。 */
    boolean deleteFriend(Long userId, Long friendId);

    /** 将当前用户指向好友的关系标记为拉黑。 */
    boolean blockFriend(Long userId, Long friendId);

    /** 将当前用户指向好友的拉黑关系恢复为正常。 */
    boolean unblockFriend(Long userId, Long friendId);

    /** 接受好友申请时建立双向好友关系和单聊会话。 */
    ModifyFriendApplicationResponse addFriend(User recipient, Long friendId);
}
