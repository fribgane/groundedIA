package fr.groundedia.core.llm;

import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Génération en local par Ollama ({@code POST /api/chat}) : gratuit, hors ligne, les documents ne quittent pas le poste.
 * Actif quand {@code llm.provider=local} (valeur par défaut).
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "local", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    /** Fenêtre de contexte demandée : la consigne et cinq extraits font environ 2 500 jetons. */
    static final int CONTEXTE = 8192;

    /** Longueur maximale de la réponse, pour borner la latence sur un processeur. */
    static final int REPONSE_MAX = 400;

    record Message(String role, String content) {
    }

    record Requete(String model, List<Message> messages, boolean stream, Map<String, Object> options) {
    }

    record Reponse(Message message, Integer prompt_eval_count, Integer eval_count) {
    }

    private final RestClient rest;
    private final String modele;
    private final String baseUrl;

    public OllamaLlmClient(RestClient.Builder builder,
                           @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
                           @Value("${llm.local.model:qwen2.5:3b}") String modele) {
        this.rest = builder.baseUrl(baseUrl).build();
        this.modele = modele;
        this.baseUrl = baseUrl;
    }

    @Override
    public String fournisseur() {
        return "local";
    }

    @Override
    public String modele() {
        return modele;
    }

    @Override
    public Generation generer(String consigneSysteme, String message) {
        // température 0 et graine fixe : la même question donne la même réponse, ce que la démonstration exige
        Requete requete = new Requete(modele,
                List.of(new Message("system", consigneSysteme), new Message("user", message)), false,
                Map.of("temperature", 0, "seed", 42, "num_ctx", CONTEXTE, "num_predict", REPONSE_MAX));
        Reponse reponse;
        try {
            reponse = rest.post().uri("/api/chat").body(requete).retrieve().body(Reponse.class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                throw new IllegalStateException("Ollama n'a pas répondu dans le délai spring.http.client.read-timeout ("
                        + baseUrl + ")", e);
            }
            throw new IllegalStateException("Ollama injoignable sur " + baseUrl + " (lancer « ollama serve ») : "
                    + e.getMessage(), e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("Modèle « " + modele + " » absent d'Ollama : lancer « ollama pull "
                    + modele + " »", e);
        } catch (RestClientResponseException e) {
            // ex. 500 « model requires more system memory » : l'opérateur doit voir le message d'Ollama
            throw new IllegalStateException("Ollama a répondu " + e.getStatusCode() + " pour le modèle " + modele
                    + " : " + ApiLlmClient.extrait(e.getResponseBodyAsString()), e);
        }
        if (reponse == null || reponse.message() == null || reponse.message().content() == null) {
            throw new IllegalStateException("Réponse inattendue d'Ollama pour le modèle " + modele);
        }
        return new Generation(reponse.message().content(),
                reponse.prompt_eval_count() == null ? 0 : reponse.prompt_eval_count(),
                reponse.eval_count() == null ? 0 : reponse.eval_count());
    }
}
