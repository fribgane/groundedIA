package fr.groundedia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import fr.groundedia.eval.Cas.Source;
import fr.groundedia.eval.ReponseAsk.Morceau;

class SourcesTest {

    private static final String SYNTEC_57 = "[convention-collective-syntec-avenant-46-2021.pdf, Article 5.7 – Congés pour évènements familiaux, p. 26]";
    private static final String CODE_3142_4 = "[code-du-travail-partie-legislative-titre-IV-conges.pdf, Article L. 3142-4, p. 11]";
    private static final String CODE_3142_45 = "[code-du-travail-partie-legislative-titre-IV-conges.pdf, Article L. 3142-45, p. 20]";

    @Test
    void reconnait_l_article_quelle_que_soit_la_typographie() {
        assertThat(Sources.designe(new Source("syntec", "5.7"), SYNTEC_57)).isTrue();
        assertThat(Sources.designe(new Source("legislative", "L. 3142-4"), CODE_3142_4)).isTrue();
        assertThat(Sources.designe(new Source("legislative", "L3142-4"), CODE_3142_4)).isTrue();
        assertThat(Sources.designe(new Source(null, "l. 3142-4"), CODE_3142_4)).isTrue();
    }

    @Test
    void ne_confond_ni_les_articles_voisins_ni_les_documents() {
        assertThat(Sources.designe(new Source("legislative", "L. 3142-4"), CODE_3142_45)).isFalse();
        assertThat(Sources.designe(new Source("syntec", "5.7"), "[convention.pdf, Article 5.75, p. 26]")).isFalse();
        assertThat(Sources.designe(new Source("syntec", "L. 3142-4"), CODE_3142_4)).isFalse(); // bon article, mauvais document
        assertThat(Sources.designe(new Source("syntec", "5.7"), "[convention.pdf, Article 15.7, p. 57]")).isFalse();
        assertThat(Sources.designe(new Source("legislative", "L. 3141-5"), "[code-legislative.pdf, Article L. 3141-5-1, p. 3]")).isFalse();
        assertThat(Sources.designe(new Source("syntec", "5.7"), "[convention-syntec.pdf, Article 5.7 - Congés, p. 26]")).isTrue(); // tiret simple
        assertThat(Sources.designe(new Source("syntec", "12"), "[convention-syntec.pdf, Article 1.2 – Définition des ETAM, p. 10]")).isFalse(); // 1.2 ≠ 12
        assertThat(Sources.designe(new Source("syntec", "1.2"), "[convention-syntec.pdf, Article 1.2 – Définition des ETAM, p. 10]")).isTrue();
        assertThat(Sources.designe(new Source("syntec", "1.2"), "[convention-syntec.pdf, Article 1.2.1 – Sous-article, p. 10]")).isFalse();
    }

    @Test
    void une_citation_parmi_plusieurs_suffit_et_le_morceau_fourni_est_reconnu() {
        assertThat(Sources.citationCorrecte(List.of(new Source("syntec", "5.7")), List.of(CODE_3142_4, SYNTEC_57))).isTrue();
        assertThat(Sources.citationCorrecte(List.of(new Source("syntec", "5.7")), List.of(CODE_3142_4))).isFalse();
        assertThat(Sources.citationCorrecte(List.of(new Source("syntec", "5.7")), null)).isFalse();
        List<Morceau> morceaux = List.of(new Morceau("convention-collective-syntec-avenant-46-2021.pdf",
                "Article 5.7 – Congés pour évènements familiaux", 26));
        assertThat(Sources.fourni(List.of(new Source("syntec", "5.7")), morceaux)).isTrue();
        assertThat(Sources.fourni(List.of(new Source("syntec", "5.1")), morceaux)).isFalse();
    }
}
