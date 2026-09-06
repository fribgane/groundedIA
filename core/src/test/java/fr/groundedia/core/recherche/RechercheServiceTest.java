package fr.groundedia.core.recherche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
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

/** Les signaux de la recherche hybride, dont dépend le refus sans appel au LLM. */
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
    void expose_la_similarite_et_la_densite_lexicale_du_meilleur_morceau_de_chaque_liste() {
        listes(List.of(resultat(1, 0.512, "Article 9.2"), resultat(2, 0.499, "Article L. 3142-4")),
                List.of(resultat(3, 0.630, "Article 9.2"), resultat(2, 0.615, "Article 5.7")));

        var recherche = service.hybride("J'ai 3 ans d'ancienneté, combien de jours pour mon mariage ?", 5);

        assertThat(recherche.similariteMax()).isEqualTo(0.512);
        assertThat(recherche.lexicalMax()).isEqualTo(0.630);
        assertThat(recherche.referenceTrouvee()).isFalse();
        verify(depot).vectorielle(any(float[].class), eq(20));
        verify(depot).pleinTexte(anyString(), eq(""), eq(5));
    }

    @Test
    void un_article_cite_present_en_titre_est_une_reference_trouvee() {
        listes(List.of(resultat(1, 0.513, "Article L. 3142-35")),
                List.of(resultat(2, 2.17, "Article L. 3141-3"), resultat(3, 1.47, "Article L. 3141-24")));

        var recherche = service.hybride("Que dit l'article L3141-3 ?", 5);

        assertThat(recherche.referenceTrouvee()).isTrue();
        assertThat(recherche.lexicalMax()).isCloseTo(0.17, within(1e-9)); // la densité seule, sans le bonus de titre
        verify(depot).pleinTexte(anyString(), eq("\\mL\\.?\\s?3141-3(?![-\\d])"), eq(5));
    }

    @Test
    void un_article_seulement_cite_par_d_autres_morceaux_n_est_pas_une_reference_trouvee() {
        // « Que dit l'article L3121-44 ? » : l'article n'est pas dans le corpus, seul L. 3141-5 le mentionne (+1)
        when(embeddings.embed(anyString())).thenReturn(new float[1024]);
        when(depot.vectorielle(any(float[].class), anyInt())).thenReturn(List.of(resultat(1, 0.50, "Article L. 3142-35")));
        when(depot.compterEmbeddings()).thenReturn(489);
        when(depot.pleinTexte(anyString(), eq("\\mL\\.?\\s?3121-44(?![-\\d])"), anyInt()))
                .thenReturn(List.of(resultat(2, 1.17, "Article L. 3141-5")));
        when(depot.pleinTexte(anyString(), eq(""), anyInt()))
                .thenReturn(List.of(resultat(5, 0.31, "Article L. 3141-1")));

        var recherche = service.hybride("Que dit l'article L3121-44 ?", 5);

        assertThat(recherche.referenceTrouvee()).isFalse();
        // faute de titre, la recherche repart sur les mots de la question : densité 0,31, pas 0,17 ni 0
        assertThat(recherche.lexicalMax()).isCloseTo(0.31, within(1e-9));
        assertThat(recherche.morceaux()).extracting(Resultat::id).contains(5L);
    }

    @Test
    void sans_aucun_resultat_lexical_la_densite_est_nulle() {
        listes(List.of(resultat(1, 0.301, "PRÉAMBULE")), List.of());

        var recherche = service.hybride("Quelle est la capitale du Japon ?", 5);

        assertThat(recherche.similariteMax()).isEqualTo(0.301);
        assertThat(recherche.lexicalMax()).isZero();
        assertThat(recherche.morceaux()).hasSize(1);
    }

    @Test
    void refuse_une_question_vide() {
        assertThatThrownBy(() -> service.hybride("  ", 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
