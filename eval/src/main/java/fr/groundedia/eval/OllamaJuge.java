package fr.groundedia.eval;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Le juge, sur un modèle local via Ollama ({@code POST /api/chat}), température 0 et graine fixe : deux passages sur
 * la même paire de réponses donnent le même verdict. La consigne est volontairement stricte et binaire, et tranche
 * les cas ambigus (chiffres en lettres, unités, plusieurs valeurs) plutôt que de laisser le modèle décider.
 */
public class OllamaJuge implements Juge {

    static final String CONSIGNE = """
            Tu es un correcteur strict. On te donne une QUESTION, la RÉPONSE ATTENDUE (rédigée par un expert RH à \
            partir des documents de référence) et la RÉPONSE OBTENUE (produite par le système évalué). Tu compares \
            uniquement les deux réponses, sans aucune connaissance extérieure.

            La réponse obtenue est CORRECTE si et seulement si elle contient chaque information de la réponse attendue \
            (chiffres, durées, conditions, conclusion oui/non) sans en contredire aucune. Tu ne juges que cela.
            Ne comptent jamais : l'ordre et la formulation, les références entre crochets, un chiffre écrit en lettres \
            (« quatre » = « 4 »), une virgule ou un point décimal (« 2,5 » = « 2.5 »), une unité absente ou ajoutée \
            quand la réponse attendue n'en précise pas ou en précise une (« 4 jours » vaut « 4 jours ouvrés »), et les \
            précisions, explications ou informations supplémentaires, même nombreuses ou non demandées, dès lors \
            qu'elles ne contredisent aucune information attendue ; la mention qu'un détail ne figure pas dans les \
            documents est une précision comme une autre. Une information supplémentaire ne rend jamais une réponse \
            INCORRECTE.
            Elle est INCORRECTE si elle donne un autre chiffre ou une autre conclusion, si elle omet une information \
            de la réponse attendue, si elle donne plusieurs valeurs différentes pour la même information, si elle \
            emploie une unité différente de celle que la réponse attendue précise (« jours ouvrables » quand « jours \
            ouvrés » est attendu), ou si elle refuse entièrement de répondre.
            Exemples : attendue « 5 jours d'absence, non rémunérés » et obtenue « cinq (5) jours ouvrés, non rémunérés, \
            pour la maladie d'un enfant de moins de seize ans [source] » → CORRECT : chaque information attendue est \
            présente, le reste est une précision. Attendue « 4 jours ouvrés » et obtenue « six (6) jours ouvrés » → \
            INCORRECT : autre chiffre. Attendue « 2,5 jours ouvrables par mois ; 30 jours au maximum » et obtenue \
            « 30 jours ouvrables au maximum » → INCORRECT : le taux mensuel attendu est omis.
            Réponds sur une seule ligne, exactement sous la forme « CORRECT : <raison en une phrase> » ou \
            « INCORRECT : <raison en une phrase> ».
            """;

    private final RestClient http;
    private final String modele;

    public OllamaJuge(String baseUrl, String modele) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(5));
        fabrique.setReadTimeout(Duration.ofMinutes(10));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrique).build();
        this.modele = modele;
    }

    @Override
    public String modele() {
        return modele;
    }

    /** Échoue tout de suite, avec la marche à suivre, si Ollama est arrêté ou si le modèle juge n'est pas téléchargé. */
    public void verifierDisponible() {
        Modeles modeles;
        try {
            modeles = http.get().uri("/api/tags").retrieve().body(Modeles.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Ollama injoignable pour le juge (" + e.getMessage() + ") : démarrer Ollama", e);
        }
        boolean present = modeles != null && modeles.models() != null && modeles.models().stream()
                .anyMatch(m -> m.name() != null && (m.name().equals(modele) || m.name().equals(modele + ":latest")));
        if (!present) {
            throw new IllegalStateException("Le modèle juge « " + modele + " » n'est pas installé : ollama pull " + modele
                    + " (ou choisir un autre modèle avec -Deval.juge.model)");
        }
    }

    @Override
    public Verdict juger(String question, String reponseAttendue, String reponseObtenue) {
        // chaque texte tient sur une ligne, entre guillemets : la réponse obtenue ne peut pas imiter un autre bloc
        String message = "QUESTION : « " + uneLigne(question) + " »\n\nRÉPONSE ATTENDUE : « " + uneLigne(reponseAttendue)
                + " »\n\nRÉPONSE OBTENUE : « " + uneLigne(reponseObtenue) + " »";
        Map<String, Object> corps = Map.of(
                "model", modele,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", CONSIGNE),
                        Map.of("role", "user", "content", message)),
                "options", Map.of("temperature", 0, "seed", 42, "num_ctx", 4096, "num_predict", 200));
        ReponseOllama r = http.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(corps)
                .retrieve().body(ReponseOllama.class);
        if (r == null || r.message() == null) {
            return new Verdict(Verdict.Decision.INDETERMINE, "réponse vide du juge", 0);
        }
        return Verdict.analyser(r.message().content(), r.promptEvalCount() + r.evalCount());
    }

    private static String uneLigne(String texte) {
        return texte == null ? "" : texte.replaceAll("\\s+", " ").strip();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReponseOllama(Message message, @JsonProperty("prompt_eval_count") int promptEvalCount,
                         @JsonProperty("eval_count") int evalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Modeles(List<Modele> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Modele(String name) {
    }
}
