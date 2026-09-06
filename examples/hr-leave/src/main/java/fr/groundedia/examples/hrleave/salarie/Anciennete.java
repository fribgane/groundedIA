package fr.groundedia.examples.hrleave.salarie;

import java.time.LocalDate;
import java.time.Period;

/**
 * L'ancienneté d'un salarié à une date donnée, en années et mois entiers, calculée en Java (pas par le modèle).
 */
public record Anciennete(int annees, int mois) {

    public static Anciennete calculer(LocalDate dateEmbauche, LocalDate aujourdHui) {
        if (aujourdHui.isBefore(dateEmbauche)) {
            return new Anciennete(0, 0);
        }
        Period periode = Period.between(dateEmbauche, aujourdHui);
        return new Anciennete(periode.getYears(), periode.getMonths());
    }

    /**
     * La même durée en années décimales, « 7,5 années », « 1,9 année » : un petit modèle compare plus sûrement
     * 7,5 à 5 et à 10 que « 7 ans et 6 mois » à « cinq années ».
     */
    public String enAnnees() {
        double valeur = annees + mois / 12.0;
        return String.format(java.util.Locale.FRENCH, "%.1f", valeur) + (valeur < 2 ? " année" : " années");
    }

    /** « 7 ans et 6 mois », « 1 an et 11 mois », « 3 mois », « moins d'un mois ». */
    @Override
    public String toString() {
        String ans = annees == 0 ? "" : annees + (annees > 1 ? " ans" : " an");
        String moisTexte = mois == 0 ? "" : mois + " mois";
        if (ans.isEmpty() && moisTexte.isEmpty()) {
            return "moins d'un mois";
        }
        if (ans.isEmpty() || moisTexte.isEmpty()) {
            return ans + moisTexte;
        }
        return ans + " et " + moisTexte;
    }
}
