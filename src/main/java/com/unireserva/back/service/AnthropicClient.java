package com.unireserva.back.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Cliente mínimo del Messages API de Anthropic (sin SDK, solo java.net.http).
 * La API key vive solo en el backend: el navegador nunca la ve.
 */
@Component
public class AnthropicClient {
    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String API_VERSION = "2023-06-01";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.base-url}")
    private String baseUrl;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    @Value("${anthropic.timeout-seconds}")
    private long timeoutSeconds;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /** Una llamada al Messages API. Devuelve el JSON de respuesta (content, stop_reason, ...). */
    public JsonNode createMessage(String system, ArrayNode messages, ArrayNode tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", system);
        body.set("tools", tools);
        body.set("messages", messages);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("Anthropic API respondió {}: {}", response.statusCode(), response.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El asistente no está disponible en este momento. Inténtalo de nuevo en unos minutos.");
            }
            return mapper.readTree(response.body());
        } catch (IOException exception) {
            log.error("Error de comunicación con Anthropic", exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No fue posible comunicarse con el asistente. Inténtalo de nuevo.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "La consulta al asistente fue interrumpida.");
        }
    }
}
