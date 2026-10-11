package psia.movieRecHub.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class MovieEmbedding {

  @Id private Long movieId; // o id que o MovieService deu ao filme (sem @GeneratedValue)

  @Column(nullable = false)
  private String textHash; // hash do texto do filme; muda quando a sinopse muda

  @Column(nullable = false)
  private String model; // modelo com que o vetor foi calculado

  @Column(nullable = false)
  private byte[] vector; // os 384 floats guardados como bytes

  protected MovieEmbedding() {} // o JPA exige um construtor vazio

  // construtor com os campos + getters, como no Movie
  public Long getMovieId() {
    return movieId;
  }

  public void setMovieId(Long movieId) {
    this.movieId = movieId;
  }

  public String getTextHash() {
    return textHash;
  }

  public void setTextHash(String textHash) {
    this.textHash = textHash;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public byte[] getVector() {
    return vector;
  }

  public void setVector(byte[] vector) {
    this.vector = vector;
  }
}
