package com.nemonicworld.backoffice.setting.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class StrictIntegerDeserializer extends StdDeserializer<Integer> {

    public StrictIntegerDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw JsonMappingException.from(parser, "정수 값을 입력해주세요.");
        }

        return parser.getIntValue();
    }
}
