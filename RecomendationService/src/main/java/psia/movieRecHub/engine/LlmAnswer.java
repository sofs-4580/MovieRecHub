package psia.movieRecHub.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LlmAnswer {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LlmAnswer() {}

  public record Pick(long movieId, String reason) {}

  /**
   * Lê o JSON do modelo. A saída do modelo é NÃO confiável, por isso usamos JsonNode e validamos
   * tudo: o id tem de existir no catálogo, sem repetidos, com limite.
   */
  public static List<Pick> parsePicks(String json, Set<Long> allowedIds, int limit) {
    List<Pick> result = new ArrayList<>();
    try {
      JsonNode picks = MAPPER.readTree(json).path("picks");
      if (!picks.isArray()) return result;
      Set<Long> seen = new LinkedHashSet<>();
      for (JsonNode p : picks) {
        if (result.size() >= limit) break;
        JsonNode idNode = p.path("id");
        if (!idNode.canConvertToLong()) continue;
        long id = idNode.asLong();
        if (!allowedIds.contains(id) || !seen.add(id)) continue;
        String reason = LlmPrompts.clean(p.path("reason").asText(""), 140);
        result.add(new Pick(id, reason.isBlank() ? "Suggested for you" : reason));
      }
    } catch (Exception e) {
      // JSON inválido: devolve lista vazia, o chamador usa o fallback
    }
    return result;
  }
}
