package fr.groundedia.core.embedding;

import java.net.http.HttpTimeoutException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Embeddings calculés en local par Ollama, via son API HTTP ({@code POST /api/embed}).
 * Un seul appel HTTP par lot de textes ; Ollama renvoie un vecteur par texte, dans l'ordre.
 */
@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    /** Corps de la requête : le modèle et les textes à représenter. */
    record Requete(String model, List<String> input) {
    }

    /** Corps de la réponse : un vecteur par texte (les autres champs d'Ollama sont ignorés). */
    record Reponse(List<float[]> embeddings) {
    }

    private final RestClient rest;
    private final String modele;
    private final String baseUrl;

    public OllamaEmbeddingClient(RestClient.Builder builder,
                                 @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
                                 @Value("${ollama.embedding-model:bge-m3}") String modele) {
        // Le délai de lecture (un lot peut prendre des dizaines de secondes sur un processeur) est réglé par
        // spring.http.client.read-timeout dans application.yml, sur le RestClient.Builder fourni par Spring Boot.
        this.rest = builder.baseUrl(baseUrl).build();
        this.modele = modele;
        this.baseUrl = baseUrl;
    }

    @Override
    public String modele() {
        return modele;
    }

    @Override
    public List<float[]> embed(List<String> textes) {
        if (textes.isEmpty()) {
            return List.of();
        }
        Reponse reponse;
        try {
            reponse = rest.post()
                    .uri("/api/embed")
                    .body(new Requete(modele, textes))
                    .retrieve()
                    .body(Reponse.class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                throw new IllegalStateException("Ollama n'a pas répondu dans le délai spring.http.client.read-timeout ("
                        + baseUrl + ") : lot trop gros pour ce processeur, ou modèle en cours de chargement", e);
            }
            throw new IllegalStateException("Ollama injoignable sur " + baseUrl + " (lancer « ollama serve », "
                    + "puis « ollama pull " + modele + " ») : " + e.getMessage(), e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("Modèle « " + modele + " » absent d'Ollama : lancer « ollama pull "
                    + modele + " »", e);
        }
        if (reponse == null || reponse.embeddings() == null || reponse.embeddings().size() != textes.size()) {
            throw new IllegalStateException("Réponse inattendue d'Ollama pour le modèle " + modele + " : "
                    + (reponse == null || reponse.embeddings() == null ? 0 : reponse.embeddings().size())
                    + " vecteur(s) pour " + textes.size() + " texte(s)");
        }
        return reponse.embeddings();
    }
}
