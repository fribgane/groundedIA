package fr.groundedia.core.recherche;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.groundedia.core.embedding.EmbeddingClient;

/**
 * Recherche de morceaux dans les documents ingérés, selon le mode configuré ({@code search.mode}) ou demandé.
 *
 * <p>Hybride : la recherche vectorielle fournit ses {@code search.candidats-vectoriels} meilleurs morceaux, la
 * recherche plein texte ses {@code search.candidats-plein-texte} meilleurs, et les deux classements sont fusionnés
 * par Reciprocal Rank Fusion. Les profondeurs diffèrent à dessein : la liste vectorielle reste pertinente loin
 * dans le classement (une bonne réponse peut y être 19e), tandis qu'une liste plein texte en « ou » n'est fiable
 * qu'en tête ; au-delà de quelques résultats, elle n'apporte que des morceaux partageant un mot avec la question,
 * dont la présence dans les deux listes ferait remonter du bruit.
 */
@Service
public class RechercheService {

    /**
     * Le résultat de la recherche hybride, avec ce qu'il faut pour décider si le corpus couvre la question :
     * la meilleure similarité cosinus, la meilleure densité lexicale (dans [0, 1[ : n/(n+10) pour n occurrences
     * des lemmes de la question dans le meilleur morceau), et si un morceau porte en titre l'article que la question
     * cite (un article seulement cité par d'autres morceaux ne compte pas : le corpus ne le contient pas).
     */
    public record RechercheDetaillee(List<Resultat> morceaux, double similariteMax, double lexicalMax,
                                     boolean referenceTrouvee) {

        /**
         * Confiance dans [0, 1] : le corpus couvre la question si au moins une des deux recherches y répond avec
         * force. Article cité présent en titre = 1 (ses morceaux sont fournis au modèle, dont la consigne garantit
         * alors seule le refus) ; sinon le maximum de la similarité cosinus et de la densité lexicale.
         */
        public double confiance() {
            return referenceTrouvee ? 1.0 : Math.max(similariteMax, lexicalMax);
        }
    }

    private final EmbeddingClient embeddings;
    private final RechercheRepository depot;
    private final Mode modeParDefaut;
    private final int candidatsVectoriels;
    private final int candidatsPleinTexte;

    public RechercheService(EmbeddingClient embeddings, RechercheRepository depot,
                            @Value("${search.mode:hybrid}") String mode,
                            @Value("${search.candidats-vectoriels:20}") int candidatsVectoriels,
                            @Value("${search.candidats-plein-texte:5}") int candidatsPleinTexte) {
        this.embeddings = embeddings;
        this.depot = depot;
        this.modeParDefaut = Mode.depuis(mode);
        this.candidatsVectoriels = candidatsVectoriels;
        this.candidatsPleinTexte = candidatsPleinTexte;
    }

    public Mode modeParDefaut() {
        return modeParDefaut;
    }

    public int candidatsVectoriels() {
        return candidatsVectoriels;
    }

    public int candidatsPleinTexte() {
        return candidatsPleinTexte;
    }

    public List<Resultat> rechercher(String question, Mode mode, int limite) {
        verifier(question);
        return switch (mode) {
            case VECTOR -> vectorielle(question, limite);
            case FULLTEXT -> pleinTexte(question, limite);
            case HYBRID -> hybride(question, limite).morceaux();
        };
    }

    /** La recherche hybride, avec les signaux de confiance dont la génération de réponse a besoin. */
    public RechercheDetaillee hybride(String question, int limite) {
        verifier(question);
        List<Resultat> vectoriels = vectorielle(question, candidatsVectoriels);
        String reference = ReferencesArticles.expressionPostgres(question);
        List<Resultat> lexicaux = depot.pleinTexte(question, reference, candidatsPleinTexte);
        double similariteMax = vectoriels.isEmpty() ? 0 : vectoriels.getFirst().score();
        // score lexical = densité dans [0, 1[ + bonus entier (2 : titre, 1 : citation) quand une référence est cherchée
        double scoreLexical = lexicaux.isEmpty() ? 0 : lexicaux.getFirst().score();
        boolean referenceTrouvee = scoreLexical >= 2;
        double lexicalMax = scoreLexical % 1;
        return new RechercheDetaillee(Rrf.fusionner(List.of(vectoriels, lexicaux), limite),
                similariteMax, lexicalMax, referenceTrouvee);
    }

    private static void verifier(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("La question est vide");
        }
    }

    private List<Resultat> vectorielle(String question, int limite) {
        List<Resultat> resultats = depot.vectorielle(embeddings.embed(question), limite);
        if (resultats.isEmpty() && depot.compterEmbeddings() == 0) {
            throw new IllegalStateException("Aucun morceau n'a d'embedding : lancer la commande --embed avant "
                    + "une recherche vectorielle ou hybride");
        }
        return resultats;
    }

    private List<Resultat> pleinTexte(String question, int limite) {
        return depot.pleinTexte(question, ReferencesArticles.expressionPostgres(question), limite);
    }
}
