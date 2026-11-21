package dev.ledgerpulse.core.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.ledgerpulse.core.web.DomainValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    public record IdempotencyExecution<T>(
            T body,
            int httpStatus,
            boolean replayed
    ) {
    }

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final Duration ttl;

    public IdempotencyService(
            IdempotencyStore store,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.ttl-hours:24}") long ttlHours
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.canonicalMapper = JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
        this.ttl = Duration.ofHours(ttlHours);
    }

    public <T> IdempotencyExecution<T> execute(
            UUID merchantScopeId,
            String idempotencyKey,
            Object canonicalPayload,
            int successStatus,
            Class<T> responseType,
            Supplier<T> action
    ) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.trim().length() > 128) {
            throw new DomainValidationException("Header Idempotency-Key é obrigatório e deve ter entre 1 e 128 caracteres.");
        }
        String compositeKey = merchantScopeId + ":" + idempotencyKey.trim();
        String requestHash = hashCanonicalPayload(canonicalPayload);

        if (store.tryAcquireInProgress(compositeKey, requestHash, ttl)) {
            return runAndCache(compositeKey, requestHash, successStatus, action);
        }

        StoredIdempotencyRecord existing = store.find(compositeKey).orElse(null);
        if (existing == null && store.tryAcquireInProgress(compositeKey, requestHash, ttl)) {
            return runAndCache(compositeKey, requestHash, successStatus, action);
        }
        if (existing == null) {
            throw new IdempotencyConflictException("Requisição idempotente concorrente em conflito.");
        }
        if (!requestHash.equals(existing.requestHash())) {
            throw new IdempotencyConflictException("Idempotency-Key já utilizada anteriormente com payload divergente.");
        }
        if (existing.state() == StoredIdempotencyRecord.State.IN_PROGRESS) {
            throw new IdempotencyConflictException("Requisição idempotente concorrente ainda está em processamento.");
        }

        try {
            T cachedBody = objectMapper.readValue(existing.responseJson(), responseType);
            int status = existing.httpStatus() != null ? existing.httpStatus() : successStatus;
            return new IdempotencyExecution<>(cachedBody, status, true);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao desserializar resposta idempotente em cache.", ex);
        }
    }

    private <T> IdempotencyExecution<T> runAndCache(
            String compositeKey,
            String requestHash,
            int successStatus,
            Supplier<T> action
    ) {
        try {
            T result = action.get();
            String responseJson = objectMapper.writeValueAsString(result);
            store.saveCompleted(compositeKey, requestHash, successStatus, responseJson, ttl);
            return new IdempotencyExecution<>(result, successStatus, false);
        } catch (RuntimeException | Error ex) {
            store.release(compositeKey);
            throw ex;
        } catch (JsonProcessingException ex) {
            store.release(compositeKey);
            throw new IllegalStateException("Falha ao serializar resposta para cache de idempotência.", ex);
        }
    }

    public String hashCanonicalPayload(Object payload) {
        try {
            byte[] jsonBytes = canonicalMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(jsonBytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Falha ao calcular hash SHA-256 do payload canônico.", ex);
        }
    }
}
