package fr.groundedia.core.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ApiLlmClientTest {

    @Test
    void appelle_chat_completions_avec_la_cle_en_en_tete_et_lit_les_jetons() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer api = MockRestServiceServer.bindTo(builder).build();
        api.expect(requestTo("https://api.exemple.fr/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer cle-de-test"))
                .andExpect(jsonPath("$.model").value("modele-distant"))
                .andExpect(jsonPath("$.temperature").value(0.0))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("question"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Réponse.\"}}],"
                        + "\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":7,\"total_tokens\":107}}",
                        MediaType.APPLICATION_JSON));
        var client = new ApiLlmClient(builder, "https://api.exemple.fr/v1", "modele-distant", "cle-de-test");

        var generation = client.generer("consigne", "question");

        assertThat(generation.texte()).isEqualTo("Réponse.");
        assertThat(generation.tokensPrompt()).isEqualTo(100);
        assertThat(generation.tokensReponse()).isEqualTo(7);
        assertThat(client.fournisseur()).isEqualTo("api");
        api.verify();
    }

    @Test
    void refuse_de_demarrer_sans_cle() {
        assertThatThrownBy(() -> new ApiLlmClient(RestClient.builder(), "https://api.exemple.fr/v1", "m", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_API_KEY");
    }

    @Test
    void signale_une_cle_refusee() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer api = MockRestServiceServer.bindTo(builder).build();
        api.expect(requestTo("https://api.exemple.fr/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"invalid key\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        var client = new ApiLlmClient(builder, "https://api.exemple.fr/v1", "m", "mauvaise-cle");

        assertThatThrownBy(() -> client.generer("consigne", "question"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_API_KEY refusée");
    }
}
