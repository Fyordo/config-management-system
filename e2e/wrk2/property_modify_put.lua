wrk.method = "POST"
wrk.path = "/v1/property/modify/put"
wrk.headers["Content-Type"] = "application/json"

init = function(args)
  math.randomseed(os.time())
end

request = function()
  local i = math.random(1, 500)
  local body = string.format(
    [[{"key":{"version":1,"namespace":"dev","service":"cms-j-sset","appId":"cms-j-sset-0","key":"app.e2e.p%d"},"value":"123123"}]],
    i
  )
  return wrk.format("POST", wrk.path, wrk.headers, body)
end

local ok = 0
local bad = 0

response = function(status, headers, resp_body)
  if status == 200 or status == 201 then
    ok = ok + 1
  else
    bad = bad + 1
  end
end

done = function(summary, latency, requests)
  io.write("\n")
  io.write("Custom counters:\n")
  io.write("  2xx responses: " .. ok .. "\n")
  io.write("  non-2xx responses: " .. bad .. "\n")
end
