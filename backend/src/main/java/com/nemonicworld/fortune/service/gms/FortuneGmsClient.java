package com.nemonicworld.fortune.service.gms;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 만세력 결과와 프롬프트 템플릿을 이용해 운세 문구를 생성하는 경계입니다.
 */
public interface FortuneGmsClient {

    FortuneGmsResult generate(String promptTemplate, JsonNode saju);
}
