package fr.groundedia.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Le jeu de non-régression ({@code regression-set.yaml}) : les identifiants de cas du jeu de référence promus
 * « bloquants ». Un cas bloquant qui échoue fait échouer le build. Promouvoir un échec = ajouter son id ici, avec
 * la date et le motif ; le rapport rappelle ces cas et leur état.
 */
public record Regression(List<Bloquant> bloquants) {

    public record Bloquant(String id, @JsonProperty("promu_le") String promuLe, String motif) {
    }

    record Fichier(List<Bloquant> bloquants) {
    }

    public static Regression charger(Path fichier) {
        if (!Files.exists(fichier)) {
            return new Regression(List.of());
        }
        try {
            return depuisYaml(Files.readString(fichier), fichier.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Jeu de non-régression illisible : " + fichier.toAbsolutePath(), e);
        }
    }

    static Regression depuisYaml(String yaml, String origine) {
        try {
            Fichier f = JeuDeCas.mapper().readValue(yaml, Fichier.class);
            List<Bloquant> liste = f == null || f.bloquants() == null ? List.of() : f.bloquants();
            for (Bloquant b : liste) {
                if (b.id() == null || b.id().isBlank()) {
                    throw new IllegalArgumentException(origine + " : un cas bloquant sans id");
                }
            }
            return new Regression(List.copyOf(liste));
        } catch (IOException e) {
            throw new IllegalArgumentException(origine + " : YAML invalide : " + e.getMessage(), e);
        }
    }

    public boolean estBloquant(String id) {
        return bloquants.stream().anyMatch(b -> b.id().equals(id));
    }
}
