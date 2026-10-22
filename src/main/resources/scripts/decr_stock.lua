-- Multi-Item Stock Deduction (All-or-Nothing)
-- KEYS: List of inventory:stock:{id}
-- ARGV: Corresponding quantities to deduct

for i = 1, #KEYS do
    local stock = tonumber(redis.call('GET', KEYS[i]))
    local req = tonumber(ARGV[i])
    if not stock or stock < req then
        return 0
    end
end

for i = 1, #KEYS do
    local req = tonumber(ARGV[i])
    redis.call('DECRBY', KEYS[i], req)
end

return 1
