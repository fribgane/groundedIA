package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.groundedia.eval.Cas.Famille;
import fr.groundedia.eval.Cas.Source;
import fr.groundedia.eval.Metriques.Prix;
import fr.groundedia.eval.Regression.Bloquant;
import fr.groundedia.eval.ReponseAsk.Confiance;
import fr.groundedia.eval.ReponseAsk.Tokens;

class RapportTest {

    private static final Cas A = new Cas("A01", Famille.A, "Combien de jours pour un mariage ?", null, "Quatre jours ouvrés.",
            List.of(new Source("syntec", "5.7"), new Source("legislative", "L. 3142-4")), "Syntec 5.7 p. 26 (morceau 3082).");
    private static final Cas B = new Cas("B01", Famille.B, "Ai-je assez de solde ?", 1L, "Non, il manque 2,5 jours.", List.of(), null);
    private static final Cas C = new Cas("C01", Famille.C, "Capitale du Japon | Tokyo ?", null, null, List.of(), null);

    private static ReponseAsk reponse(String texte, boolean refus, List<String> citations) {
        return new ReponseAsk(texte, refus, false, citations, List.of(), 98_000, new Tokens(2600, 60, 2660), "local", "qwen2.5:7b",
                new Confiance(0.7, 0.5, false, 0.58, 0.40, true));
    }

    private static Rapport.Contexte contexte(String fournisseur, Prix prix, int casTotal) {
        return new Rapport.Contexte(LocalDateTime.of(2026, 9, 5, 15, 42), "a127f03", true, "http://localhost:8080",
                fournisseur, "qwen2.5:7b", "qwen2.5:7b", prix, casTotal);
    }

    private static List<ResultatCas> resultats() {
        return List.of(
                new ResultatCas(A, reponse("Quatre jours ouvrés [convention.pdf, Article 5.7, p. 26].", false, List.of("[convention.pdf, Article 5.7, p. 26]")), null, true, true, null, false),
                new ResultatCas(B, reponse("Oui, largement.", false, List.of()), new Verdict(Verdict.Decision.INCORRECT, "conclusion inverse", 90), false, null,
                        "réponse inexacte selon le juge (conclusion inverse)", false),
                new ResultatCas(C, reponse("Tokyo.", false, List.of()), null, false, null, "a répondu au lieu de refuser", false));
    }

    @Test
    void le_rapport_a_la_date_le_commit_les_scores_les_echecs_detailles_et_les_bloquants() {
        Regression regression = new Regression(List.of(new Bloquant("C01", "2026-09-05", "culture générale"), new Bloquant("Z99", "2026-09-05", "absent")));

        String md = Rapport.rendre(contexte("local", new Prix(0, 0), 3), resultats(), Metriques.calculer(resultats(), new Prix(0, 0)), regression);

        assertThat(md).startsWith("# Évaluation de groundedIA — 5 septembre 2026 à 15:42");
        assertThat(md).doesNotContain("Évaluation en cours");
        assertThat(md).contains("Commit `a127f03` (le dépôt contenait des modifications non enregistrées dans git)")
                .contains("génération par le modèle local `qwen2.5:7b` via Ollama")
                .contains("**3 cas** (1 A, 1 B, 1 C) sur les 50 prévus")
                .contains("réussi **1 cas sur 3 (33 %)**")
                .contains("| A — documents seuls |").contains("| 1 | 1 | 100 % |")
                .contains("| **Global** | | 3 | 1 | **33 %** |")
                .contains("| Sources citées correctes (parmi les réponses données quand une source était attendue) | 1 / 1 (100 %) |")
                .contains("| Refus corrects (famille C) | 0 / 1 (0 %) |")
                .contains("| Temps de réponse médian (questions ayant appelé le modèle) | 98 s |")
                .contains("0 € : modèle local, le coût est le temps machine (98 s par question en médiane)");
        assertThat(md).contains("## Échecs détaillés (2)")
                .contains("### B01 — « Ai-je assez de solde ? » (salarié 1)")
                .contains("- **Réponse attendue** : Non, il manque 2,5 jours.")
                .contains("- **Réponse obtenue** : Oui, largement.")
                .contains("- **Cause probable** : réponse inexacte selon le juge (conclusion inverse)")
                .contains("### C01 — « Capitale du Japon | Tokyo ? » — **cas bloquant**")
                .contains("un refus (« Je n'ai pas trouvé cette information dans les documents fournis. »)")
                .doesNotContain("Note du cas : Syntec 5.7"); // le cas A a réussi : sa note n'apparaît pas
        assertThat(md).contains("## Cas bloquants (non-régression)")
                .contains("| C01 | 2026-09-05 | culture générale | **ÉCHEC** |")
                .contains("| Z99 | 2026-09-05 | absent | **absent du jeu de référence** |");
        assertThat(md).contains("## Comment lire ce rapport").contains("**La garde**").contains("**Suspecte**").contains("Ses limites");
        assertThat(md.indexOf("## Scores")).isLessThan(md.indexOf("## Échecs détaillés"));
    }

    @Test
    void un_rapport_partiel_le_dit_et_un_echec_montre_la_note_du_cas_et_les_sources_lisibles() {
        List<ResultatCas> partiel = List.of(new ResultatCas(A, reponse("Quatre jours.", false, List.of()),
                new Verdict(Verdict.Decision.CORRECT, "ok", 50), false, false, "réponse juste mais mal sourcée : attendu 5.7 (convention Syntec) ou L. 3142-4 (Code du travail, partie législative), cité aucune source", false));
        Regression regression = new Regression(List.of(new Bloquant("C01", "2026-09-05", "culture générale")));

        String md = Rapport.rendre(contexte("api", new Prix(0, 0), 30), partiel, Metriques.calculer(partiel, new Prix(0, 0)), regression);

        assertThat(md).contains("> **Évaluation en cours** : 1 cas sur 30 évalués.")
                .contains("Jeu de référence : **30 cas** sur les 50 prévus.")
                .contains("génération par l'API distante, modèle `qwen2.5:7b`")
                .contains("- **Source attendue** : 5.7 (convention Syntec) ou L. 3142-4 (Code du travail, partie législative)")
                .contains("- **Note du cas** : Syntec 5.7 p. 26 (morceau 3082).")
                .contains("| C01 | 2026-09-05 | culture générale | pas encore évalué |")
                .contains("non calculé : prix non configurés");
        assertThat(md).doesNotContain("(1 A, 0 B, 0 C)"); // la répartition n'est donnée qu'une fois tous les cas joués
    }

    @Test
    void le_cout_est_lisible_quand_des_prix_sont_configures_et_les_non_mesurables_sont_dits() {
        List<ResultatCas> resultats = List.of(
                new ResultatCas(A, reponse("Quatre jours [convention.pdf, Article 5.7, p. 26].", false, List.of("[convention.pdf, Article 5.7, p. 26]")), null, true, true, null, false),
                ResultatCas.erreurApplication(B, "connexion refusée"));
        Prix prix = new Prix(0.10, 0.30); // 0,10 € et 0,30 € le million : 2600 × 0,10 + 60 × 0,30 = 278 µ€ pour la seule réponse

        String md = Rapport.rendre(contexte("api", prix, 2), resultats, Metriques.calculer(resultats, prix), new Regression(List.of()));

        assertThat(md).contains("| Coût moyen par question | 0,0003 €, soit 0,28 € pour 1 000 questions")
                .contains("1 cas n'a pas pu être mesuré (erreur technique ou verdict du juge illisible) : ils comptent en échec.")
                .contains("| Cas non mesurables (erreur technique, verdict illisible), comptés en échec | 1 |")
                .contains("Aucun cas promu bloquant pour l'instant");
    }

    @Test
    void les_durees_sont_lisibles() {
        assertThat(Rapport.duree(236)).isEqualTo("236 ms");
        assertThat(Rapport.duree(98_000)).isEqualTo("98 s");
        assertThat(Rapport.duree(98_400)).isEqualTo("98,4 s");
    }
}
