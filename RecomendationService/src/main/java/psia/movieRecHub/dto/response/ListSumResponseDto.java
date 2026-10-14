package psia.movieRecHub.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListSumResponseDto(Long id, String name, String type) {}
