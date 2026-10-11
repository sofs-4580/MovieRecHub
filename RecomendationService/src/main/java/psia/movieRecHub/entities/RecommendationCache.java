package psia.movieRecHub.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "recommendation_cache")
public class RecommendationCache {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode items;

  @Column(nullable = false, length = 30)
  private String source;

  @Column(length = 200)
  private String model;

  @Column(name = "input_hash", nullable = false, length = 64)
  private String inputHash;

  @Column(name = "generated_at", nullable = false, columnDefinition = "timestamptz")
  private Instant generatedAt;

  protected RecommendationCache() {}

  public RecommendationCache(Long userId) {
    this.userId = userId;
  }

  @PrePersist
  void onCreate() {
    generatedAt = Instant.now();
  }

  public Long getUserId() {
    return userId;
  }

  public JsonNode getItems() {
    return items;
  }

  public void setItems(JsonNode items) {
    this.items = items;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getInputHash() {
    return inputHash;
  }

  public void setInputHash(String inputHash) {
    this.inputHash = inputHash;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }
}
