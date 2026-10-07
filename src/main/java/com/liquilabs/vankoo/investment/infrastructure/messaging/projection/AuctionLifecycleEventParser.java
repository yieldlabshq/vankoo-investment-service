package com.liquilabs.vankoo.investment.infrastructure.messaging.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquilabs.vankoo.investment.domain.model.commands.AuctionLifecycleProjectionEventType;
import com.liquilabs.vankoo.investment.domain.model.commands.ProjectAuctionLifecycleEventCommand;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventContract;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuctionLifecycleEventParser {

    private static final TypeReference<Map<String, Object>> EVENT_DATA_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public AuctionLifecycleEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProjectAuctionLifecycleEventCommand parse(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Auction lifecycle payload is required");
        }
        return parse(new String(payload, StandardCharsets.UTF_8));
    }

    public ProjectAuctionLifecycleEventCommand parse(String payload) {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (envelope == null || !envelope.isObject()) {
                throw new IllegalArgumentException("Auction lifecycle envelope must be a JSON object");
            }

            String eventId = requiredText(envelope, "eventId");
            validateEventId(eventId);
            String eventTypeValue = requiredText(envelope, "eventType");
            AuctionLifecycleProjectionEventType eventType = parseEventType(eventTypeValue);
            int schemaVersion = requiredInt(envelope, "schemaVersion");
            if (schemaVersion != AuctionLifecycleEventContract.SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported auction lifecycle schemaVersion: " + schemaVersion);
            }

            String aggregateId = requiredText(envelope, "aggregateId");
            long sequence = requiredLong(envelope, "sequence");
            if (sequence <= 0) {
                throw new IllegalArgumentException("Auction lifecycle sequence must be positive");
            }

            Instant occurredAt = parseInstant(requiredText(envelope, "occurredAt"));
            JsonNode dataNode = envelope.get("data");
            if (dataNode == null || !dataNode.isObject()) {
                throw new IllegalArgumentException("Auction lifecycle data must be a JSON object");
            }
            Map<String, Object> data = objectMapper.convertValue(dataNode, EVENT_DATA_TYPE);

            return new ProjectAuctionLifecycleEventCommand(
                    eventId,
                    eventType,
                    schemaVersion,
                    aggregateId,
                    sequence,
                    occurredAt,
                    Collections.unmodifiableMap(new LinkedHashMap<>(data)),
                    payload
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid auction lifecycle JSON", exception);
        }
    }

    private static AuctionLifecycleProjectionEventType parseEventType(String eventType) {
        try {
            return AuctionLifecycleProjectionEventType.valueOf(eventType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported auction lifecycle eventType: " + eventType, exception);
        }
    }

    private static void validateEventId(String eventId) {
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Auction lifecycle eventId must be a UUID", exception);
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Auction lifecycle occurredAt must be an ISO-8601 instant", exception);
        }
    }

    private static String requiredText(JsonNode envelope, String fieldName) {
        JsonNode value = envelope.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Auction lifecycle " + fieldName + " is required");
        }
        return value.textValue();
    }

    private static int requiredInt(JsonNode envelope, String fieldName) {
        JsonNode value = envelope.get(fieldName);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Auction lifecycle " + fieldName + " must be an integer");
        }
        return value.intValue();
    }

    private static long requiredLong(JsonNode envelope, String fieldName) {
        JsonNode value = envelope.get(fieldName);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalArgumentException("Auction lifecycle " + fieldName + " must be an integer");
        }
        return value.longValue();
    }
}
