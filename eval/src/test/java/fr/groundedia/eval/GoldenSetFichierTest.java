package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import fr.groundedia.eval.Cas.Famille;

/**
 * Le vrai fichier {@code golden-set.yaml} se lit et respecte le cahier des charges : au plus 20 A, 20 B, 10 C, les cas
 * obligatoires présents, et chaque cas bloquant de {@code regression-set.yaml} existe. Sans réseau : ce test tourne
 * dans le build ordinaire et attrape une faute de frappe dans un cas ajouté à la main.
 */
class GoldenSetFichierTest {

    private final JeuDeCas jeu = JeuDeCas.charger(Path.of("golden-set.yaml"));
    private final Regression regression = Regression.charger(Path.of("regression-set.yaml"));

    @Test
    void le_jeu_de_reference_respecte_la_repartition_des_familles() {
        assertThat(jeu.famille(Famille.A)).isNotEmpty().hasSizeLessThanOrEqualTo(20);
        assertThat(jeu.famille(Famille.B)).isNotEmpty().hasSizeLessThanOrEqualTo(20);
        assertThat(jeu.famille(Famille.C)).isNotEmpty().hasSizeLessThanOrEqualTo(10);
        assertThat(jeu.cas()).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void les_cas_obligatoires_du_cahier_des_charges_sont_presents() {
        assertThat(jeu.famille(Famille.C)).extracting(Cas::question)
                .contains("Quelle est la capitale du Japon ?", "Puis-je télétravailler depuis l'étranger ?");
        // le couple Amina (1) / Marc (2) sur la même question
        assertThat(jeu.famille(Famille.B).stream()
                .filter(c -> c.salarieId() == 1L)
                .anyMatch(amina -> jeu.famille(Famille.B).stream()
                        .anyMatch(marc -> marc.salarieId() == 2L && marc.question().equals(amina.question()))))
                .as("au moins une question posée pour les salariés 1 et 2").isTrue();
    }

    @Test
    void chaque_cas_bloquant_designe_un_cas_existant() {
        assertThat(regression.bloquants()).isNotEmpty();
        for (Regression.Bloquant b : regression.bloquants()) {
            assertThat(jeu.parId(b.id())).as("cas bloquant %s", b.id()).isPresent();
        }
    }
}
