package psia.movieRecHub.client;

/**
 * O MovieService respondeu com erro (ou nao respondeu) - mensagem e para
 * mostrar ao utilizador
 */
public class MovieServiceException extends RuntimeException {
	public MovieServiceException(String message) {
		super(message);
	}
}
