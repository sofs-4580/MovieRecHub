package psia.movieRecHub.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LlmAnswer {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LlmAnswer() {}

  // le o q o llm devolveu e filtra os ids válidos
  // valida o q o llm propos, ids q nao existem e repetidos sao ignorados
  // a resposta do modelo nao é de confiar, é um modelo pequeno
  public static List<Long> parseIds(String json, Set<Long> allowedIds, int maxLimit) {
    List<Long> result = new ArrayList<>();
    try {
      JsonNode root = MAPPER.readTree(json);
      int count = Math.max(1, Math.min(maxLimit, root.path("count").asInt(maxLimit)));
      for (JsonNode node : root.path("ids")) {
        if (result.size() >= count) break;
        if (!node.canConvertToLong()) continue;
        long id = node.asLong();
        if (allowedIds.contains(id) && !result.contains(id)) {
          result.add(id);
        }
      }
    } catch (Exception e) {
      return List.of(); // json invalido, usar fallback
    }
    return result;
  }
}
