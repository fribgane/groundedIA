package fr.groundedia.core.recherche;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Les trois modes de recherche. Valeur de configuration {@code search.mode=vector|fulltext|hybrid}.
 */
public enum Mode {
    /** Similarité vectorielle seule (pgvector, distance cosinus). */
    VECTOR,
    /** Plein texte seul (PostgreSQL, configuration « french », références d'articles reconnues à l'exact). */
    FULLTEXT,
    /** Les deux, fusionnées par Reciprocal Rank Fusion. */
    HYBRID;

    /** La clé de configuration et de l'API (« hybrid »), telle qu'elle est renvoyée dans le JSON. */
    @JsonValue
    public String cle() {
        return name().toLowerCase();
    }

    public static Mode depuis(String valeur) {
        try {
            return valueOf(valeur.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Mode de recherche inconnu : « " + valeur
                    + " » (attendu : vector, fulltext ou hybrid)");
        }
    }
}
