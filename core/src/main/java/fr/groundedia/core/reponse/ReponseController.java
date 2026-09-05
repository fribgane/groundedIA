package fr.groundedia.core.reponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.groundedia.core.reponse.ReponseService.Reponse;

/**
 * {@code POST /ask {"question": "…"}} : la réponse, les citations, les morceaux utilisés, la latence et les jetons.
 */
@RestController
public class ReponseController {

    /** Une question RH tient en une ou deux phrases ; au-delà, c'est une erreur ou une tentative d'injection. */
    static final int QUESTION_MAX = 1000;

    public record Demande(String question) {
    }

    public record Erreur(String erreur) {
    }

    private final ReponseService service;

    public ReponseController(ReponseService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    public Reponse repondre(@RequestBody(required = false) Demande demande) {
        if (demande == null || demande.question() == null || demande.question().isBlank()) {
            throw new IllegalArgumentException("Le corps attendu est {\"question\": \"…\"}");
        }
        if (demande.question().length() > QUESTION_MAX) {
            throw new IllegalArgumentException("Question trop longue (" + QUESTION_MAX + " caractères maximum)");
        }
        return service.repondre(demande.question().strip());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erreur requeteInvalide(IllegalArgumentException e) {
        return new Erreur(e.getMessage());
    }

    /** JSON illisible (guillemets, encodage) : même forme d'erreur que les autres 400, avec le rappel du format. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erreur corpsIllisible(HttpMessageNotReadableException e) {
        return new Erreur("Le corps attendu est {\"question\": \"…\"} en JSON encodé en UTF-8");
    }

    /** Ollama ou l'API injoignable, modèle absent, embeddings non calculés : l'opérateur sait quoi faire. */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Erreur serviceIndisponible(IllegalStateException e) {
        return new Erreur(e.getMessage());
    }
}
