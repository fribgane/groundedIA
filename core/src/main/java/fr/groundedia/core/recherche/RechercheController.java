package fr.groundedia.core.recherche;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /search?q=…&mode=vector|fulltext|hybrid} : les meilleurs morceaux, avec score, document, article et page.
 * Sans {@code mode}, le mode configuré ({@code search.mode}) s'applique.
 */
@RestController
public class RechercheController {

    public record Reponse(String question, Mode mode, List<Resultat> resultats) {
    }

    public record Erreur(String erreur) {
    }

    private final RechercheService recherche;
    private final int limite;

    public RechercheController(RechercheService recherche, @Value("${search.limit:5}") int limite) {
        this.recherche = recherche;
        this.limite = limite;
    }

    @GetMapping("/search")
    public Reponse rechercher(@RequestParam(value = "q", required = false) String question,
                              @RequestParam(value = "mode", required = false) String mode) {
        Mode choisi = mode == null || mode.isBlank() ? recherche.modeParDefaut() : Mode.depuis(mode);
        return new Reponse(question, choisi, recherche.rechercher(question, choisi, limite));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erreur requeteInvalide(IllegalArgumentException e) {
        return new Erreur(e.getMessage());
    }

    /** Ollama injoignable, modèle absent, embeddings non calculés : l'opérateur sait quoi faire. */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Erreur serviceIndisponible(IllegalStateException e) {
        return new Erreur(e.getMessage());
    }
}
