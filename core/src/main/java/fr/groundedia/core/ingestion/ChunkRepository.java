package fr.groundedia.core.ingestion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès à la table {@code chunk}. Requêtes écrites en Java, paramètres liés.
 */
@Repository
public class ChunkRepository {

    private final JdbcClient jdbc;

    public ChunkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void inserer(Chunk chunk) {
        jdbc.sql("INSERT INTO chunk (document, chapitre, page, texte) VALUES (:document, :chapitre, :page, :texte)")
                .param("document", chunk.document())
                .param("chapitre", chunk.chapitre())
                .param("page", chunk.page())
                .param("texte", chunk.texte())
                .update();
    }

    public int compter(String document) {
        return jdbc.sql("SELECT count(*) FROM chunk WHERE document = :document")
                .param("document", document)
                .query(Integer.class)
                .single();
    }
}
