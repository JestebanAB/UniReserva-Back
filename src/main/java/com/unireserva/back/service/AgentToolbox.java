package com.unireserva.back.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unireserva.back.dto.AvailabilityResponse;
import com.unireserva.back.dto.ReservationRequest;
import com.unireserva.back.dto.ReservationResponse;
import com.unireserva.back.dto.ResourceResponse;
import com.unireserva.back.model.ReservationChannel;
import com.unireserva.back.model.ReservationStatus;
import com.unireserva.back.model.ResourceType;

/**
 * Las "manos" del agente. Cada herramienta llama a los MISMOS servicios que usa la web,
 * por lo que las reglas de negocio (horarios, cupos, permisos) no se pueden saltar.
 * Importante: la identidad del usuario sale del JWT, nunca de lo que diga el modelo.
 */
@Component
public class AgentToolbox {

    public record ToolResult(String content, boolean error, boolean mutatedReservations) {
    }

    private final ResourceService resourceService;
    private final ReservationService reservationService;
    private final AnthropicClient anthropicClient;
    private final Clock clock;

    public AgentToolbox(
            ResourceService resourceService,
            ReservationService reservationService,
            AnthropicClient anthropicClient,
            Clock clock) {
        this.resourceService = resourceService;
        this.reservationService = reservationService;
        this.anthropicClient = anthropicClient;
        this.clock = clock;
    }

    private ObjectMapper mapper() {
        return anthropicClient.mapper();
    }

    // ------------------------------------------------------------------
    // Definición de las herramientas (JSON Schema que ve el modelo)
    // ------------------------------------------------------------------
    public ArrayNode definitions() {
        ArrayNode tools = mapper().createArrayNode();

        tools.add(tool("search_resources",
                "Busca espacios y equipos reservables de la universidad (canchas, salas de estudio, auditorios, "
                        + "juegos de la ludoteca...). Úsala para encontrar el resource_id. Sin filtros devuelve todo el catálogo.",
                schema(
                        prop("query", "string", "Texto a buscar en nombre o descripción, por ejemplo 'tenis' o 'ajedrez'"),
                        prop("type", "string", "SPACE (espacio por horario) o EQUIPMENT (equipo por unidades)")),
                List.of()));

        tools.add(tool("get_availability",
                "Devuelve las franjas libres y ocupadas de un recurso en un día. Úsala SIEMPRE antes de proponer "
                        + "un horario o de crear una reserva.",
                schema(
                        prop("resource_id", "integer", "ID del recurso"),
                        prop("date", "string", "Fecha en formato YYYY-MM-DD")),
                List.of("resource_id", "date")));

        tools.add(tool("create_reservation",
                "Crea una reserva a nombre del usuario actual. SOLO llámala después de que el usuario haya confirmado "
                        + "explícitamente recurso, fecha, hora de inicio y hora de fin.",
                schema(
                        prop("resource_id", "integer", "ID del recurso"),
                        prop("start", "string", "Inicio en hora local, formato YYYY-MM-DDTHH:MM (ej. 2026-11-20T14:00)"),
                        prop("end", "string", "Fin en hora local, formato YYYY-MM-DDTHH:MM"),
                        prop("attendees", "integer", "Número de personas (solo espacios)"),
                        prop("quantity", "integer", "Unidades a prestar (solo equipos, por defecto 1)"),
                        prop("notes", "string", "Nota opcional para el administrador")),
                List.of("resource_id", "start", "end")));

        tools.add(tool("list_my_reservations",
                "Lista las reservas activas y próximas (pendientes o confirmadas) del usuario actual.",
                schema(),
                List.of()));

        tools.add(tool("cancel_reservation",
                "Cancela una reserva del usuario actual. SOLO llámala después de que el usuario confirme cuál cancelar.",
                schema(prop("reservation_id", "integer", "ID de la reserva (sale de list_my_reservations)")),
                List.of("reservation_id")));

        return tools;
    }

    // ------------------------------------------------------------------
    // Ejecución
    // ------------------------------------------------------------------
    public ToolResult execute(String name, JsonNode input, String userEmail) {
        try {
            return switch (name) {
                case "search_resources" -> searchResources(input);
                case "get_availability" -> getAvailability(input);
                case "create_reservation" -> createReservation(input, userEmail);
                case "list_my_reservations" -> listMyReservations(userEmail);
                case "cancel_reservation" -> cancelReservation(input, userEmail);
                default -> new ToolResult("Herramienta desconocida: " + name, true, false);
            };
        } catch (ResponseStatusException exception) {
            // Los mensajes de negocio ya están en español y son seguros de mostrar al modelo.
            return new ToolResult(exception.getReason(), true, false);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            return new ToolResult("Parámetros inválidos: " + exception.getMessage(), true, false);
        } catch (RuntimeException exception) {
            return new ToolResult("Ocurrió un error interno al ejecutar la herramienta.", true, false);
        }
    }

    private ToolResult searchResources(JsonNode input) {
        String query = text(input, "query");
        String typeText = text(input, "type");
        ResourceType type = typeText == null ? null : ResourceType.valueOf(typeText.trim().toUpperCase());

        List<ResourceResponse> found = resourceService.search(null, type, query);
        // Si la búsqueda por texto no encontró nada, ofrece el catálogo completo para que el modelo elija.
        boolean fallback = found.isEmpty() && query != null;
        if (fallback) {
            found = resourceService.search(null, type, null);
        }

        ArrayNode array = mapper().createArrayNode();
        for (ResourceResponse r : found) {
            ObjectNode node = array.addObject();
            node.put("resource_id", r.id());
            node.put("name", r.name());
            node.put("category", r.categoryName());
            node.put("type", r.resourceType().name());
            node.put("location", r.location());
            if (r.capacity() != null) {
                node.put("capacity", r.capacity());
            }
            node.put("units_total", r.quantity());
            node.put("opens", r.openTime().toString());
            node.put("closes", r.closeTime().toString());
            node.put("max_duration_minutes", r.maxDurationMinutes());
            node.put("requires_approval", r.requiresApproval());
        }

        ObjectNode result = mapper().createObjectNode();
        result.put("count", array.size());
        if (fallback) {
            result.put("note", "Ningún recurso coincide con la búsqueda; se muestra todo el catálogo.");
        }
        result.set("resources", array);
        return ok(result);
    }

    private ToolResult getAvailability(JsonNode input) {
        Long resourceId = requiredLong(input, "resource_id");
        LocalDate date = LocalDate.parse(requiredText(input, "date"));
        AvailabilityResponse availability = resourceService.getAvailability(resourceId, date);

        ObjectNode result = mapper().createObjectNode();
        result.put("resource", availability.resourceName());
        result.put("date", availability.date().toString());
        result.put("opens", availability.openTime().toString());
        result.put("closes", availability.closeTime().toString());
        result.put("units_total", availability.totalQuantity());
        result.put("max_duration_minutes", availability.maxDurationMinutes());
        ArrayNode free = result.putArray("free_windows");
        availability.freeWindows().forEach(w -> {
            ObjectNode node = free.addObject();
            node.put("from", w.start().toLocalTime().toString());
            node.put("to", w.end().toLocalTime().toString());
            node.put("units_available", w.availableQuantity());
        });
        if (availability.freeWindows().isEmpty()) {
            result.put("note", "No hay franjas libres ese día (o el recurso no está disponible).");
        }
        return ok(result);
    }

    private ToolResult createReservation(JsonNode input, String userEmail) {
        ReservationRequest request = new ReservationRequest(
                requiredLong(input, "resource_id"),
                LocalDateTime.parse(requiredText(input, "start")),
                LocalDateTime.parse(requiredText(input, "end")),
                optionalInt(input, "quantity"),
                optionalInt(input, "attendees"),
                text(input, "notes"));

        ReservationResponse created = reservationService.create(userEmail, request, ReservationChannel.AGENT);

        ObjectNode result = mapper().createObjectNode();
        result.put("reservation_id", created.id());
        result.put("resource", created.resourceName());
        result.put("start", created.startTime().toString());
        result.put("end", created.endTime().toString());
        result.put("status", created.status().name());
        result.put("message", created.status() == ReservationStatus.PENDING
                ? "Reserva registrada, queda PENDIENTE de aprobación por un administrador."
                : "Reserva CONFIRMADA.");
        return new ToolResult(result.toString(), false, true);
    }

    private ToolResult listMyReservations(String userEmail) {
        LocalDateTime now = LocalDateTime.now(clock);
        ArrayNode array = mapper().createArrayNode();
        for (ReservationResponse r : reservationService.getMine(userEmail)) {
            if (!ReservationStatus.OCCUPYING.contains(r.status()) || r.endTime().isBefore(now.minusDays(1))) {
                continue;
            }
            ObjectNode node = array.addObject();
            node.put("reservation_id", r.id());
            node.put("resource", r.resourceName());
            node.put("start", r.startTime().toString());
            node.put("end", r.endTime().toString());
            node.put("status", r.status().name());
        }
        ObjectNode result = mapper().createObjectNode();
        result.put("count", array.size());
        result.set("reservations", array);
        return ok(result);
    }

    private ToolResult cancelReservation(JsonNode input, String userEmail) {
        ReservationResponse cancelled = reservationService.cancel(userEmail, requiredLong(input, "reservation_id"), false);
        ObjectNode result = mapper().createObjectNode();
        result.put("reservation_id", cancelled.id());
        result.put("status", cancelled.status().name());
        result.put("message", "Reserva cancelada.");
        return new ToolResult(result.toString(), false, true);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------
    private ToolResult ok(ObjectNode node) {
        return new ToolResult(node.toString(), false, false);
    }

    private static String text(JsonNode input, String field) {
        JsonNode node = input == null ? null : input.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return node.asText();
    }

    private static String requiredText(JsonNode input, String field) {
        String value = text(input, field);
        if (value == null) {
            throw new IllegalArgumentException("falta el parámetro '" + field + "'");
        }
        return value;
    }

    private static Long requiredLong(JsonNode input, String field) {
        JsonNode node = input == null ? null : input.get(field);
        if (node == null || !node.canConvertToLong()) {
            throw new IllegalArgumentException("falta o es inválido el parámetro '" + field + "'");
        }
        return node.asLong();
    }

    private static Integer optionalInt(JsonNode input, String field) {
        JsonNode node = input == null ? null : input.get(field);
        return node == null || !node.canConvertToInt() ? null : node.asInt();
    }

    private ObjectNode tool(String name, String description, ObjectNode inputSchema, List<String> required) {
        ObjectNode tool = mapper().createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ArrayNode requiredArray = inputSchema.putArray("required");
        required.forEach(requiredArray::add);
        tool.set("input_schema", inputSchema);
        return tool;
    }

    private ObjectNode schema(ObjectNode... properties) {
        ObjectNode schema = mapper().createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        for (ObjectNode property : properties) {
            props.set(property.get("__name").asText(), strip(property));
        }
        return schema;
    }

    private ObjectNode prop(String name, String type, String description) {
        ObjectNode node = mapper().createObjectNode();
        node.put("__name", name);
        node.put("type", type);
        node.put("description", description);
        return node;
    }

    private static ObjectNode strip(ObjectNode property) {
        property.remove("__name");
        return property;
    }
}
