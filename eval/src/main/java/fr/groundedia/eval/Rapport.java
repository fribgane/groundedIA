package fr.groundedia.eval;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import fr.groundedia.eval.Cas.Famille;
import fr.groundedia.eval.Metriques.Prix;
import fr.groundedia.eval.Metriques.Score;
import fr.groundedia.eval.Regression.Bloquant;

/**
 * Le rapport {@code evaluation.md}, écrit pour un lecteur non développeur : la date et le commit, le score en une
 * phrase, les tableaux, puis la liste détaillée des échecs (question, attendu, obtenu, cause probable), l'état des cas
 * bloquants et la méthode avec ses limites. Réécrit après chaque cas pendant l'évaluation, avec un bandeau
 * « en cours » tant que tous les cas n'ont pas été joués.
 */
public final class Rapport {

    /** Le cahier des charges : 50 cas (20 A, 20 B, 10 C). */
    public static final int CAS_PREVUS = 50;

    public static final String REFUS = "Je n'ai pas trouvé cette information dans les documents fournis.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH);

    private Rapport() {
    }

    /** Ce qui entoure les résultats : quand, quel code, quelle application, quels modèles, combien de cas au total. */
    public record Contexte(LocalDateTime date, String commit, boolean modificationsLocales, String baseUrl,
                           String fournisseur, String modele, String jugeModele, Prix prix, int casTotal) {
    }

    public static String rendre(Contexte ctx, List<ResultatCas> resultats, Metriques m, Regression regression) {
        StringBuilder md = new StringBuilder();
        md.append("# Évaluation de groundedIA — ").append(ctx.date().format(DATE)).append("\n\n");
        if (resultats.size() < ctx.casTotal()) {
            md.append("> **Évaluation en cours** : ").append(resultats.size()).append(" cas sur ").append(ctx.casTotal())
                    .append(" évalués. Ce rapport est réécrit après chaque cas.\n\n");
        }
        md.append("Commit `").append(ctx.commit()).append('`')
                .append(ctx.modificationsLocales() ? " (le dépôt contenait des modifications non enregistrées dans git)" : "")
                .append(". Application évaluée : ").append(ctx.baseUrl()).append(", génération par ")
                .append(generateur(ctx)).append(". Juge : modèle local `").append(ctx.jugeModele()).append("` via Ollama.\n\n");

        Score a = m.parFamille().get(Famille.A);
        Score b = m.parFamille().get(Famille.B);
        Score c = m.parFamille().get(Famille.C);
        md.append("Jeu de référence : **").append(ctx.casTotal()).append(" cas**");
        if (resultats.size() == ctx.casTotal()) {
            md.append(" (").append(a.total()).append(" A, ").append(b.total()).append(" B, ").append(c.total()).append(" C)");
        }
        if (ctx.casTotal() < CAS_PREVUS) {
            md.append(" sur les ").append(CAS_PREVUS).append(" prévus");
        }
        md.append(".\n\n");

        md.append("## En une phrase\n\n");
        md.append("Le système a réussi **").append(m.global().reussis()).append(" cas sur ").append(m.global().total())
                .append(" (").append(m.global().pourcentage()).append(")** : ")
                .append(a.reussis()).append(" des ").append(a.total()).append(" questions dont la réponse est dans les documents, ")
                .append(b.reussis()).append(" des ").append(b.total()).append(" questions qui croisent les documents et la fiche du salarié, ")
                .append("et il a refusé ").append(c.reussis()).append(" des ").append(c.total())
                .append(" questions auxquelles il ne devait pas répondre.");
        if (m.nonMesurables() > 0) {
            md.append(" Parmi les échecs, ").append(m.nonMesurables()).append(m.nonMesurables() > 1 ? " cas n'ont" : " cas n'a")
                    .append(" pas pu être mesuré").append(m.nonMesurables() > 1 ? "s" : "")
                    .append(" (erreur technique ou verdict du juge illisible) : ils comptent en échec.");
        }
        md.append("\n\n");

        md.append("## Scores\n\n");
        md.append("| Famille | Ce qu'on mesure | Cas | Réussis | Score |\n|---|---|---:|---:|---:|\n");
        ligne(md, "A — documents seuls", "la réponse est juste selon le juge et cite la bonne source", a);
        ligne(md, "B — documents et fiche du salarié", "la réponse est juste pour ce salarié (et cite la bonne source quand une est attendue)", b);
        ligne(md, "C — refus attendu", "le système refuse au lieu d'inventer", c);
        md.append("| **Global** | | ").append(m.global().total()).append(" | ").append(m.global().reussis())
                .append(" | **").append(m.global().pourcentage()).append("** |\n\n");

        md.append("| Indicateur | Valeur |\n|---|---|\n");
        md.append("| Sources citées correctes (parmi les réponses données quand une source était attendue) | ")
                .append(m.citations().reussis()).append(" / ").append(m.citations().total())
                .append(" (").append(m.citations().pourcentage()).append(") |\n");
        md.append("| Refus corrects (famille C) | ").append(c.reussis()).append(" / ").append(c.total())
                .append(" (").append(c.pourcentage()).append(") |\n");
        md.append("| Refus décidés par la garde, sans appel au modèle | ").append(m.refusSansModele()).append(" |\n");
        md.append("| Temps de réponse médian (questions ayant appelé le modèle) | ").append(duree(m.latenceMedianeMs())).append(" |\n");
        md.append("| Temps de réponse maximal pour 95 % de ces questions (p95) | ").append(duree(m.latenceP95Ms())).append(" |\n");
        md.append("| Coût moyen par question | ").append(cout(m, ctx)).append(" |\n");
        md.append("| Réponses marquées suspectes par l'application | ").append(m.suspectes()).append(" |\n");
        md.append("| Cas non mesurables (erreur technique, verdict illisible), comptés en échec | ").append(m.nonMesurables()).append(" |\n\n");

        List<ResultatCas> echecs = resultats.stream().filter(r -> !r.reussi())
                .sorted(Comparator.comparing((ResultatCas r) -> r.cas().famille()).thenComparing(r -> r.cas().id())).toList();
        md.append("## Échecs détaillés (").append(echecs.size()).append(")\n\n");
        if (echecs.isEmpty()) {
            md.append("Aucun échec.\n\n");
        }
        for (ResultatCas r : echecs) {
            Cas cas = r.cas();
            md.append("### ").append(cas.id()).append(" — « ").append(cas.question()).append(" »");
            if (cas.salarieId() != null) {
                md.append(" (salarié ").append(cas.salarieId()).append(')');
            }
            if (regression.estBloquant(cas.id())) {
                md.append(" — **cas bloquant**");
            }
            md.append("\n\n");
            md.append("- **Réponse attendue** : ").append(cas.famille() == Famille.C
                    ? "un refus (« " + REFUS + " »)" : cas.reponseAttendue()).append('\n');
            if (cas.attendUneSource()) {
                md.append("- **Source attendue** : ").append(cas.sourcesAttenduesLisibles()).append('\n');
            }
            md.append("- **Réponse obtenue** : ").append(r.reponse() == null ? "aucune (erreur technique)" : uneLigne(r.reponse().reponse())).append('\n');
            if (r.reponse() != null && r.reponse().citations() != null && !r.reponse().citations().isEmpty()) {
                md.append("- **Sources citées** : ").append(String.join(" ; ", r.reponse().citations())).append('\n');
            }
            md.append("- **Cause probable** : ").append(r.cause()).append('\n');
            if (cas.commentaire() != null && !cas.commentaire().isBlank()) {
                md.append("- **Note du cas** : ").append(uneLigne(cas.commentaire())).append('\n');
            }
            md.append('\n');
        }

        md.append("## Cas bloquants (non-régression)\n\n");
        if (regression.bloquants().isEmpty()) {
            md.append("Aucun cas promu bloquant pour l'instant. Pour promouvoir un échec, ajouter son identifiant dans "
                    + "`eval/regression-set.yaml` : la commande échouera tant qu'il ne repassera pas.\n\n");
        } else {
            md.append("| Cas | Promu le | Motif | État |\n|---|---|---|---|\n");
            for (Bloquant bq : regression.bloquants()) {
                String etat = resultats.stream().filter(r -> r.cas().id().equals(bq.id())).findFirst()
                        .map(r -> r.reussi() ? "réussi" : "**ÉCHEC**").orElse(resultats.size() < ctx.casTotal() ? "pas encore évalué" : "**absent du jeu de référence**");
                md.append("| ").append(bq.id()).append(" | ").append(cellule(bq.promuLe())).append(" | ")
                        .append(cellule(bq.motif())).append(" | ").append(etat).append(" |\n");
            }
            md.append('\n');
        }

        md.append(METHODE);
        return md.toString();
    }

    private static final String METHODE = """
            ## Comment lire ce rapport

            - **Famille A** : la réponse est dans les documents seuls. Un cas réussit si le juge déclare la réponse \
            correcte **et** si l'une des sources citées par l'application est bien l'article attendu. Une bonne \
            réponse mal sourcée est comptée en échec : devant un salarié, une règle sans sa référence ne se défend pas.
            - **Famille B** : la réponse demande les documents et la fiche du salarié dans la base RH (ancienneté, solde, \
            statut). Même règle, la source n'étant exigée que si le cas en attend une.
            - **Famille C** : le système doit refuser par la phrase exacte « Je n'ai pas trouvé cette information dans \
            les documents fournis. ». Le contrôle est fait par le code, sans juge.
            - **La garde** est le contrôle que l'application fait avant d'appeler le modèle : si la recherche ne trouve \
            aucun passage assez proche de la question (la **similarité**, ressemblance de sens entre la question et le \
            meilleur passage, et la **densité lexicale**, part des mots de la question retrouvés dans le meilleur \
            passage, restent toutes deux sous leur seuil), elle refuse d'office. Un refus de la garde prend une fraction \
            de seconde ; c'est pourquoi les temps de réponse sont donnés pour les seules questions qui ont appelé le modèle.
            - **Suspecte** : l'application marque ainsi une réponse qui ne cite aucun extrait fourni et ne s'appuie sur \
            aucune donnée de la fiche du salarié. Le harnais compte une telle réponse en échec même si le juge la trouve \
            juste : une réponse sans ancrage vérifiable ne se défend pas.
            - **Le juge** est un second appel à un modèle de langage : il compare la réponse obtenue à la réponse \
            attendue, écrite à la main, avec une consigne stricte (chaque information attendue doit être présente, \
            aucune contredite ; un chiffre en lettres vaut un chiffre, une unité différente est une erreur), sans \
            aucun aléa. Ses limites : il peut se tromper comme tout modèle (une tolérance ou une sévérité injustifiée \
            sur une formulation) ; il ne voit pas les documents et ne vérifie que ce que la réponse attendue contient, \
            donc une réponse attendue incomplète ou erronée fausse le verdict et une précision supplémentaire fausse \
            passe inaperçue si la réponse attendue ne la contredit pas ; il ne sait pas juger une nuance juridique que \
            l'expert n'a pas écrite ; quand il est le même modèle que le système évalué, il peut être indulgent avec son \
            propre style ; et un verdict illisible est compté comme non mesurable, donc en échec. C'est pourquoi les \
            refus, l'ancrage et les citations sont jugés par le code, que chaque échec porte la raison du juge pour être \
            relu par un humain, et que seuls des cas relus à la main sont promus bloquants.
            - **La cause probable** est déduite des signaux que l'application renvoie : refus décidé par la garde (la \
            recherche n'a pas trouvé), refus ou erreur du modèle alors que le passage attendu lui avait été fourni \
            (défaut de rédaction ou de raisonnement), passage attendu absent des extraits fournis (défaut de recherche), \
            source citée différente de la source attendue.
            - **Le temps de réponse** est celui mesuré par l'application elle-même pour chaque question, recherche et \
            génération comprises. **Le coût** est calculé à partir des jetons consommés et des prix configurés \
            (`-Deval.prix.entree-par-million`, `-Deval.prix.sortie-par-million`, en euros) ; pour un modèle local il \
            est nul, le coût réel étant le temps machine. Le coût du juge n'est pas compté.
            - Ce rapport est régénéré par `./mvnw -pl eval test -Dtest=GoldenSetRunner`, l'application étant démarrée.
            """;

    private static String generateur(Contexte ctx) {
        return "api".equalsIgnoreCase(ctx.fournisseur())
                ? "l'API distante, modèle `" + ctx.modele() + "`"
                : "le modèle local `" + ctx.modele() + "` via Ollama";
    }

    private static void ligne(StringBuilder md, String famille, String mesure, Score s) {
        md.append("| ").append(famille).append(" | ").append(mesure).append(" | ").append(s.total()).append(" | ")
                .append(s.reussis()).append(" | ").append(s.pourcentage()).append(" |\n");
    }

    static String duree(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        return String.format(Locale.FRENCH, "%.1f s", ms / 1000.0).replace(",0 s", " s");
    }

    private static String cout(Metriques m, Contexte ctx) {
        String jetons = String.format(Locale.FRENCH, "%,.0f jetons en moyenne par question ayant appelé le modèle", m.jetonsMoyens());
        if (!ctx.prix().renseigne()) {
            return "api".equalsIgnoreCase(ctx.fournisseur())
                    ? "non calculé : prix non configurés (`-Deval.prix.entree-par-million`, `-Deval.prix.sortie-par-million`) ; " + jetons
                    : "0 € : modèle local, le coût est le temps machine (" + duree(m.latenceMedianeMs()) + " par question en médiane) ; " + jetons;
        }
        return String.format(Locale.FRENCH, "%.4f €, soit %.2f € pour 1 000 questions (%s)", m.coutMoyenEuros(), m.coutMoyenEuros() * 1000, jetons);
    }

    private static String uneLigne(String texte) {
        return texte == null ? "" : texte.replaceAll("\\s+", " ").strip();
    }

    private static String cellule(String texte) {
        return texte == null ? "" : uneLigne(texte).replace("|", "\\|");
    }
}
