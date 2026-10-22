-- Multi-Item Stock Reversion (Compensating Action)
-- KEYS: List of inventory:stock:{id}
-- ARGV: Corresponding quantities to add back

for i = 1, #KEYS do
    local req = tonumber(ARGV[i])
    redis.call('INCRBY', KEYS[i], req)
end

return 1
