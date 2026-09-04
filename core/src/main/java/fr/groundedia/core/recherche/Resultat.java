package fr.groundedia.core.recherche;

/**
 * Un morceau retourné par la recherche, avec son score et sa provenance pour la citation.
 * Le score dépend du mode : similarité cosinus (vector), rang plein texte avec bonus de référence (fulltext),
 * score de fusion RRF (hybrid).
 */
public record Resultat(long id, double score, String document, String chapitre, int page, String texte) {

    Resultat avecScore(double nouveauScore) {
        return new Resultat(id, nouveauScore, document, chapitre, page, texte);
    }
}
