package fr.groundedia.eval;

/**
 * Le résultat d'un cas : la réponse de l'application, le verdict du juge (familles A et B, hors refus), la réussite,
 * la justesse de la citation ({@code null} quand aucune source n'était attendue ou que le système a refusé) et, en
 * cas d'échec, la cause probable en français, telle qu'elle apparaît dans le rapport.
 *
 * <p>{@code nonMesurable} distingue un échec du harnais (application ou juge injoignable, verdict illisible) d'un
 * échec du système : les deux comptent en échec dans le score, mais le rapport les sépare.
 */
public record ResultatCas(Cas cas, ReponseAsk reponse, Verdict verdict, boolean reussi, Boolean citationCorrecte,
                          String cause, boolean nonMesurable) {

    public static ResultatCas erreurApplication(Cas cas, String erreur) {
        return new ResultatCas(cas, null, null, false, null, "erreur technique, l'application n'a pas répondu : " + erreur, true);
    }
}
