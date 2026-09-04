package fr.groundedia.core.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import fr.groundedia.core.ingestion.ChunkRepository;
import fr.groundedia.core.ingestion.ChunkRepository.ChunkSansEmbedding;

/** La boucle de la commande --embed : lots, arrêt quand tout est fait, garde de dimension avant toute écriture. */
class EmbeddingCommandLotsTest {

    private static List<ChunkSansEmbedding> morceaux(int de, int a) {
        return IntStream.range(de, a).mapToObj(i -> new ChunkSansEmbedding(i, "texte " + i)).toList();
    }

    private static PlatformTransactionManager transactions() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(new SimpleTransactionStatus());
        return tx;
    }

    @Test
    void traite_les_morceaux_sans_embedding_par_lots_de_16_jusqu_a_epuisement() {
        ChunkRepository chunks = mock(ChunkRepository.class);
        when(chunks.compterSansEmbedding()).thenReturn(20);
        when(chunks.compter()).thenReturn(25);
        // 16 morceaux, puis les 4 restants, puis plus rien : la commande s'arrête d'elle-même
        when(chunks.sansEmbedding(16)).thenReturn(morceaux(0, 16), morceaux(16, 20), List.of());
        List<List<String>> lotsRecus = new ArrayList<>();
        EmbeddingClient client = new EmbeddingClient() {
            public String modele() { return "faux-modele"; }
            public List<float[]> embed(List<String> textes) {
                lotsRecus.add(textes);
                return textes.stream().map(t -> new float[EmbeddingCommand.DIMENSION]).toList();
            }
        };

        new EmbeddingCommand(client, chunks, transactions()).run(new DefaultApplicationArguments("--embed"));

        assertThat(lotsRecus).extracting(List::size).containsExactly(16, 4);
        verify(chunks, times(20)).enregistrerEmbedding(anyLong(), org.mockito.ArgumentMatchers.any(float[].class));
    }

    @Test
    void refuse_un_modele_d_une_autre_dimension_sans_rien_ecrire() {
        ChunkRepository chunks = mock(ChunkRepository.class);
        when(chunks.compterSansEmbedding()).thenReturn(3);
        when(chunks.compter()).thenReturn(3);
        when(chunks.sansEmbedding(anyInt())).thenReturn(morceaux(0, 3));
        EmbeddingClient nomic = new EmbeddingClient() {
            public String modele() { return "nomic-embed-text"; }
            public List<float[]> embed(List<String> textes) {
                return textes.stream().map(t -> new float[768]).toList();
            }
        };

        assertThatThrownBy(() -> new EmbeddingCommand(nomic, chunks, transactions())
                .run(new DefaultApplicationArguments("--embed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("768 dimensions").hasMessageContaining("1024");
        verify(chunks, never()).enregistrerEmbedding(anyLong(), org.mockito.ArgumentMatchers.any(float[].class));
    }

    @Test
    void sans_l_option_embed_ne_fait_rien_d_autre_que_signaler_les_morceaux_sans_vecteur() {
        ChunkRepository chunks = mock(ChunkRepository.class);
        when(chunks.compterSansEmbedding()).thenReturn(7);
        EmbeddingClient client = mock(EmbeddingClient.class);

        new EmbeddingCommand(client, chunks, transactions()).run(new DefaultApplicationArguments());

        verify(chunks).compterSansEmbedding();
        verify(chunks, never()).sansEmbedding(anyInt());
        verify(client, never()).embed(org.mockito.ArgumentMatchers.anyList());
        verify(chunks, never()).enregistrerEmbedding(anyLong(), org.mockito.ArgumentMatchers.any(float[].class));
    }
}
