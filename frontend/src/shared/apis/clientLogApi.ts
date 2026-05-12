import { api } from "@/shared/libs";
import type {
  ApiResponse,
  ClientLogIngestRequest,
  ClientLogIngestResponse,
} from "@/shared/types";

import { apiUnwrap } from "@/shared/utils";

// POST /logs/client — 클라이언트 로그 이벤트 수집
export const postClientLogIngest = (payload: ClientLogIngestRequest) =>
  apiUnwrap(
    api.post<ApiResponse<ClientLogIngestResponse>>("logs/client", payload),
  );
