package fr.groundedia.core.recherche;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import fr.groundedia.core.ingestion.ChunkRepository;

/**
 * Les deux requêtes de recherche sur la table {@code chunk}, écrites en Java avec des paramètres liés.
 */
@Repository
public class RechercheRepository {

    private final JdbcClient jdbc;

    public RechercheRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int compterEmbeddings() {
        return jdbc.sql("SELECT count(*) FROM chunk WHERE embedding IS NOT NULL").query(Integer.class).single();
    }

    /** Similarité cosinus entre le vecteur de la question et celui de chaque morceau (opérateur pgvector « <=> »). */
    public List<Resultat> vectorielle(float[] question, int limite) {
        return jdbc.sql("""
                SELECT id, 1 - (embedding <=> CAST(:question AS vector)) AS score, document, chapitre, page, texte
                FROM chunk
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> CAST(:question AS vector)
                LIMIT :limite
                """)
                .param("question", ChunkRepository.litteral(question))
                .param("limite", limite)
                .query(Resultat.class)
                .list();
    }

    /**
     * Recherche lexicale, en deux cas.
     *
     * <p>Question sans référence : plein texte français, les mots de la question (lemmatisés, sans les mots vides)
     * sont cherchés en « ou » (filtre {@code @@} sur l'index GIN) et classés par densité de correspondance,
     * {@code ts_rank_cd} normalisé dans [0, 1[ (option 32 : rang / (rang + 1)).
     *
     * <p>Question qui cite un article (« L3141-3 », « article 5.7 ») : c'est cet article qu'on cherche. Seuls les
     * morceaux qui le portent en titre (+2) ou qui le citent (+1) sont retenus, par expression régulière tolérante à
     * la typographie (balayage, acceptable pour quelques milliers de morceaux), puis départagés par le rang lexical.
     * Les paliers sont stricts : titre devant citation, quelle que soit la longueur du texte.
     */
    public List<Resultat> pleinTexte(String question, String referenceRegex, int limite) {
        return jdbc.sql("""
                WITH requete AS (
                    SELECT replace(plainto_tsquery('french', :question)::text, '&', '|')::tsquery AS ts
                )
                SELECT id,
                       ts_rank_cd(to_tsvector('french', texte), requete.ts, 32)
                         + CASE WHEN :ref <> '' AND chapitre ~* :ref THEN 2
                                WHEN :ref <> '' AND texte ~* :ref THEN 1
                                ELSE 0 END AS score,
                       document, chapitre, page, texte
                FROM chunk, requete
                WHERE (:ref = '' AND to_tsvector('french', texte) @@ requete.ts)
                   OR (:ref <> '' AND (chapitre ~* :ref OR texte ~* :ref))
                ORDER BY score DESC, id
                LIMIT :limite
                """)
                .param("question", question)
                .param("ref", referenceRegex)
                .param("limite", limite)
                .query(Resultat.class)
                .list();
    }
}
