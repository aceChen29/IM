-- KEYS[1]：Redis ZSET 的 key，本项目传入 friend-request-expire-zset。
local zsetKey = KEYS[1]

-- ARGV[1]：调用方给定的当前毫秒时间戳；传入 "0" 时改用 Redis 服务器时间。
local nowArg = ARGV[1]

-- ARGV[2]：本次最多取出的申请数量；Redis 传入的参数是字符串，先转为数字。
local maxCount = tonumber(ARGV[2])

-- 没有传数量或数量不合法时，默认一次最多取 500 条。
if not maxCount or maxCount <= 0 then maxCount = 500 end

-- 保存这次扫描使用的当前时间，单位为毫秒。
local nowMs

-- 未指定有效时间（项目传入 "0"）时，使用 Redis 自己的时间。
if (not nowArg) or nowArg == '' or tonumber(nowArg) <= 0 then
    -- Redis TIME 返回两个值：秒和微秒。
    local t = redis.call('TIME')
    -- 把秒、微秒合并成毫秒时间戳。
    nowMs = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000)
else
    -- 若调用方传了有效时间戳，则使用它。
    nowMs = tonumber(nowArg)
end

-- ZSET 的 score 是过期时间；取出 score <= nowMs 的前 maxCount 个申请 ID。
-- '-inf' 表示不限制最早时间，'LIMIT', 0, maxCount 表示从第 0 个取最多 maxCount 个。
local expired = redis.call('ZRANGEBYSCORE', zsetKey, '-inf', nowMs, 'LIMIT', 0, maxCount)

-- #expired 是列表长度；没有到期申请时返回空列表。
if #expired == 0 then return {} end

-- 遍历本次取到的申请 ID，并从 ZSET 删除；Lua 执行期间不会插入别的 Redis 命令。
for i = 1, #expired do redis.call('ZREM', zsetKey, expired[i]) end

-- 将取到的申请 ID 列表返回 Java 调度器，由它发送 Kafka 过期事件。
return expired
