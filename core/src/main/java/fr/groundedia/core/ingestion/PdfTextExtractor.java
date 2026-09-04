package fr.groundedia.core.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Extrait le texte d'un PDF page par page avec Apache PDFBox.
 * Le numéro de page est conservé dès l'extraction : c'est lui qui sera cité.
 */
public final class PdfTextExtractor {

    public List<PageText> extraire(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper extracteur = new PDFTextStripper();
            List<PageText> pages = new ArrayList<>();
            for (int numero = 1; numero <= document.getNumberOfPages(); numero++) {
                extracteur.setStartPage(numero);
                extracteur.setEndPage(numero);
                pages.add(new PageText(numero, extracteur.getText(document)));
            }
            return pages;
        }
    }
}
