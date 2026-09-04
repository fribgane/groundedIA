package fr.groundedia.core.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingestion des PDF d'un dossier : extraction du texte page par page, découpage en morceaux, enregistrement.
 *
 * <p>Idempotente : un PDF dont l'empreinte (SHA-256 du fichier + version des règles de découpage) est déjà
 * enregistrée est ignoré ; un PDF nouveau ou modifié remplace l'ensemble de ses morceaux, dans une transaction
 * par document (tout ou rien). Un PDF illisible est signalé et n'empêche pas l'ingestion des autres.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public enum Statut { INGERE, INCHANGE, SANS_TEXTE, ERREUR }

    /** Bilan d'un document : statut, pages lues, morceaux produits, titres distincts, taille médiane des morceaux. */
    public record Resultat(String document, Statut statut, int pages, int morceaux, int titres, int tailleMediane) {
    }

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final TransactionTemplate transaction;
    private final PdfTextExtractor extracteur = new PdfTextExtractor();
    private final Chunker chunker = new Chunker();

    public IngestionService(DocumentRepository documents, ChunkRepository chunks,
                            PlatformTransactionManager gestionnaireDeTransactions) {
        this.documents = documents;
        this.chunks = chunks;
        this.transaction = new TransactionTemplate(gestionnaireDeTransactions);
    }

    /** Ingère tous les PDF d'un dossier, par ordre alphabétique, et signale les documents en base absents du dossier. */
    public List<Resultat> ingerer(Path dossier) throws IOException {
        if (!Files.isDirectory(dossier)) {
            throw new IllegalArgumentException("Dossier introuvable : " + dossier.toAbsolutePath().normalize());
        }
        List<Path> pdfs;
        try (Stream<Path> fichiers = Files.list(dossier)) {
            pdfs = fichiers.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".pdf")).sorted().toList();
        }
        List<Resultat> resultats = new ArrayList<>();
        for (Path pdf : pdfs) {
            String nom = pdf.getFileName().toString();
            try {
                resultats.add(ingererDocument(pdf));
            } catch (IOException e) {
                log.error("{} : lecture impossible ({})", nom, e.getMessage());
                resultats.add(new Resultat(nom, Statut.ERREUR, 0, 0, 0, 0));
            }
        }
        Set<String> presents = pdfs.stream().map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        for (String nom : documents.noms()) {
            if (!presents.contains(nom)) {
                log.warn("{} : en base mais absent du dossier (fichier renommé ou retiré ?) ; ses morceaux restent "
                        + "citables. Pour le retirer : DELETE FROM document WHERE nom = '{}'", nom, nom);
            }
        }
        return resultats;
    }

    public Resultat ingererDocument(Path pdf) throws IOException {
        String nom = pdf.getFileName().toString();
        // Empreinte du fichier + version des règles de découpage : un document est ré-ingéré si l'un des deux change.
        String empreinte = empreinte(pdf) + ":decoupage-v" + Chunker.VERSION;

        var existant = documents.trouver(nom);
        if (existant.isPresent() && existant.get().empreinte().equals(empreinte)) {
            int conserves = chunks.compter(nom);
            Statut statut = conserves == 0 ? Statut.SANS_TEXTE : Statut.INCHANGE;
            return new Resultat(nom, statut, existant.get().nbPages(), conserves, 0, 0);
        }

        List<PageText> pages = extracteur.extraire(pdf);
        List<Chunk> morceaux = chunker.decouper(nom, pages);
        transaction.executeWithoutResult(statut -> {
            documents.supprimer(nom);
            documents.inserer(nom, empreinte, pages.size());
            morceaux.forEach(chunks::inserer);
        });
        Statut statut = morceaux.isEmpty() ? Statut.SANS_TEXTE : Statut.INGERE;
        return new Resultat(nom, statut, pages.size(), morceaux.size(), titresDistincts(morceaux), tailleMediane(morceaux));
    }

    private static String empreinte(Path fichier) throws IOException {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream flux = new DigestInputStream(Files.newInputStream(fichier), sha256)) {
                flux.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(sha256.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static int titresDistincts(List<Chunk> morceaux) {
        return (int) morceaux.stream().map(Chunk::chapitre).distinct().count();
    }

    private static int tailleMediane(List<Chunk> morceaux) {
        if (morceaux.isEmpty()) {
            return 0;
        }
        int[] tailles = morceaux.stream().mapToInt(m -> m.texte().length()).sorted().toArray();
        return tailles[tailles.length / 2];
    }
}
