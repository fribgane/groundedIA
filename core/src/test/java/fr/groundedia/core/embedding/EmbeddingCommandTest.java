package fr.groundedia.core.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import fr.groundedia.core.ingestion.ChunkRepository;

class EmbeddingCommandTest {

    @Test
    void la_barre_de_progression_indique_l_avancement_le_debit_et_le_temps_restant() {
        String ligne = EmbeddingCommand.progression(245, 489, 128_000_000_000L); // 245 morceaux en 128 s

        assertThat(ligne).startsWith("[██████████░░░░░░░░░░]");
        assertThat(ligne).contains(" 50 % · 245/489 · ")
                .contains(String.format("%.1f morceau/s", 245 / 128.0)) // même locale que le code : « 1,9 » ou « 1.9 »
                .contains("reste ≈ 2 min 07 s");
    }

    @Test
    void la_barre_reste_bornee_si_des_morceaux_ont_ete_ajoutes_pendant_le_calcul() {
        String ligne = EmbeddingCommand.progression(505, 489, 10_000_000_000L);

        assertThat(ligne).startsWith("[████████████████████] 100 %").contains("505/489").contains("reste ≈ 0 s");
    }

    @Test
    void la_duree_est_lisible() {
        assertThat(EmbeddingCommand.duree(42_000_000_000L)).isEqualTo("42 s");
        assertThat(EmbeddingCommand.duree(185_000_000_000L)).isEqualTo("3 min 05 s");
    }

    @Test
    void le_vecteur_est_ecrit_au_format_pgvector() {
        assertThat(ChunkRepository.litteral(new float[] {0.5f, -1.0f, 0.25f})).isEqualTo("[0.5,-1.0,0.25]");
    }
}
