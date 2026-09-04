package fr.groundedia.core.recherche;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReferencesArticlesTest {

    @Test
    void reconnait_une_reference_du_code_quelle_que_soit_sa_typographie() {
        assertThat(ReferencesArticles.expressionPostgres("que dit l'article L3141-3 ?"))
                .isEqualTo("\\mL\\.?\\s?3141-3(?![-\\d])");
        assertThat(ReferencesArticles.expressionPostgres("Que dit l'article L. 3141-3 ?"))
                .isEqualTo("\\mL\\.?\\s?3141-3(?![-\\d])");
        assertThat(ReferencesArticles.expressionPostgres("contenu de l'article D 3141-5"))
                .isEqualTo("\\mD\\.?\\s?3141-5(?![-\\d])");
        assertThat(ReferencesArticles.expressionPostgres("Article L3141-5-1"))
                .isEqualTo("\\mL\\.?\\s?3141-5-1(?![-\\d])");
        assertThat(ReferencesArticles.expressionPostgres("que dit l'article l3141-3 ?"))
                .as("saisie en minuscules").isEqualTo("\\mL\\.?\\s?3141-3(?![-\\d])");
    }

    @Test
    void reconnait_un_numero_d_article_de_convention() {
        assertThat(ReferencesArticles.expressionPostgres("Que prévoit l'article 5.7 de la convention ?"))
                .isEqualTo("\\marticle\\s+5\\.7(?![.\\d])");
        assertThat(ReferencesArticles.expressionPostgres("art. 24"))
                .isEqualTo("\\marticle\\s+24(?![.\\d])");
    }

    @Test
    void combine_plusieurs_references_et_ne_prend_pas_un_numero_de_code_pour_un_numero_de_convention() {
        assertThat(ReferencesArticles.expressionPostgres("articles L3141-3 et R3142-1"))
                .isEqualTo("\\mL\\.?\\s?3141-3(?![-\\d])|\\mR\\.?\\s?3142-1(?![-\\d])");
        assertThat(ReferencesArticles.expressionPostgres("l'article L. 3141-3 du code"))
                .doesNotContain("\\marticle");
    }

    @Test
    void une_question_en_langage_naturel_ne_contient_aucune_reference() {
        assertThat(ReferencesArticles.expressionPostgres("combien de jours de congé pour me marier ?")).isEmpty();
        assertThat(ReferencesArticles.expressionPostgres("j'ai 3 enfants et 12 jours de congés")).isEmpty();
    }
}
