package psia.movieRecHub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TODO: quando houver token do UserService, passar a ler dai, o resto nao
 * precisamos de mudar, porque já tamos a usuar o getId
 */
@Component
public class CurrentUser {

	private final Long id;

	public CurrentUser(@Value("${app.current-user-id:1}") Long id) {
		this.id = id;
	}

	public Long getId() {
		return id;
	}
}
