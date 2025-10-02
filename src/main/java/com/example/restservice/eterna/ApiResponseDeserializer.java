package com.example.restservice.eterna;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class ApiResponseDeserializer extends JsonDeserializer<ApiResult<?>> {
  @Override
  public ApiResult<?> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
    ObjectCodec codec = p.getCodec();
    JsonNode node = codec.readTree(p);

    if (node.has("type") && node.has("message")) {
      return codec.treeToValue(node, ApiError.class);
    } else if (node.has("type")) {
      return codec.treeToValue(node, ApiSuccess.class);
    } else {
      throw new JsonMappingException(p, "Unknown response type: " + node.toString());
    }
  }
}
