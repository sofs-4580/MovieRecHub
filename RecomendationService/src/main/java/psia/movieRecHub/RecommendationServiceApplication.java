package psia.movieRecHub;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import psia.movieRecHub.client.MovieServiceClient;

@SpringBootApplication
@EnableScheduling
public class RecommendationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(RecommendationServiceApplication.class, args);
  }

  @Bean
  ApplicationRunner testMovies(MovieServiceClient client) {
    return args -> System.out.println("FILMES LIDOS: " + client.movies().size());
  }
}
