package fr.groundedia.core.reponse;

import java.util.List;

/**
 * La situation de la personne qui pose la question : les champs libellés que le modèle est autorisé à voir, et rien
 * d'autre. Le cas d'usage décide des champs (voir {@link SituationProvider}) ; le socle les insère dans le bloc
 * « SITUATION DU SALARIÉ (source : base de données RH) » du prompt.
 */
public record Situation(List<Champ> champs) {

    public record Champ(String libelle, String valeur) {
    }
}
