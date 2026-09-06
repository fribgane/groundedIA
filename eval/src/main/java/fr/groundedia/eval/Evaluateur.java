package fr.groundedia.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.groundedia.eval.Cas.Famille;
import fr.groundedia.eval.ReponseAsk.Confiance;
import fr.groundedia.eval.Verdict.Decision;

/**
 * Évalue les cas un par un : appel de l'application, puis, selon la famille, contrôle du refus (code), verdict du
 * juge (LLM) et contrôle de la citation (code). Chaque échec reçoit une cause probable, déduite des signaux que
 * l'application renvoie : la garde a-t-elle refusé ou laissé passer, le passage attendu était-il dans les extraits
 * fournis, la citation désigne-t-elle la bonne source. Une panne de l'application ou du juge sur un cas ne fait pas
 * tomber la campagne : le cas est marqué non mesurable et l'évaluation continue.
 */
public class Evaluateur {

    private static final Logger log = LoggerFactory.getLogger(Evaluateur.class);

    private final AskClient client;
    private final Juge juge;

    public Evaluateur(AskClient client, Juge juge) {
        this.client = client;
        this.juge = juge;
    }

    /** Évalue tous les cas ; {@code apresChaqueCas} reçoit les résultats acquis, pour réécrire le rapport au fil de l'eau. */
    public List<ResultatCas> evaluer(List<Cas> cas, Consumer<List<ResultatCas>> apresChaqueCas) {
        List<ResultatCas> resultats = new ArrayList<>();
        long debut = System.nanoTime();
        int n = 0;
        for (Cas c : cas) {
            n++;
            long debutCas = System.nanoTime();
            ResultatCas r = evaluerUn(c);
            resultats.add(r);
            long totalCas = (System.nanoTime() - debutCas) / 1_000_000_000;
            log.info("[{}/{}] {} {} en {} s{}{}", n, cas.size(), c.id(), r.reussi() ? "réussi" : "ÉCHEC", totalCas,
                    r.reponse() == null ? "" : " (application " + r.reponse().latenceMs() / 1000 + " s)",
                    r.cause() == null ? "" : " — " + r.cause());
            try {
                apresChaqueCas.accept(List.copyOf(resultats));
            } catch (RuntimeException e) {
                // le rapport intermédiaire est un confort : son échec (fichier verrouillé…) n'arrête pas la campagne
                log.warn("Traitement après le cas {} impossible ({}) : l'évaluation continue", c.id(), message(e));
            }
        }
        log.info("Évaluation terminée : {} cas en {} min", cas.size(), (System.nanoTime() - debut) / 60_000_000_000L);
        return resultats;
    }

    ResultatCas evaluerUn(Cas cas) {
        ReponseAsk reponse;
        try {
            reponse = client.demander(cas.question(), cas.salarieId());
        } catch (RuntimeException e) {
            return ResultatCas.erreurApplication(cas, message(e));
        }
        if (reponse == null) {
            return ResultatCas.erreurApplication(cas, "réponse vide");
        }
        return cas.famille() == Famille.C ? controlerRefus(cas, reponse) : controlerReponse(cas, reponse);
    }

    /** Famille C : le système doit refuser. Jugé par le code, sans LLM. */
    private ResultatCas controlerRefus(Cas cas, ReponseAsk r) {
        if (r.refus()) {
            return new ResultatCas(cas, r, null, true, null, null, false);
        }
        String cause = "a répondu au lieu de refuser : la garde a laissé passer la question (" + signaux(r.confiance())
                + ") et le modèle n'a pas refusé"
                + (r.suspecte() ? " ; l'application a marqué la réponse suspecte (aucune citation fondée)"
                : r.citations() != null && !r.citations().isEmpty() ? " ; elle cite " + r.citations() : "");
        return new ResultatCas(cas, r, null, false, null, cause, false);
    }

    /** Familles A et B : pas de refus, verdict CORRECT du juge, ancrage, et citation de la source attendue quand il y en a une. */
    private ResultatCas controlerReponse(Cas cas, ReponseAsk r) {
        boolean sourceAttendue = cas.attendUneSource();
        boolean fourni = sourceAttendue && Sources.fourni(cas.sourcesAttendues(), r.morceaux());
        if (r.refus()) {
            return new ResultatCas(cas, r, null, false, null, causeDuRefus(r, sourceAttendue, fourni), false);
        }
        if (r.reponse() == null || r.reponse().isBlank()) {
            return new ResultatCas(cas, r, null, false, null, "erreur technique : réponse sans texte", true);
        }
        Boolean citation = sourceAttendue ? Sources.citationCorrecte(cas.sourcesAttendues(), r.citations()) : null;
        Verdict verdict;
        try {
            verdict = juge.juger(cas.question(), cas.reponseAttendue(), r.reponse());
        } catch (RuntimeException e) {
            return new ResultatCas(cas, r, null, false, citation, "erreur technique, le juge n'a pas pu être consulté : " + message(e), true);
        }
        if (verdict.decision() == Decision.INDETERMINE) {
            return new ResultatCas(cas, r, verdict, false, citation, "le juge n'a pas rendu de verdict lisible : " + verdict.raison(), true);
        }
        String diagnostic = !sourceAttendue ? "" : fourni
                ? " ; le passage attendu était dans les extraits fournis au modèle (défaut de rédaction ou de raisonnement)"
                : " ; le passage attendu n'était pas dans les extraits fournis au modèle (défaut de recherche)";
        if (verdict.decision() == Decision.INCORRECT) {
            String suspecte = r.suspecte() ? " ; l'application l'avait elle-même marquée suspecte" : "";
            return new ResultatCas(cas, r, verdict, false, citation, "réponse inexacte selon le juge (" + verdict.raison() + ")"
                    + diagnostic + suspecte, false);
        }
        if (Boolean.FALSE.equals(citation)) {
            return new ResultatCas(cas, r, verdict, false, false, "réponse juste mais mal sourcée : attendu "
                    + cas.sourcesAttenduesLisibles() + ", cité " + (r.citations() == null || r.citations().isEmpty()
                    ? "aucune source" : r.citations()) + (fourni ? "" : " ; le passage attendu n'était pas dans les extraits fournis au modèle"), false);
        }
        if (r.suspecte()) {
            return new ResultatCas(cas, r, verdict, false, citation, "réponse juste selon le juge mais non ancrée : "
                    + "l'application l'a marquée suspecte (aucune citation fondée ni donnée de la fiche du salarié)", false);
        }
        return new ResultatCas(cas, r, verdict, true, citation, null, false);
    }

    private static String causeDuRefus(ReponseAsk r, boolean sourceAttendue, boolean fourni) {
        if (r.refusParLaGarde()) {
            return "refus décidé par la garde, avant le modèle (" + signaux(r.confiance()) + ")"
                    + (!sourceAttendue ? "" : fourni
                    ? " ; le passage attendu figurait pourtant parmi les morceaux trouvés, mais avec des scores sous les seuils"
                    : " ; le passage attendu n'est pas parmi les morceaux trouvés (défaut de recherche)");
        }
        if (!sourceAttendue) {
            return "refus du modèle alors que la garde avait laissé passer la question ; la réponse attendue vient de la "
                    + "fiche du salarié, que le modèle avait sous les yeux";
        }
        return fourni
                ? "refus du modèle alors que le passage attendu était dans les extraits fournis : il n'a pas su l'utiliser"
                : "refus du modèle ; le passage attendu n'était pas dans les extraits fournis (défaut de recherche)";
    }

    private static String signaux(Confiance c) {
        if (c == null) {
            return "signaux de recherche absents";
        }
        return String.format(Locale.FRENCH, "similarité %.3f pour un seuil de %.2f, densité lexicale %.3f pour un seuil de %.2f%s",
                c.similarite(), c.seuilSimilarite(), c.lexical(), c.seuilLexical(),
                c.referenceTrouvee() ? ", article cité trouvé" : "");
    }

    private static String message(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replaceAll("\\s+", " ").strip();
    }
}
