package fr.groundedia.examples.hrleave.salarie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AncienneteTest {

    private static final LocalDate LE_5_SEPTEMBRE_2026 = LocalDate.of(2026, 9, 5);

    @Test
    void amina_embauchee_le_1er_mars_2019_a_7_ans_et_6_mois() {
        var anciennete = Anciennete.calculer(LocalDate.of(2019, 3, 1), LE_5_SEPTEMBRE_2026);

        assertThat(anciennete).isEqualTo(new Anciennete(7, 6));
        assertThat(anciennete).hasToString("7 ans et 6 mois");
    }

    @Test
    void marc_embauche_le_15_septembre_2024_a_1_an_et_11_mois() {
        var anciennete = Anciennete.calculer(LocalDate.of(2024, 9, 15), LE_5_SEPTEMBRE_2026);

        assertThat(anciennete).isEqualTo(new Anciennete(1, 11));
        assertThat(anciennete).hasToString("1 an et 11 mois");
    }

    @Test
    void les_mois_entames_ne_comptent_pas_et_l_anniversaire_fait_basculer() {
        assertThat(Anciennete.calculer(LocalDate.of(2024, 9, 15), LocalDate.of(2026, 9, 14))).isEqualTo(new Anciennete(1, 11));
        assertThat(Anciennete.calculer(LocalDate.of(2024, 9, 15), LocalDate.of(2026, 9, 15))).isEqualTo(new Anciennete(2, 0));
        assertThat(Anciennete.calculer(LocalDate.of(2024, 9, 15), LocalDate.of(2026, 9, 15))).hasToString("2 ans");
    }

    @Test
    void s_exprime_aussi_en_annees_decimales_a_une_decimale() {
        assertThat(new Anciennete(7, 6).enAnnees()).isEqualTo("7,5 années");
        assertThat(new Anciennete(1, 11).enAnnees()).isEqualTo("1,9 année");
        assertThat(new Anciennete(2, 0).enAnnees()).isEqualTo("2,0 années");
        assertThat(new Anciennete(0, 3).enAnnees()).isEqualTo("0,3 année");
    }

    @Test
    void moins_d_un_an_s_exprime_en_mois_et_moins_d_un_mois_le_dit() {
        assertThat(Anciennete.calculer(LocalDate.of(2026, 6, 1), LE_5_SEPTEMBRE_2026)).hasToString("3 mois");
        assertThat(Anciennete.calculer(LocalDate.of(2026, 8, 20), LE_5_SEPTEMBRE_2026)).hasToString("moins d'un mois");
        assertThat(Anciennete.calculer(LocalDate.of(2027, 1, 1), LE_5_SEPTEMBRE_2026)).hasToString("moins d'un mois");
    }
}
