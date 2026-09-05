package fr.groundedia.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/** La bascule locale / API distante ne tient qu'à la configuration : aucune autre ligne ne change. */
class LlmClientSelectionTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(OllamaLlmClient.class, ApiLlmClient.class);

    @Test
    void par_defaut_le_modele_local_est_utilise() {
        contexte.run(ctx -> assertThat(ctx).hasSingleBean(LlmClient.class)
                .getBean(LlmClient.class).isInstanceOf(OllamaLlmClient.class));
    }

    @Test
    void llm_provider_local_choisit_ollama() {
        contexte.withPropertyValues("llm.provider=local")
                .run(ctx -> assertThat(ctx).hasSingleBean(LlmClient.class)
                        .getBean(LlmClient.class).isInstanceOf(OllamaLlmClient.class));
    }

    @Test
    void llm_provider_api_choisit_l_api_distante_avec_la_cle_de_l_environnement() {
        contexte.withPropertyValues("llm.provider=api", "LLM_API_KEY=cle-de-test")
                .run(ctx -> assertThat(ctx).hasSingleBean(LlmClient.class)
                        .getBean(LlmClient.class).isInstanceOf(ApiLlmClient.class));
    }

    @Test
    void llm_provider_api_sans_cle_fait_echouer_le_demarrage_avec_un_message_clair() {
        contexte.withPropertyValues("llm.provider=api")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("LLM_API_KEY"));
    }
}
