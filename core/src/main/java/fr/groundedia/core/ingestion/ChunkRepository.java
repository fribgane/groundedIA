package fr.groundedia.core.ingestion;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès à la table {@code chunk}. Requêtes écrites en Java, paramètres liés.
 */
@Repository
public class ChunkRepository {

    /** Un morceau qui n'a pas encore de vecteur. */
    public record ChunkSansEmbedding(long id, String texte) {
    }

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

    public int compter() {
        return jdbc.sql("SELECT count(*) FROM chunk").query(Integer.class).single();
    }

    public int compterSansEmbedding() {
        return jdbc.sql("SELECT count(*) FROM chunk WHERE embedding IS NULL").query(Integer.class).single();
    }

    public List<ChunkSansEmbedding> sansEmbedding(int limite) {
        return jdbc.sql("SELECT id, texte FROM chunk WHERE embedding IS NULL ORDER BY id LIMIT :limite")
                .param("limite", limite)
                .query(ChunkSansEmbedding.class)
                .list();
    }

    /** Enregistre le vecteur au format texte de pgvector : « [0.1,0.2,…] ». */
    public void enregistrerEmbedding(long id, float[] vecteur) {
        jdbc.sql("UPDATE chunk SET embedding = CAST(:vecteur AS vector) WHERE id = :id")
                .param("vecteur", litteral(vecteur))
                .param("id", id)
                .update();
    }

    public static String litteral(float[] vecteur) {
        return IntStream.range(0, vecteur.length)
                .mapToObj(i -> Float.toString(vecteur[i]))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
