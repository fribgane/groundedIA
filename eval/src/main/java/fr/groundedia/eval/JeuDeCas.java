package fr.groundedia.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import fr.groundedia.eval.Cas.Famille;
import fr.groundedia.eval.Cas.Source;

/**
 * Le jeu de référence, lu depuis {@code golden-set.yaml} : trois listes, {@code famille_a}, {@code famille_b},
 * {@code famille_c}. Un champ inconnu ou un cas incomplet arrête la lecture avec un message précis : le fichier est
 * écrit à la main, une faute de frappe ne doit pas passer en silence.
 */
public record JeuDeCas(List<Cas> cas) {

    public static JeuDeCas charger(Path fichier) {
        try {
            return depuisYaml(Files.readString(fichier), fichier.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Jeu de référence introuvable : " + fichier.toAbsolutePath(), e);
        }
    }

    static JeuDeCas depuisYaml(String yaml, String origine) {
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException(origine + " : fichier vide, aucune famille de cas");
        }
        Fichier fichier;
        try {
            fichier = mapper().readValue(yaml, Fichier.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(origine + " : YAML invalide : " + e.getMessage(), e);
        }
        if (fichier == null) {
            throw new IllegalArgumentException(origine + " : fichier vide, aucune famille de cas");
        }
        List<Cas> cas = new ArrayList<>();
        ajouter(cas, fichier.familleA(), Famille.A, origine);
        ajouter(cas, fichier.familleB(), Famille.B, origine);
        ajouter(cas, fichier.familleC(), Famille.C, origine);
        Set<String> ids = new HashSet<>();
        for (Cas c : cas) {
            if (!ids.add(c.id())) {
                throw new IllegalArgumentException(origine + " : identifiant en double : " + c.id());
            }
        }
        return new JeuDeCas(List.copyOf(cas));
    }

    public List<Cas> famille(Famille famille) {
        return cas.stream().filter(c -> c.famille() == famille).toList();
    }

    public Optional<Cas> parId(String id) {
        return cas.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    static ObjectMapper mapper() {
        return new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    private static void ajouter(List<Cas> cible, List<Entree> entrees, Famille famille, String origine) {
        if (entrees == null) {
            return;
        }
        for (Entree e : entrees) {
            String ou = origine + ", famille " + famille + ", cas « " + (e.id() == null ? e.question() : e.id()) + " »";
            exiger(e.id() != null && !e.id().isBlank(), ou + " : id manquant");
            exiger(e.question() != null && !e.question().isBlank(), ou + " : question manquante");
            if (famille != Famille.C) {
                exiger(e.reponseAttendue() != null && !e.reponseAttendue().isBlank(), ou + " : reponse_attendue manquante");
            }
            List<Source> sources = e.sourcesAttendues() == null ? List.of()
                    : e.sourcesAttendues().stream().map(s -> new Source(s.document(), s.article())).toList();
            for (Source s : sources) {
                exiger(s.article() != null && !s.article().isBlank(), ou + " : source_attendue sans article");
            }
            if (famille == Famille.A) {
                exiger(!sources.isEmpty(), ou + " : source_attendue (document, article) manquante");
            }
            if (famille == Famille.C) {
                exiger(sources.isEmpty() && e.reponseAttendue() == null, ou + " : un refus attendu n'a ni réponse ni source");
            }
            if (famille == Famille.B) {
                exiger(e.salarieId() != null, ou + " : salarieId manquant");
            } else {
                exiger(e.salarieId() == null, ou + " : salarieId n'a de sens qu'en famille B");
            }
            cible.add(new Cas(e.id().strip(), famille, e.question().strip(), e.salarieId(),
                    e.reponseAttendue() == null ? null : e.reponseAttendue().strip(), sources, e.commentaire()));
        }
    }

    private static void exiger(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** La forme du fichier YAML. */
    record Fichier(@JsonProperty("famille_a") List<Entree> familleA,
                   @JsonProperty("famille_b") List<Entree> familleB,
                   @JsonProperty("famille_c") List<Entree> familleC) {
    }

    /** {@code source_attendue} accepte un objet seul ou une liste d'alternatives également valables. */
    record Entree(String id, String question, @JsonProperty("salarieId") Long salarieId,
                  @JsonProperty("reponse_attendue") String reponseAttendue,
                  @JsonProperty("source_attendue") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                  List<SourceEntree> sourcesAttendues,
                  String commentaire) {
    }

    record SourceEntree(String document, String article) {
    }
}
