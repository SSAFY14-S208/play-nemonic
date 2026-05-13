-- ============================================================
-- fluent-bit lua filter
-- ============================================================
-- 1) tag(=docker log file path)에서 container_id (16자, full 64자) 추출.
-- 2) full container_id로 /var/lib/docker/containers/<id>/config.v2.json 을
--    읽어 container_name 도 박는다. 이 이름은 재배포해도 동일하게 유지되어
--    ("nemonic-prod-app-1" 등) 다운스트림 logstash 에서 service 매핑에 안정적
--    join key가 된다.
-- 3) 결과 캐시 — 매 record file IO 막기 (캐시 hit/miss 양쪽 모두 캐시).
--
-- 마운트 요구: fluent-bit 컨테이너에 /var/lib/docker/containers 가 ro 로 마운트
-- 되어 있어야 한다. compose 설정 변경 없이 동작 (이미 마운트됨).
-- ============================================================

-- container_id(full) → container_name 또는 false(못 찾음). false도 캐시해서
-- 반복 IO 회피. 컨테이너 재배포 시 ID가 바뀌므로 new ID는 새로 lookup, old ID는
-- 캐시에 남아 있어도 무해 (메모리 작음).
local name_cache = {}

local function read_container_name(cid_full)
    if name_cache[cid_full] ~= nil then
        if name_cache[cid_full] == false then
            return nil
        end
        return name_cache[cid_full]
    end

    local path = "/var/lib/docker/containers/" .. cid_full .. "/config.v2.json"
    local f = io.open(path, "rb")
    if not f then
        name_cache[cid_full] = false
        return nil
    end
    local content = f:read("*all")
    f:close()

    -- Docker config.v2.json 표준: "Name":"/<container-name>" (앞에 슬래시 포함)
    local name = content:match('"Name":"/([^"]+)"')
    if name then
        name_cache[cid_full] = name
        return name
    end
    name_cache[cid_full] = false
    return nil
end

function extract_container_id(tag, timestamp, record)
    -- 짧은 ID (16자) — 기존 호환. log_tag 전체도 함께 보존.
    local cid_short = string.match(tag, "([a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9])")
    if cid_short then
        record["container_id"] = cid_short
    end
    record["log_tag"] = tag

    -- full ID (64자) 는 tag 안에 들어 있다: "...containers.<full_id>.<full_id>-json.log".
    -- 첫 번째로 등장하는 hex 시퀀스 길이 >= 32 를 잡는다.
    local cid_full = string.match(tag, "containers%.([a-f0-9]+)")
    if cid_full and #cid_full >= 32 then
        local name = read_container_name(cid_full)
        if name then
            record["container_name"] = name
        end
    end

    return 1, timestamp, record
end
