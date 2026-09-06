package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import fr.groundedia.eval.Cas.Famille;

class JeuDeCasTest {

    private static final String YAML = """
            famille_a:
              - id: A01
                question: Combien de jours pour un mariage ?
                reponse_attendue: Quatre jours ouvrés.
                source_attendue:
                  document: syntec
                  article: "5.7"
                commentaire: facile
            famille_b:
              - id: B01
                salarieId: 1
                question: Ai-je assez de solde pour 15 jours ?
                reponse_attendue: Non, il manque 2,5 jours.
            famille_c:
              - id: C01
                question: Quelle est la capitale du Japon ?
            """;

    @Test
    void lit_les_trois_familles_avec_leurs_champs() {
        JeuDeCas jeu = JeuDeCas.depuisYaml(YAML, "test");

        assertThat(jeu.cas()).hasSize(3);
        Cas a = jeu.parId("A01").orElseThrow();
        assertThat(a.famille()).isEqualTo(Famille.A);
        assertThat(a.reponseAttendue()).isEqualTo("Quatre jours ouvrés.");
        assertThat(a.sourcesAttendues()).containsExactly(new Cas.Source("syntec", "5.7"));
        assertThat(a.commentaire()).isEqualTo("facile");
        Cas b = jeu.parId("B01").orElseThrow();
        assertThat(b.salarieId()).isEqualTo(1L);
        assertThat(b.attendUneSource()).isFalse();
        Cas c = jeu.parId("C01").orElseThrow();
        assertThat(c.famille()).isEqualTo(Famille.C);
        assertThat(c.reponseAttendue()).isNull();
        assertThat(jeu.famille(Famille.B)).containsExactly(b);
    }

    @Test
    void plusieurs_sources_attendues_sont_des_alternatives_egalement_valables() {
        String yaml = """
                famille_a:
                  - id: A02
                    question: Combien de jours pour un mariage ?
                    reponse_attendue: Quatre jours.
                    source_attendue:
                      - {document: syntec, article: "5.7"}
                      - {document: legislative, article: L. 3142-4}
                """;

        Cas a = JeuDeCas.depuisYaml(yaml, "test").parId("A02").orElseThrow();

        assertThat(a.sourcesAttendues()).containsExactly(new Cas.Source("syntec", "5.7"), new Cas.Source("legislative", "L. 3142-4"));
        assertThat(a.sourcesAttenduesLisibles()).isEqualTo("5.7 (convention Syntec) ou L. 3142-4 (Code du travail, partie législative)");
    }

    @Test
    void refuse_un_champ_inconnu_pour_attraper_les_fautes_de_frappe() {
        String yaml = YAML.replace("reponse_attendue: Quatre", "reponse_atendue: Quatre");

        assertThatThrownBy(() -> JeuDeCas.depuisYaml(yaml, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reponse_atendue");
    }

    @Test
    void exige_les_champs_de_chaque_famille() {
        assertThatThrownBy(() -> JeuDeCas.depuisYaml(YAML.replace("    salarieId: 1\n", ""), "test"))
                .hasMessageContaining("B01").hasMessageContaining("salarieId");
        assertThatThrownBy(() -> JeuDeCas.depuisYaml(YAML.replace("    source_attendue:\n      document: syntec\n      article: \"5.7\"\n", ""), "test"))
                .hasMessageContaining("A01").hasMessageContaining("source_attendue");
        assertThatThrownBy(() -> JeuDeCas.depuisYaml(YAML.replace("id: C01", "id: A01"), "test"))
                .hasMessageContaining("en double").hasMessageContaining("A01");
    }

    @Test
    void un_fichier_vide_est_refuse_avec_un_message() {
        assertThatThrownBy(() -> JeuDeCas.depuisYaml("", "test")).hasMessageContaining("vide");
        assertThatThrownBy(() -> JeuDeCas.depuisYaml("---\n", "test")).hasMessageContaining("vide");
    }

    @Test
    void un_jeu_de_non_regression_absent_est_vide_et_un_present_se_lit() {
        assertThat(Regression.charger(java.nio.file.Path.of("n-existe-pas.yaml")).bloquants()).isEmpty();
        Regression r = Regression.depuisYaml("bloquants:\n  - id: C01\n    promu_le: 2026-09-05\n    motif: refus\n", "test");
        assertThat(r.estBloquant("C01")).isTrue();
        assertThat(r.estBloquant("A01")).isFalse();
        assertThat(Regression.depuisYaml("bloquants: []\n", "test").bloquants()).isEmpty();
    }
}
