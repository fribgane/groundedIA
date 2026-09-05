package fr.groundedia.core.llm;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Génération par une API distante compatible OpenAI ({@code POST {base-url}/chat/completions}) : Mistral, OpenAI,
 * Groq… selon {@code llm.api.base-url} et {@code llm.api.model}. Actif quand {@code llm.provider=api}.
 *
 * <p>La clé est lue dans la variable d'environnement {@code LLM_API_KEY}, jamais dans le code ni dans un fichier
 * versionné ; son absence fait échouer le démarrage avec un message explicite.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "api")
public class ApiLlmClient implements LlmClient {

    record Message(String role, String content) {
    }

    record Requete(String model, List<Message> messages, double temperature) {
    }

    record Choix(Message message) {
    }

    record Usage(Integer prompt_tokens, Integer completion_tokens) {
    }

    record Reponse(List<Choix> choices, Usage usage) {
    }

    private final RestClient rest;
    private final String modele;
    private final String baseUrl;

    public ApiLlmClient(RestClient.Builder builder,
                        @Value("${llm.api.base-url:https://api.mistral.ai/v1}") String baseUrl,
                        @Value("${llm.api.model:mistral-small-latest}") String modele,
                        @Value("${LLM_API_KEY:}") String cle) {
        if (cle == null || cle.isBlank()) {
            throw new IllegalStateException("llm.provider=api mais la clé est absente : définir la variable "
                    + "d'environnement LLM_API_KEY (jamais dans un fichier versionné)");
        }
        this.rest = builder.baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cle).build();
        this.modele = modele;
        this.baseUrl = baseUrl;
    }

    @Override
    public String fournisseur() {
        return "api";
    }

    @Override
    public String modele() {
        return modele;
    }

    @Override
    public Generation generer(String consigneSysteme, String message) {
        Requete requete = new Requete(modele,
                List.of(new Message("system", consigneSysteme), new Message("user", message)), 0);
        Reponse reponse;
        try {
            reponse = rest.post().uri("/chat/completions").body(requete).retrieve().body(Reponse.class);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("API distante injoignable sur " + baseUrl + " : " + e.getMessage(), e);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException("Clé LLM_API_KEY refusée par " + baseUrl + " (" + e.getStatusCode() + ")", e);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("L'API distante (" + baseUrl + ") a répondu " + e.getStatusCode()
                    + " pour le modèle " + modele + " : " + extrait(e.getResponseBodyAsString()), e);
        }
        if (reponse == null || reponse.choices() == null || reponse.choices().isEmpty()
                || reponse.choices().getFirst().message() == null
                || reponse.choices().getFirst().message().content() == null) {
            throw new IllegalStateException("Réponse inattendue de l'API distante pour le modèle " + modele);
        }
        Usage usage = reponse.usage() == null ? new Usage(0, 0) : reponse.usage();
        return new Generation(reponse.choices().getFirst().message().content(),
                usage.prompt_tokens() == null ? 0 : usage.prompt_tokens(),
                usage.completion_tokens() == null ? 0 : usage.completion_tokens());
    }

    /** Le début du corps d'erreur du fournisseur, pour l'opérateur (il ne contient pas la clé). */
    static String extrait(String corps) {
        return corps == null ? "" : corps.length() > 200 ? corps.substring(0, 200) + "…" : corps;
    }
}
