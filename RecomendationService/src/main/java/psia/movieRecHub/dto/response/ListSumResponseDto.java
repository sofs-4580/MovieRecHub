package psia.movieRecHub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListSumResponseDto(Long id, String name, String type) {}
