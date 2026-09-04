package fr.groundedia.core.ingestion;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès à la table {@code document} : un document ingéré, son empreinte et son nombre de pages.
 * Requêtes écrites en Java, paramètres liés.
 */
@Repository
public class DocumentRepository {

    public record DocumentEnregistre(String nom, String empreinte, int nbPages) {
    }

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> noms() {
        return jdbc.sql("SELECT nom FROM document ORDER BY nom")
                .query(String.class)
                .list();
    }

    public Optional<DocumentEnregistre> trouver(String nom) {
        return jdbc.sql("SELECT nom, empreinte, nb_pages FROM document WHERE nom = :nom")
                .param("nom", nom)
                .query(DocumentEnregistre.class)
                .optional();
    }

    /** Supprime le document et, en cascade, tous ses morceaux. */
    public void supprimer(String nom) {
        jdbc.sql("DELETE FROM document WHERE nom = :nom")
                .param("nom", nom)
                .update();
    }

    public void inserer(String nom, String empreinte, int nbPages) {
        jdbc.sql("INSERT INTO document (nom, empreinte, nb_pages) VALUES (:nom, :empreinte, :nbPages)")
                .param("nom", nom)
                .param("empreinte", empreinte)
                .param("nbPages", nbPages)
                .update();
    }
}
