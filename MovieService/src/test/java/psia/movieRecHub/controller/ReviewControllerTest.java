package psia.movieRecHub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import psia.movieRecHub.dto.request.ReviewRequestDto;
import psia.movieRecHub.dto.response.ReviewResponseDto;
import psia.movieRecHub.exceptions.DuplicateReviewException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.exceptions.ReviewNotFoundException;
import psia.movieRecHub.service.ReviewService;

// new ReviewResponseDto(id, userId, movieId, rating, comment, createdAt)
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private ReviewService reviewService;

//POST /api/reviews

	@Test
	void createReview() throws Exception {
		ReviewResponseDto saved = new ReviewResponseDto(10L, 2L, 1L, 4, "bom", LocalDateTime.of(2026, 1, 15, 21, 30));
		when(reviewService.createReview(any(ReviewRequestDto.class))).thenReturn(saved);

		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\": 2, \"movieId\": 1, \"rating\": 4, \"comment\": \"bom\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(10))
				.andExpect(jsonPath("$.userId").value(2)).andExpect(jsonPath("$.movieId").value(1))
				.andExpect(jsonPath("$.rating").value(4)).andExpect(jsonPath("$.comment").value("bom"))
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void createReviewWithBlankComment() throws Exception {
		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\": 2, \"movieId\": 1, \"rating\": 4, \"comment\": \"   \"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Validation failed"));

		// nem chega ao service
		verifyNoInteractions(reviewService);
	}

	@Test
	void createReviewWithoutComment() throws Exception {
		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\": 2, \"movieId\": 1, \"rating\": 4}")).andExpect(status().isBadRequest());

		verifyNoInteractions(reviewService);
	}

	@Test
	void createReviewWithoutUserId() throws Exception {
		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"movieId\": 1, \"rating\": 4, \"comment\": \"bom\"}")).andExpect(status().isBadRequest());

		verifyNoInteractions(reviewService);
	}

	@Test
	void createReviewWithoutMovieId() throws Exception {
		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\": 2, \"rating\": 4, \"comment\": \"bom\"}")).andExpect(status().isBadRequest());

		verifyNoInteractions(reviewService);
	}

	@Test
	void createReviewNonExistingMovie() throws Exception {
		when(reviewService.createReview(any(ReviewRequestDto.class))).thenThrow(new MovieNotFoundException(99L));

		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\": 2, \"movieId\": 99, \"rating\": 4, \"comment\": \"bom\"}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("MOVIE_ERR_001"));
	}

	@Test
	void createReviewTwice() throws Exception {
		when(reviewService.createReview(any(ReviewRequestDto.class))).thenThrow(new DuplicateReviewException(2L, 1L));

		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\": 2, \"movieId\": 1, \"rating\": 5, \"comment\": \"outra vez\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Duplicate review"))
				.andExpect(jsonPath("$.errorCode").value("REVIEW_ERR_002"));
	}

//GET

	@Test
	void getReviewsByMovie() throws Exception {
		ReviewResponseDto rev1 = new ReviewResponseDto(10L, 2L, 1L, 4, "bom", LocalDateTime.of(2026, 1, 15, 21, 30));
		ReviewResponseDto rev2 = new ReviewResponseDto(11L, 3L, 1L, 5, "otimo", LocalDateTime.of(2026, 1, 14, 21, 30));
		when(reviewService.getReviewsByMovie(1L)).thenReturn(List.of(rev1, rev2));

		mockMvc.perform(get("/api/reviews/movie/1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(10))
				.andExpect(jsonPath("$[0].rating").value(4)).andExpect(jsonPath("$[1].id").value(11));
	}

	@Test
	void getReviewsMovieWithoutReviews() throws Exception {
		when(reviewService.getReviewsByMovie(1L)).thenReturn(List.of());

		mockMvc.perform(get("/api/reviews/movie/1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void getReviewsByUser() throws Exception {
		ReviewResponseDto rev1 = new ReviewResponseDto(10L, 2L, 1L, 4, "bom", LocalDateTime.of(2026, 1, 15, 21, 30));
		when(reviewService.getReviewsByUser(2L)).thenReturn(List.of(rev1));

		mockMvc.perform(get("/api/reviews/user/2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].userId").value(2));
	}

//DELETE /api/reviews/{id}

	@Test
	void deleteReview() throws Exception {
		mockMvc.perform(delete("/api/reviews/10")).andExpect(status().isNoContent());

		verify(reviewService).deleteReview(10L);
	}

	@Test
	void deleteReviewThatDoesNotExist() throws Exception {
		doThrow(new ReviewNotFoundException(99L)).when(reviewService).deleteReview(99L);

		mockMvc.perform(delete("/api/reviews/99")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Review not found"))
				.andExpect(jsonPath("$.errorCode").value("REVIEW_ERR_001"));
	}

//pedidos invalidos - 400

	@Test
	void createReviewRatingOutOfRange() throws Exception {
		mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\": 2, \"movieId\": 1, \"rating\": 6, \"comment\": \"bom\"}"))
				.andExpect(status().isBadRequest());
	}
}