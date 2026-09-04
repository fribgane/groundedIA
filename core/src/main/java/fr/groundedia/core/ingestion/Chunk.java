package fr.groundedia.core.ingestion;

/**
 * Un morceau de document prêt à être cité : son texte et sa provenance (document, chapitre ou article, page).
 * Les champs portent le nom des colonnes de la table {@code chunk}.
 */
public record Chunk(String document, String chapitre, int page, String texte) {
}
