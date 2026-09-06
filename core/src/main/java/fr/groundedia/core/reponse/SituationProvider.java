package fr.groundedia.core.reponse;

import java.util.Optional;

/**
 * Fournit la situation d'une personne à partir de son identifiant. Implémenté par le cas d'usage (requête SQL écrite
 * en Java, paramètres liés) ; le socle ne fait qu'insérer le résultat dans le prompt.
 */
public interface SituationProvider {

    /** La situation de la personne, ou vide si l'identifiant est inconnu. */
    Optional<Situation> situation(long id);
}
