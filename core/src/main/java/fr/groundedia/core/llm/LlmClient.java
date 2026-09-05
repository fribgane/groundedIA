package fr.groundedia.core.llm;

/**
 * Génère une réponse à partir d'une consigne système et d'un message.
 *
 * <p>Interface maison, sans framework IA. Deux implémentations, choisies par configuration
 * ({@code llm.provider=local|api}) : le reste du code ne connaît que cette interface.
 */
public interface LlmClient {

    /** Le texte généré et les jetons consommés (prompt, réponse), tels que rapportés par le fournisseur. */
    record Generation(String texte, int tokensPrompt, int tokensReponse) {

        public int tokensTotal() {
            return tokensPrompt + tokensReponse;
        }
    }

    /** « local » (Ollama sur le poste) ou « api » (API distante). */
    String fournisseur();

    String modele();

    Generation generer(String consigneSysteme, String message);
}
