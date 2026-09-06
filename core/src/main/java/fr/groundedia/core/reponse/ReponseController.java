package fr.groundedia.core.reponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.groundedia.core.reponse.ReponseService.Reponse;

/**
 * {@code POST /ask {"question": "…", "salarieId": 1}} : la réponse, les citations, les morceaux utilisés, la
 * situation chargée pour la question, la latence et les jetons. {@code salarieId} est facultatif ; quand il est
 * présent, la situation de la personne est chargée par le cas d'usage ({@link SituationProvider}) et ajoutée au prompt.
 *
 * <p>Périmètre assumé du démonstrateur : il n'y a pas d'authentification, {@code salarieId} est un paramètre libre et
 * la situation est renvoyée dans la réponse. Le cloisonnement garanti ici est celui du <b>modèle</b> (il ne voit que
 * les champs autorisés du salarié demandé), pas celui de l'<b>appelant</b> : en production, l'identifiant vient de
 * l'identité authentifiée de l'appelant, jamais du corps de la requête.
 */
@RestController
public class ReponseController {

    /** Une question RH tient en une ou deux phrases ; au-delà, c'est une erreur ou une tentative d'injection. */
    static final int QUESTION_MAX = 1000;

    public record Demande(String question, Long salarieId) {
    }

    public record Erreur(String erreur) {
    }

    private final ReponseService service;
    private final ObjectProvider<SituationProvider> situations;

    public ReponseController(ReponseService service, ObjectProvider<SituationProvider> situations) {
        this.service = service;
        this.situations = situations;
    }

    @PostMapping("/ask")
    public Reponse repondre(@RequestBody(required = false) Demande demande) {
        if (demande == null || demande.question() == null || demande.question().isBlank()) {
            throw new IllegalArgumentException("Le corps attendu est {\"question\": \"…\"} (et, facultatif, \"salarieId\")");
        }
        if (demande.question().length() > QUESTION_MAX) {
            throw new IllegalArgumentException("Question trop longue (" + QUESTION_MAX + " caractères maximum)");
        }
        Situation situation = null;
        if (demande.salarieId() != null) {
            SituationProvider fournisseur = situations.getIfAvailable();
            if (fournisseur == null) {
                throw new IllegalArgumentException("Ce cas d'usage ne connaît pas de salarié : ne pas envoyer salarieId");
            }
            situation = fournisseur.situation(demande.salarieId())
                    .orElseThrow(() -> new IllegalArgumentException("Salarié inconnu : " + demande.salarieId()));
        }
        return service.repondre(demande.question().strip(), situation);
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
        return new Erreur("Le corps attendu est {\"question\": \"…\", \"salarieId\": 1} en JSON encodé en UTF-8");
    }

    /** Ollama ou l'API injoignable, modèle absent, embeddings non calculés : l'opérateur sait quoi faire. */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Erreur serviceIndisponible(IllegalStateException e) {
        return new Erreur(e.getMessage());
    }
}
