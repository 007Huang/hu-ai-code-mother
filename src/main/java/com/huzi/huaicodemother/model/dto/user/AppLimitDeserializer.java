package com.huzi.huaicodemother.model.dto.user;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** 配额只接受 JSON 整数，避免 Jackson 把小数截断或把字符串隐式转成数字。 */
public class AppLimitDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        }
        return parser.getIntValue();
    }
}
