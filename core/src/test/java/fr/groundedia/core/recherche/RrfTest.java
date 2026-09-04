package fr.groundedia.core.recherche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

class RrfTest {

    private static Resultat morceau(long id, double score) {
        return new Resultat(id, score, "doc.pdf", "Article " + id, 1, "texte " + id);
    }

    @Test
    void additionne_les_apports_des_deux_classements() {
        var vectoriel = List.of(morceau(1, 0.9), morceau(2, 0.8), morceau(3, 0.7));
        var pleinTexte = List.of(morceau(4, 5.0), morceau(1, 3.0));

        var fusion = Rrf.fusionner(List.of(vectoriel, pleinTexte), 10);

        // 1 : rang 1 et rang 2 → 1/61 + 1/62 ; 4 : rang 1 seul → 1/61 ; 2 : rang 2 seul → 1/62 ; 3 : rang 3 → 1/63
        assertThat(fusion).extracting(Resultat::id).containsExactly(1L, 4L, 2L, 3L);
        assertThat(fusion.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-12));
        assertThat(fusion.get(1).score()).isCloseTo(1.0 / 61, within(1e-12));
    }

    @Test
    void un_morceau_present_dans_les_deux_classements_passe_devant_un_premier_isole() {
        var vectoriel = List.of(morceau(10, 0.99), morceau(20, 0.5), morceau(30, 0.4));
        var pleinTexte = List.of(morceau(40, 9.0), morceau(30, 1.0), morceau(20, 0.5));

        var fusion = Rrf.fusionner(List.of(vectoriel, pleinTexte), 10);

        // 20 et 30 sont dans les deux listes (rangs 2+3 et 3+2 → même score), 10 et 40 premiers d'une seule liste
        assertThat(fusion.subList(0, 2)).extracting(Resultat::id).containsExactlyInAnyOrder(20L, 30L);
        assertThat(fusion.subList(2, 4)).extracting(Resultat::id).containsExactlyInAnyOrder(10L, 40L);
        // à score strictement égal, l'identifiant le plus petit passe devant : ordre déterministe
        assertThat(fusion).extracting(Resultat::id).containsExactly(20L, 30L, 10L, 40L);
    }

    @Test
    void respecte_la_limite_et_conserve_la_provenance() {
        var fusion = Rrf.fusionner(List.of(List.of(morceau(1, 0.9), morceau(2, 0.8)), List.of(morceau(3, 1.0))), 2);

        assertThat(fusion).hasSize(2);
        assertThat(fusion.get(0)).extracting(Resultat::document, Resultat::chapitre, Resultat::page, Resultat::texte)
                .containsExactly("doc.pdf", "Article 1", 1, "texte 1");
    }
}
