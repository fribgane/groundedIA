package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import fr.groundedia.eval.Verdict.Decision;

class VerdictTest {

    @Test
    void lit_la_decision_et_la_raison() {
        Verdict v = Verdict.analyser("CORRECT : la réponse donne bien quatre jours ouvrés.", 120);

        assertThat(v.decision()).isEqualTo(Decision.CORRECT);
        assertThat(v.raison()).isEqualTo("la réponse donne bien quatre jours ouvrés.");
        assertThat(v.tokens()).isEqualTo(120);
    }

    @Test
    void incorrect_n_est_pas_pris_pour_correct_et_la_ponctuation_est_toleree() {
        assertThat(Verdict.analyser("INCORRECT: elle refuse de répondre", 0).decision()).isEqualTo(Decision.INCORRECT);
        assertThat(Verdict.analyser("**Incorrect** — un autre chiffre", 0).decision()).isEqualTo(Decision.INCORRECT);
        assertThat(Verdict.analyser("« CORRECT »", 0).decision()).isEqualTo(Decision.CORRECT);
        assertThat(Verdict.analyser("Correct.\nRaison : tout y est.", 0).decision()).isEqualTo(Decision.CORRECT);
        // le féminin, que la consigne emploie elle-même (« la réponse est CORRECTE »)
        assertThat(Verdict.analyser("CORRECTE : tout y est", 0).decision()).isEqualTo(Decision.CORRECT);
        assertThat(Verdict.analyser("INCORRECTE : elle refuse", 0).decision()).isEqualTo(Decision.INCORRECT);
        // la ponctuation Markdown en tête de raison est retirée
        assertThat(Verdict.analyser("**INCORRECT** : un autre chiffre", 0).raison()).isEqualTo("un autre chiffre");
        assertThat(Verdict.analyser("**Incorrect** — un autre chiffre", 0).raison()).isEqualTo("un autre chiffre");
    }

    @Test
    void tout_le_reste_est_indetermine() {
        assertThat(Verdict.analyser("La réponse est correcte.", 0).decision()).isEqualTo(Decision.INDETERMINE);
        assertThat(Verdict.analyser("", 0).decision()).isEqualTo(Decision.INDETERMINE);
        assertThat(Verdict.analyser(null, 0).raison()).isEqualTo("«  »"); // le texte reçu, tel quel, entre guillemets
    }
}
