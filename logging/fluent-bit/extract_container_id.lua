function extract_container_id(tag, timestamp, record)
    local cid = string.match(tag, "([a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9])")
    if cid then
        record["container_id"] = cid
    end
    record["log_tag"] = tag
    return 1, timestamp, record
end
