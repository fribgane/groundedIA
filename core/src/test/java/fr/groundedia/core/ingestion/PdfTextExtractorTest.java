package fr.groundedia.core.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfTextExtractorTest {

    @TempDir
    Path dossier;

    /** Un PDF de deux pages où l'article 7 commence page 1 et se termine page 2, au milieu d'une phrase. */
    private Path pdfAvecUneRegleSurDeuxPages() throws IOException {
        Path pdf = dossier.resolve("convention.pdf");
        ecrirePdf(pdf, List.of(
                List.of("Article 7",
                        "Le salarié a droit à un congé de deux jours et demi ouvrables par mois de travail effectif",
                        "chez le même"),
                List.of("employeur. La durée totale du congé exigible ne peut excéder trente jours ouvrables.",
                        "Article 8",
                        "Les jours fériés sont chômés.")));
        return pdf;
    }

    @Test
    void extrait_le_texte_page_par_page() throws IOException {
        var pages = new PdfTextExtractor().extraire(pdfAvecUneRegleSurDeuxPages());

        assertThat(pages).extracting(PageText::numero).containsExactly(1, 2);
        assertThat(pages.get(0).texte()).contains("Article 7").contains("chez le même").doesNotContain("employeur");
        assertThat(pages.get(1).texte()).contains("employeur. La durée totale").contains("Article 8");
    }

    @Test
    void une_regle_sur_deux_pages_d_un_vrai_pdf_donne_un_morceau_entier_rattache_a_sa_premiere_page()
            throws IOException {
        var pages = new PdfTextExtractor().extraire(pdfAvecUneRegleSurDeuxPages());

        var morceaux = new Chunker().decouper("convention.pdf", pages);

        assertThat(morceaux).extracting(Chunk::chapitre).containsExactly("Article 7", "Article 8");
        assertThat(morceaux.get(0).page()).isEqualTo(1);
        assertThat(morceaux.get(0).texte()).contains("chez le même employeur. La durée totale du congé");
        assertThat(morceaux.get(1).page()).isEqualTo(2);
        assertThat(morceaux).allSatisfy(m -> {
            assertThat(m.document()).isEqualTo("convention.pdf");
            assertThat(m.chapitre()).isNotBlank();
            assertThat(m.page()).isPositive();
        });
    }

    private static void ecrirePdf(Path fichier, List<List<String>> lignesParPage) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font police = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> lignes : lignesParPage) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream contenu = new PDPageContentStream(document, page)) {
                    contenu.beginText();
                    contenu.setFont(police, 11);
                    contenu.setLeading(14f);
                    contenu.newLineAtOffset(50, 780);
                    for (String ligne : lignes) {
                        contenu.showText(ligne);
                        contenu.newLine();
                    }
                    contenu.endText();
                }
            }
            document.save(fichier.toFile());
        }
    }
}
