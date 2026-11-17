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
 * Cliente mínimo del API de Gemini (generateContent), sin SDK, solo java.net.http.
 * La API key vive solo en el backend: el navegador nunca la ve.
 */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.max-tokens}")
    private int maxTokens;

    @Value("${gemini.timeout-seconds}")
    private long timeoutSeconds;

    @Value("${gemini.fallback-model:gemini-3.5-flash}")
    private String fallbackModel;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * Una llamada a generateContent. Devuelve el JSON de respuesta (candidates, promptFeedback...).
     *
     * @param system   instrucciones del sistema
     * @param contents historial en formato Gemini: [{role: user|model, parts: [...]}]
     * @param tools    herramientas en formato Gemini: [{functionDeclarations: [...]}]
     */
        public JsonNode generateContent(String system, ArrayNode contents, ArrayNode tools) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("system_instruction").putArray("parts").addObject().put("text", system);
        body.set("contents", contents);
        body.set("tools", tools);
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("maxOutputTokens", maxTokens);
        generationConfig.putObject("thinkingConfig").put("thinkingLevel", "low");

        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Intentos 1 y 2: modelo principal. Intento 3: modelo de respaldo.
            String currentModel = attempt < maxAttempts ? model : fallbackModel;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1beta/models/" + currentModel + ":generateContent"))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .header("content-type", "application/json")
                        .header("x-goog-api-key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status / 100 == 2) {
                    return mapper.readTree(response.body());
                }
                if ((status == 503 || status == 500) && attempt < maxAttempts) {
                    log.warn("Gemini ({}) respondió {}, reintentando (intento {}/{})", currentModel, status, attempt, maxAttempts);
                    Thread.sleep(1500L * attempt);
                    continue;
                }
                if (status == 429) {
                    log.warn("Gemini API: límite de uso alcanzado: {}", response.body());
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "El asistente alcanzó su límite de uso gratuito por ahora. Espera un minuto e inténtalo de nuevo.");
                }

                log.error("Gemini API ({}) respondió {}: {}", currentModel, status, response.body());
                String detalle;
                try {
                    detalle = mapper.readTree(response.body()).path("error").path("message").asText("");
                } catch (Exception e) {
                    detalle = "";
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "El asistente no está disponible (Gemini " + status + "): " + detalle);
            } catch (IOException exception) {
                log.error("Error de comunicación con Gemini", exception);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "No fue posible comunicarse con el asistente ("+ exception.getClass().getSimpleName() + ": " + exception.getMessage() + ").");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "La consulta al asistente fue interrumpida.");
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El asistente no está disponible. Inténtalo de nuevo.");
    }
}
