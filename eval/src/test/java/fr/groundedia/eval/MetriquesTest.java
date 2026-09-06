package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import fr.groundedia.eval.Cas.Famille;
import fr.groundedia.eval.Metriques.Prix;
import fr.groundedia.eval.ReponseAsk.Confiance;
import fr.groundedia.eval.ReponseAsk.Tokens;

class MetriquesTest {

    private static Cas cas(String id, Famille f) {
        return new Cas(id, f, "q", f == Famille.B ? 1L : null, f == Famille.C ? null : "r",
                f == Famille.A ? List.of(new Cas.Source("syntec", "5.7")) : List.of(), null);
    }

    /** Une réponse du modèle (couverte = vrai) ou un refus de la garde (couverte = faux, aucun jeton). */
    private static ReponseAsk reponse(long latence, int prompt, int sortie, boolean suspecte) {
        boolean garde = prompt == 0;
        return new ReponseAsk(garde ? "refus" : "texte", garde, suspecte, List.of(), List.of(), latence,
                new Tokens(prompt, sortie, prompt + sortie), "local", "m",
                new Confiance(garde ? 0.30 : 0.70, 0.2, false, 0.58, 0.40, !garde));
    }

    @Test
    void mediane_et_percentile_au_rang_le_plus_proche() {
        assertThat(Metriques.mediane(List.of(5L, 1L, 3L))).isEqualTo(3);
        assertThat(Metriques.mediane(List.of(1L, 2L, 3L, 4L))).isEqualTo(3); // (2+3)/2 arrondi
        assertThat(Metriques.mediane(List.of())).isZero();
        List<Long> unAVingt = LongStream.rangeClosed(1, 20).boxed().toList();
        assertThat(Metriques.percentile(unAVingt, 95)).isEqualTo(19);
        assertThat(Metriques.percentile(unAVingt, 50)).isEqualTo(10);
        assertThat(Metriques.percentile(List.of(7L), 95)).isEqualTo(7);
    }

    @Test
    void scores_par_famille_citations_refus_latences_cout_et_jetons() {
        List<ResultatCas> resultats = List.of(
                new ResultatCas(cas("A01", Famille.A), reponse(100, 2000, 50, false), null, true, true, null, false),
                new ResultatCas(cas("A02", Famille.A), reponse(200, 2000, 50, true), null, false, false, "mal sourcée", false),
                new ResultatCas(cas("A03", Famille.A), reponse(10, 0, 0, false), null, false, null, "refus par la garde", false),
                new ResultatCas(cas("B01", Famille.B), reponse(300, 3000, 100, false), null, true, null, null, false),
                new ResultatCas(cas("C01", Famille.C), reponse(10, 0, 0, false), null, true, null, null, false),
                new ResultatCas(cas("C02", Famille.C), reponse(400, 2000, 60, false), null, false, null, "a répondu", false),
                ResultatCas.erreurApplication(cas("B02", Famille.B), "connexion refusée"));

        Metriques m = Metriques.calculer(resultats, new Prix(1.0, 3.0)); // 1 € et 3 € le million de jetons

        assertThat(m.global()).isEqualTo(new Metriques.Score(7, 3));
        assertThat(m.global().pourcentage()).isEqualTo("43 %");
        assertThat(m.parFamille().get(Famille.A)).isEqualTo(new Metriques.Score(3, 1));
        assertThat(m.parFamille().get(Famille.B)).isEqualTo(new Metriques.Score(2, 1));
        assertThat(m.refus()).isEqualTo(new Metriques.Score(2, 1));
        assertThat(m.citations()).as("seuls les cas avec source attendue et une réponse donnée").isEqualTo(new Metriques.Score(2, 1));
        assertThat(m.refusSansModele()).as("A03 et C01 : refus de la garde").isEqualTo(2);
        assertThat(m.latenceMedianeMs()).as("100, 200, 300, 400 sans les refus de la garde").isEqualTo(250);
        assertThat(m.latenceP95Ms()).isEqualTo(400);
        // coût sur les 6 réponses reçues : (2000·1 + 50·3) × 2 + (3000·1 + 100·3) + 0 + 0 + (2000·1 + 60·3) = 9 780 µ€
        assertThat(m.coutMoyenEuros()).isCloseTo(9_780e-6 / 6, within(1e-12));
        assertThat(m.jetonsMoyens()).as("jetons des 4 questions ayant appelé le modèle").isCloseTo((2050 + 2050 + 3100 + 2060) / 4.0, within(1e-9));
        assertThat(m.suspectes()).isEqualTo(1);
        assertThat(m.nonMesurables()).isEqualTo(1);
    }

    @Test
    void sans_resultat_tout_est_a_zero_sans_division_par_zero() {
        Metriques m = Metriques.calculer(List.of(), new Prix(0, 0));

        assertThat(m.global().pourcentage()).isEqualTo("—");
        assertThat(m.coutMoyenEuros()).isZero();
        assertThat(m.latenceP95Ms()).isZero();
        assertThat(new Prix(0, 0).renseigne()).isFalse();
        assertThat(new Prix(0.1, 0).renseigne()).isTrue();
    }
}
