package fr.groundedia.examples.hrleave.salarie;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un salarié tel que la table {@code salarie} le décrit. Les champs sont ceux, et seulement ceux, que le système
 * lit en base ; ce que le modèle pourra voir en est un sous-ensemble libellé (voir {@link SituationSalarieService}).
 */
public record Salarie(long id, String nom, LocalDate dateEmbauche, String typeContrat, String statut,
                      BigDecimal tempsTravail, BigDecimal soldeConges, String convention) {
}
