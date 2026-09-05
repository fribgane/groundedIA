package fr.groundedia.core.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

class OllamaLlmClientTest {

    @Test
    void appelle_api_chat_avec_la_consigne_le_message_et_une_generation_deterministe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ollama = MockRestServiceServer.bindTo(builder).build();
        ollama.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("consigne"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("question"))
                .andExpect(jsonPath("$.options.temperature").value(0))
                .andExpect(jsonPath("$.options.seed").value(42))
                .andExpect(jsonPath("$.options.num_ctx").value(8192))
                .andRespond(withSuccess("{\"model\":\"qwen2.5:3b\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"Quatre jours [doc.pdf, Article 5.7, p. 26].\"},\"done\":true,"
                        + "\"prompt_eval_count\":1234,\"eval_count\":21}", MediaType.APPLICATION_JSON));
        var client = new OllamaLlmClient(builder, "http://localhost:11434", "qwen2.5:3b");

        var generation = client.generer("consigne", "question");

        assertThat(generation.texte()).isEqualTo("Quatre jours [doc.pdf, Article 5.7, p. 26].");
        assertThat(generation.tokensPrompt()).isEqualTo(1234);
        assertThat(generation.tokensReponse()).isEqualTo(21);
        assertThat(generation.tokensTotal()).isEqualTo(1255);
        assertThat(client.fournisseur()).isEqualTo("local");
        assertThat(client.modele()).isEqualTo("qwen2.5:3b");
        ollama.verify();
    }

    @Test
    void explique_quoi_faire_quand_le_modele_n_est_pas_installe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ollama = MockRestServiceServer.bindTo(builder).build();
        ollama.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"model \\\"qwen2.5:3b\\\" not found\"}").contentType(MediaType.APPLICATION_JSON));
        var client = new OllamaLlmClient(builder, "http://localhost:11434", "qwen2.5:3b");

        assertThatThrownBy(() -> client.generer("consigne", "question"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ollama pull qwen2.5:3b");
    }
}
