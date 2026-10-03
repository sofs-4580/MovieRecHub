package psia.movieRecHub.controller;

import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import psia.movieRecHub.exceptions.DuplicateListException;
import psia.movieRecHub.exceptions.DuplicateReviewException;
import psia.movieRecHub.exceptions.ListAccessDeniedException;
import psia.movieRecHub.exceptions.ListItemNotFoundException;
import psia.movieRecHub.exceptions.ListNotFoundException;
import psia.movieRecHub.exceptions.MovieNotFoundException;
import psia.movieRecHub.exceptions.ReviewNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	// Catch a specific custom exception
	@ExceptionHandler(MovieNotFoundException.class)
	public ProblemDetail handleMovieNotFoundException(MovieNotFoundException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problemDetail.setDetail("Movie not found");
		problemDetail.setProperty("message", ex.getMessage());
		problemDetail.setProperty("errorCode", "MOVIE_ERR_001");

		return problemDetail;
	}

	@ExceptionHandler(ReviewNotFoundException.class)
	public ProblemDetail handleReviewNotFoundException(ReviewNotFoundException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problemDetail.setDetail("Review not found");
		problemDetail.setProperty("message", ex.getMessage());
		problemDetail.setProperty("errorCode", "REVIEW_ERR_001");

		return problemDetail;
	}

	@ExceptionHandler(DuplicateReviewException.class)
	public ProblemDetail handleDuplicateReviewException(DuplicateReviewException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problemDetail.setDetail("Duplicate review");
		problemDetail.setProperty("message", ex.getMessage());
		problemDetail.setProperty("errorCode", "REVIEW_ERR_002");

		return problemDetail;
	}

	@ExceptionHandler({ ListNotFoundException.class, ListItemNotFoundException.class })
	public ProblemDetail handleListNotFound(RuntimeException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problemDetail.setDetail("List or list item not found");
		problemDetail.setProperty("message", ex.getMessage());
		problemDetail.setProperty("errorCode", "LIST_ERR_001");
		return problemDetail;
	}

	@ExceptionHandler(ListAccessDeniedException.class)
	public ProblemDetail handleListAccessDenied(ListAccessDeniedException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		problemDetail.setDetail("Forbidden");
		problemDetail.setProperty("message", ex.getMessage());
		problemDetail.setProperty("errorCode", "LIST_ERR_002");
		return problemDetail;
	}

	@ExceptionHandler(DuplicateListException.class)
	public ProblemDetail handleDuplicateList(DuplicateListException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problemDetail.setDetail("Duplicate list or list item");
		problemDetail.setProperty("message", ex.getMessage());
		problemDetail.setProperty("errorCode", "LIST_ERR_003");
		return problemDetail;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problemDetail.setDetail("Validation failed");
		problemDetail.setProperty("message", ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining("; ")));
		return problemDetail;
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problemDetail.setDetail("Missing header");
		problemDetail.setProperty("message", ex.getMessage());
		return problemDetail;
	}

	// Corrida entre dois pedidos que passam a verificacao e batem na constraint
	// unique da BD
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problemDetail.setDetail("Conflict with existing data");
		problemDetail.setProperty("errorCode", "DB_ERR_001");
		return problemDetail;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problemDetail.setDetail("Invalid request");
		problemDetail.setProperty("message", ex.getMessage());
		return problemDetail;
	}

	// valor com tipo errado
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problemDetail.setDetail("Invalid parameter");
		problemDetail.setProperty("message", "Invalid value for '" + ex.getName() + "'");
		return problemDetail;
	}

	// parametro em falta
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problemDetail.setDetail("Missing parameter");
		problemDetail.setProperty("message", ex.getMessage());
		return problemDetail;
	}

	// Catch generic exceptions as a fallback
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleGenericException(Exception ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}
}
