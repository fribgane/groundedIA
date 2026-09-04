package fr.groundedia.core.ingestion;

/**
 * Le texte brut d'une page d'un document, avec son numéro (à partir de 1).
 */
public record PageText(int numero, String texte) {
}
