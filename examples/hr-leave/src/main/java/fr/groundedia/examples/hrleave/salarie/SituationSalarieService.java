package fr.groundedia.examples.hrleave.salarie;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import fr.groundedia.core.reponse.Situation;
import fr.groundedia.core.reponse.Situation.Champ;
import fr.groundedia.core.reponse.SituationProvider;

/**
 * Décrit la situation d'un salarié pour le modèle : les champs autorisés, et rien d'autre.
 *
 * <p>Règle de sécurité : le modèle ne voit que le salarié demandé (une ligne, par identifiant) et que les champs
 * listés ici. Le nom n'en fait pas partie : aucune règle n'en dépend, et une donnée nominative ne doit pas quitter
 * le poste quand la génération passe par une API distante. L'ancienneté est calculée en Java à la date du jour.
 * Les libellés reprennent le vocabulaire de la convention Syntec (« ingénieurs et cadres », « ETAM ») pour que le
 * modèle applique la bonne règle.
 */
@Service
public class SituationSalarieService implements SituationProvider {

    /** Les libellés des champs montrés au modèle, dans l'ordre. Le test de sécurité vérifie qu'il n'y en a pas d'autre. */
    public static final List<String> CHAMPS_AUTORISES = List.of("Date d'embauche", "Ancienneté", "Type de contrat",
            "Statut", "Temps de travail", "Solde de congés payés", "Convention collective");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final SalarieRepository salaries;
    private final Clock horloge;

    public SituationSalarieService(SalarieRepository salaries, Clock horloge) {
        this.salaries = salaries;
        this.horloge = horloge;
    }

    @Override
    public Optional<Situation> situation(long id) {
        return salaries.trouver(id).map(this::decrire);
    }

    Situation decrire(Salarie salarie) {
        LocalDate aujourdHui = LocalDate.now(horloge);
        Anciennete anciennete = Anciennete.calculer(salarie.dateEmbauche(), aujourdHui);
        String convention = Objects.requireNonNullElse(salarie.convention(), "non renseignée");
        return new Situation(List.of(
                new Champ("Date d'embauche", salarie.dateEmbauche().format(DATE)),
                new Champ("Ancienneté", anciennete + " au " + aujourdHui.format(DATE) + ", soit " + anciennete.enAnnees()),
                new Champ("Type de contrat", salarie.typeContrat()),
                new Champ("Statut", libelleStatut(salarie.statut(), convention)),
                new Champ("Temps de travail", nombre(salarie.tempsTravail()) + " % d'un temps plein"),
                new Champ("Solde de congés payés", nombre(salarie.soldeConges()) + " jours ouvrés"),
                new Champ("Convention collective", convention)));
    }

    /** Le statut de la base, traduit dans le vocabulaire de la convention quand c'est celle que l'on connaît. */
    static String libelleStatut(String statut, String convention) {
        boolean syntec = "Syntec".equalsIgnoreCase(convention);
        return switch (statut) {
            case "CADRE" -> syntec ? "cadre (catégorie « ingénieurs et cadres » de la convention)" : "cadre";
            case "NON_CADRE" -> syntec ? "non-cadre (catégorie « ETAM » de la convention)" : "non-cadre";
            default -> statut;
        };
    }

    /** « 12,5 », « 4 », « 100 » : décimale française, sans zéros inutiles. */
    static String nombre(BigDecimal valeur) {
        return valeur.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
