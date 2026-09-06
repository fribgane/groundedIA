package fr.groundedia.examples.hrleave.salarie;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès à la table {@code salarie}. Requête écrite en Java, paramètre lié : le LLM ne génère jamais de SQL.
 * Une seule ligne, désignée par son identifiant, et des colonnes nommées une à une : jamais {@code SELECT *}.
 */
@Repository
public class SalarieRepository {

    /** Les seules colonnes lues. Ajouter une colonne ici, c'est décider qu'elle peut être montrée au modèle. */
    static final String COLONNES = "id, nom, date_embauche, type_contrat, statut, temps_travail, solde_conges, convention";

    /** La requête, constante et testée : une ligne, celle de l'identifiant demandé. */
    static final String REQUETE = "SELECT " + COLONNES + " FROM salarie WHERE id = :id";

    private final JdbcClient jdbc;

    public SalarieRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Salarie> trouver(long id) {
        return jdbc.sql(REQUETE)
                .param("id", id)
                .query(Salarie.class)
                .optional();
    }
}
