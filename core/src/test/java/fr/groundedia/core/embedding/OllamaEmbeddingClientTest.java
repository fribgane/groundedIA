package fr.groundedia.core.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaEmbeddingClientTest {

    @Test
    void appelle_api_embed_avec_le_modele_et_les_textes_et_rend_un_vecteur_par_texte() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ollama = MockRestServiceServer.bindTo(builder).build();
        ollama.expect(requestTo("http://localhost:11434/api/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("bge-m3"))
                .andExpect(jsonPath("$.input[0]").value("premier texte"))
                .andExpect(jsonPath("$.input[1]").value("second texte"))
                .andRespond(withSuccess("{\"model\":\"bge-m3\",\"embeddings\":[[0.1,0.2,0.3],[0.4,0.5,0.6]]}",
                        MediaType.APPLICATION_JSON));
        var client = new OllamaEmbeddingClient(builder, "http://localhost:11434", "bge-m3");

        List<float[]> vecteurs = client.embed(List.of("premier texte", "second texte"));

        assertThat(vecteurs).hasSize(2);
        assertThat(vecteurs.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vecteurs.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        assertThat(client.modele()).isEqualTo("bge-m3");
        ollama.verify();
    }

    @Test
    void signale_une_reponse_incomplete() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ollama = MockRestServiceServer.bindTo(builder).build();
        ollama.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[[0.1]]}", MediaType.APPLICATION_JSON));
        var client = new OllamaEmbeddingClient(builder, "http://localhost:11434", "bge-m3");

        assertThatThrownBy(() -> client.embed(List.of("un", "deux")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 vecteur(s) pour 2 texte(s)");
    }

    @Test
    void explique_quoi_faire_quand_le_modele_n_est_pas_installe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ollama = MockRestServiceServer.bindTo(builder).build();
        ollama.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"model \\\"bge-m3\\\" not found, try pulling it first\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        var client = new OllamaEmbeddingClient(builder, "http://localhost:11434", "bge-m3");

        assertThatThrownBy(() -> client.embed(List.of("texte")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ollama pull bge-m3");
    }

    @Test
    void explique_quoi_faire_quand_ollama_ne_repond_pas() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ollama = MockRestServiceServer.bindTo(builder).build();
        ollama.expect(requestTo("http://localhost:11434/api/embed"))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));
        var client = new OllamaEmbeddingClient(builder, "http://localhost:11434", "bge-m3");

        assertThatThrownBy(() -> client.embed(List.of("texte")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ollama serve");
    }

    @Test
    void ne_fait_aucun_appel_pour_une_liste_vide() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer ollama = MockRestServiceServer.bindTo(builder).build();
        var client = new OllamaEmbeddingClient(builder, "http://localhost:11434", "bge-m3");

        assertThat(client.embed(List.of())).isEmpty();
        ollama.verify();
    }
}
