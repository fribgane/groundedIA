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
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("La question est vide");
        }
        return switch (mode) {
            case VECTOR -> vectorielle(question, limite);
            case FULLTEXT -> pleinTexte(question, limite);
            case HYBRID -> Rrf.fusionner(
                    List.of(vectorielle(question, candidatsVectoriels), pleinTexte(question, candidatsPleinTexte)),
                    limite);
        };
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
