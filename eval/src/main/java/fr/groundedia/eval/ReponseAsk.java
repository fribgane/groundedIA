package fr.groundedia.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** La réponse de {@code POST /ask}, telle que l'application la renvoie ; seuls les champs utiles au harnais sont lus. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReponseAsk(String reponse, boolean refus, boolean suspecte, List<String> citations,
                         List<Morceau> morceaux, long latenceMs, Tokens tokens, String fournisseur, String modele,
                         Confiance confiance) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Morceau(String document, String chapitre, int page) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tokens(int prompt, int reponse, int total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Confiance(double similarite, double lexical, boolean referenceTrouvee,
                            double seuilSimilarite, double seuilLexical, boolean couverte) {
    }

    /**
     * Un refus décidé par le code, avant tout appel au modèle : la garde a jugé la question non couverte
     * ({@code confiance.couverte} faux). À défaut de ce signal, un refus sans aucun jeton consommé.
     */
    public boolean refusParLaGarde() {
        if (!refus) {
            return false;
        }
        return confiance != null ? !confiance.couverte() : tokens == null || tokens.total() == 0;
    }

    /** Le modèle a été appelé : la latence et les jetons mesurent alors une génération, pas un refus immédiat. */
    public boolean modeleAppele() {
        return !refusParLaGarde();
    }
}
