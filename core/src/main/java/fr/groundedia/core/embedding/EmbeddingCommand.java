package fr.groundedia.core.embedding;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import fr.groundedia.core.ingestion.ChunkRepository;
import fr.groundedia.core.ingestion.ChunkRepository.ChunkSansEmbedding;

/**
 * Commande de calcul des embeddings, déclenchée par l'argument {@code --embed} :
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.arguments=--embed}.
 *
 * <p>Idempotente : seuls les morceaux sans embedding sont traités, par lots, avec une barre de progression
 * dans les journaux (plusieurs centaines de morceaux, lent sur un processeur). Relancer la commande après une
 * interruption reprend là où elle s'était arrêtée.
 */
@Component
public class EmbeddingCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingCommand.class);

    /** Nombre de textes par appel à Ollama : assez pour amortir l'appel HTTP, assez peu pour une progression visible. */
    static final int TAILLE_DE_LOT = 16;

    /** Dimension attendue par la colonne {@code chunk.embedding vector(1024)}. */
    static final int DIMENSION = 1024;

    private final EmbeddingClient embeddings;
    private final ChunkRepository chunks;
    private final TransactionTemplate transaction;

    public EmbeddingCommand(EmbeddingClient embeddings, ChunkRepository chunks,
                            PlatformTransactionManager gestionnaireDeTransactions) {
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.transaction = new TransactionTemplate(gestionnaireDeTransactions);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("embed")) {
            int sans = chunks.compterSansEmbedding();
            if (sans > 0) {
                log.warn("{} morceau(x) sans embedding : la recherche vectorielle et l'hybride les ignorent ; "
                        + "lancer --embed", sans);
            }
            return;
        }
        int total = chunks.compterSansEmbedding();
        int deja = chunks.compter() - total;
        if (total == 0) {
            log.info("Embeddings : rien à faire, les {} morceaux ont déjà leur vecteur ({})", deja, embeddings.modele());
            return;
        }
        log.info("Embeddings : {} morceaux à traiter avec {} ({} déjà faits), par lots de {}",
                total, embeddings.modele(), deja, TAILLE_DE_LOT);

        long debut = System.nanoTime();
        int faits = 0;
        while (true) {
            List<ChunkSansEmbedding> lot = chunks.sansEmbedding(TAILLE_DE_LOT);
            if (lot.isEmpty()) {
                break;
            }
            List<float[]> vecteurs = embeddings.embed(lot.stream().map(ChunkSansEmbedding::texte).toList());
            verifierDimension(vecteurs);
            transaction.executeWithoutResult(statut -> {
                for (int i = 0; i < lot.size(); i++) {
                    chunks.enregistrerEmbedding(lot.get(i).id(), vecteurs.get(i));
                }
            });
            faits += lot.size();
            log.info("{}", progression(faits, total, System.nanoTime() - debut));
        }
        log.info("Embeddings terminés : {} morceaux en {}", faits, duree(System.nanoTime() - debut));
    }

    private static void verifierDimension(List<float[]> vecteurs) {
        for (float[] v : vecteurs) {
            if (v.length != DIMENSION) {
                throw new IllegalStateException("Le modèle renvoie des vecteurs de " + v.length + " dimensions, "
                        + "la colonne chunk.embedding en attend " + DIMENSION + " : changer de modèle d'embedding "
                        + "ou la dimension de la colonne (migration Flyway)");
            }
        }
    }

    /** « [██████████░░░░░░░░░░]  50 % · 245/489 · 1,9 morceau/s · reste ≈ 2 min 08 s ». */
    static String progression(int faits, int total, long ecouleNanos) {
        int largeur = 20;
        // bornes : des morceaux insérés pendant le calcul (ingestion parallèle) peuvent porter faits au-delà de total
        int pleins = Math.min(largeur, (int) Math.round(largeur * (double) faits / total));
        String barre = "█".repeat(pleins) + "░".repeat(largeur - pleins);
        double secondes = ecouleNanos / 1e9;
        double debit = secondes > 0 ? faits / secondes : 0;
        long resteSecondes = debit > 0 ? Math.round(Math.max(0, total - faits) / debit) : 0;
        return String.format("[%s] %3d %% · %d/%d · %.1f morceau/s · reste ≈ %s",
                barre, Math.min(100, 100 * faits / total), faits, total, debit, duree(resteSecondes * 1_000_000_000L));
    }

    static String duree(long nanos) {
        long secondes = Math.round(nanos / 1e9);
        return secondes < 60 ? secondes + " s" : String.format("%d min %02d s", secondes / 60, secondes % 60);
    }
}
