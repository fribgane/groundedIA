package fr.groundedia.eval;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Le client de l'application évaluée : {@code POST /ask}, exactement comme un appelant externe. Le harnais ne voit
 * rien d'autre que ce que l'API expose ; c'est voulu, il mesure le système tel qu'il est livré.
 */
public class AskClient {

    private final String baseUrl;
    private final RestClient http;

    public AskClient(String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(5));
        // une réponse générée sur processeur prend une à trois minutes ; on laisse une marge large
        fabrique.setReadTimeout(Duration.ofMinutes(15));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrique).build();
    }

    /** Échoue tout de suite, avec la marche à suivre, si l'application n'est pas démarrée ou pas saine. */
    public void verifierDisponible() {
        try {
            String sante = http.get().uri("/actuator/health").retrieve().body(String.class);
            if (sante == null || !sante.contains("UP")) {
                throw new IllegalStateException("L'application sur " + baseUrl + " répond mais n'est pas saine : " + sante);
            }
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("L'application sur " + baseUrl + " répond " + e.getStatusCode().value()
                    + " à /actuator/health : vérifier la base de données (docker compose ps) ; corps : " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Application injoignable sur " + baseUrl + " : lancer ./mvnw spring-boot:run "
                    + "(ou indiquer son adresse avec -Deval.base-url=http://hote:port)", e);
        }
    }

    public ReponseAsk demander(String question, Long salarieId) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("question", question);
        if (salarieId != null) {
            corps.put("salarieId", salarieId);
        }
        return http.post().uri("/ask").contentType(MediaType.APPLICATION_JSON).body(corps)
                .retrieve().body(ReponseAsk.class);
    }
}
