package dev.ledgerpulse.core.risk;

import dev.ledgerpulse.core.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Component
@EnableConfigurationProperties(RiskEngineProperties.class)
public class RiskEngineHttpClient {

    private final RestClient restClient;

    public RiskEngineHttpClient(RestClient.Builder restClientBuilder, RiskEngineProperties properties) {
        int connectTimeout = properties.connectTimeoutMs() > 0 ? properties.connectTimeoutMs() : 1000;
        int readTimeout = properties.readTimeoutMs() > 0 ? properties.readTimeoutMs() : 1500;
        String baseUrl = StringUtils.hasText(properties.baseUrl()) ? properties.baseUrl() : "http://localhost:8001";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public RiskEvaluateResponseDto evaluate(RiskEvaluateRequestDto request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        RiskEvaluateResponseDto response = restClient.post()
                .uri("/v1/risk/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                .body(request)
                .retrieve()
                .body(RiskEvaluateResponseDto.class);

        if (response == null || response.decision() == null) {
            throw new IllegalStateException("Resposta inválida do risk-engine-ai.");
        }
        return response;
    }
}
