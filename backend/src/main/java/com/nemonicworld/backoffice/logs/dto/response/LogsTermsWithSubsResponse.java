package com.nemonicworld.backoffice.logs.dto.response;

import java.util.List;

public record LogsTermsWithSubsResponse(List<LogsTermsWithSubsBucketResponse> buckets) {
}
