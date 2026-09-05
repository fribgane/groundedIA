package fr.groundedia.core.recherche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import fr.groundedia.core.embedding.EmbeddingClient;

/** Les signaux de confiance de la recherche hybride, dont dépend le refus sans appel au LLM. */
class RechercheServiceTest {

    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final RechercheRepository depot = mock(RechercheRepository.class);
    private final RechercheService service = new RechercheService(embeddings, depot, "hybrid", 20, 5);

    private static Resultat resultat(long id, double score, String chapitre) {
        return new Resultat(id, score, "doc.pdf", chapitre, 1, "texte");
    }

    private void listes(List<Resultat> vectoriels, List<Resultat> lexicaux) {
        when(embeddings.embed(anyString())).thenReturn(new float[1024]);
        when(depot.vectorielle(any(float[].class), anyInt())).thenReturn(vectoriels);
        when(depot.pleinTexte(anyString(), anyString(), anyInt())).thenReturn(lexicaux);
        when(depot.compterEmbeddings()).thenReturn(489);
    }

    @Test
    void la_confiance_est_le_maximum_de_la_similarite_et_de_la_densite_lexicale() {
        listes(List.of(resultat(1, 0.512, "Article 9.2"), resultat(2, 0.499, "Article L. 3142-4")),
                List.of(resultat(3, 0.630, "Article 9.2"), resultat(2, 0.615, "Article 5.7")));

        var recherche = service.hybride("J'ai 3 ans d'ancienneté, combien de jours pour mon mariage ?", 5);

        assertThat(recherche.similariteMax()).isEqualTo(0.512);
        assertThat(recherche.lexicalMax()).isEqualTo(0.630);
        assertThat(recherche.referenceTrouvee()).isFalse();
        assertThat(recherche.confiance()).isEqualTo(0.630);
        verify(depot).vectorielle(any(float[].class), eq(20));
        verify(depot).pleinTexte(anyString(), eq(""), eq(5));
    }

    @Test
    void un_article_cite_present_en_titre_vaut_confiance_maximale() {
        listes(List.of(resultat(1, 0.513, "Article L. 3142-35")),
                List.of(resultat(2, 2.17, "Article L. 3141-3"), resultat(3, 1.47, "Article L. 3141-24")));

        var recherche = service.hybride("Que dit l'article L3141-3 ?", 5);

        assertThat(recherche.referenceTrouvee()).isTrue();
        assertThat(recherche.lexicalMax()).isCloseTo(0.17, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(recherche.confiance()).isEqualTo(1.0);
        verify(depot).pleinTexte(anyString(), eq("\\mL\\.?\\s?3141-3(?![-\\d])"), eq(5));
    }

    @Test
    void un_article_seulement_cite_par_d_autres_morceaux_ne_vaut_pas_confiance_maximale() {
        // « Que dit l'article L3121-44 ? » : l'article n'est pas dans le corpus, seul L. 3141-5 le mentionne (+1)
        listes(List.of(resultat(1, 0.50, "Article L. 3142-35")),
                List.of(resultat(2, 1.17, "Article L. 3141-5")));

        var recherche = service.hybride("Que dit l'article L3121-44 ?", 5);

        assertThat(recherche.referenceTrouvee()).isFalse();
        assertThat(recherche.confiance()).isCloseTo(0.50, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void sans_aucun_resultat_lexical_la_confiance_est_la_similarite() {
        listes(List.of(resultat(1, 0.301, "PRÉAMBULE")), List.of());

        var recherche = service.hybride("Quelle est la capitale du Japon ?", 5);

        assertThat(recherche.confiance()).isEqualTo(0.301);
        assertThat(recherche.morceaux()).hasSize(1);
    }

    @Test
    void refuse_une_question_vide() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.hybride("  ", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
