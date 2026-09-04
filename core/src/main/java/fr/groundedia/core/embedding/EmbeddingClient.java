package fr.groundedia.core.embedding;

import java.util.List;

/**
 * Calcule la représentation vectorielle (embedding) de textes.
 *
 * <p>Interface maison, sans framework IA : l'implémentation est choisie par configuration
 * (modèle local via Ollama aujourd'hui, API distante demain) et chaque ligne reste explicable.
 */
public interface EmbeddingClient {

    /** Le nom du modèle utilisé, pour les journaux et les contrôles de cohérence. */
    String modele();

    /** Un vecteur par texte, dans le même ordre. */
    List<float[]> embed(List<String> textes);

    default float[] embed(String texte) {
        return embed(List.of(texte)).getFirst();
    }
}
