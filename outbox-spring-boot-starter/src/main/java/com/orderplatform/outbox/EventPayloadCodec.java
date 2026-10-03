package com.orderplatform.outbox;

import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts Avro events to the JSON stored in the outbox and back.
 *
 * <p>JSON (rather than the broker wire format) is stored so the table stays readable for humans
 * and so a schema that evolved since the row was written can still be decoded. The mapping from
 * schema name to class is explicit configuration: it keeps the relay free of classpath scanning
 * and makes an unmapped event type fail fast at startup instead of at the first publish.
 */
public class EventPayloadCodec {

    private final Map<String, Class<? extends SpecificRecordBase>> eventTypes;
    private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

    public EventPayloadCodec(Map<String, Class<? extends SpecificRecordBase>> eventTypes) {
        this.eventTypes = Collections.unmodifiableMap(eventTypes);
        this.eventTypes.keySet().forEach(this::schemaOf);
    }

    public String toJson(SpecificRecordBase event) {
        // SpecificRecordBase.toString() is the JSON encoding: it uses the Jackson mapper Avro
        // ships with, so logical types survive the round trip through fromJson().
        return event.toString();
    }

    public SpecificRecordBase fromJson(String eventType, String payload) {
        Class<? extends SpecificRecordBase> type = eventTypes.get(eventType);
        if (type == null) {
            throw new IllegalArgumentException("No outbox mapping registered for event type '" + eventType + "'");
        }
        Schema schema = schemaOf(eventType);
        try {
            JsonDecoder decoder = DecoderFactory.get().jsonDecoder(schema, payload);
            return (SpecificRecordBase) new SpecificDatumReader<SpecificRecord>(schema).read(null, decoder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot decode outbox payload of type " + eventType, e);
        }
    }

    public String eventTypeOf(SpecificRecordBase event) {
        return event.getSchema().getName();
    }

    private Schema schemaOf(String eventType) {
        return schemas.computeIfAbsent(eventType, name -> {
            Class<? extends SpecificRecordBase> type = eventTypes.get(name);
            try {
                return type.getDeclaredConstructor().newInstance().getSchema();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Event type '" + name + "' (" + type.getName() + ") needs a public no-arg constructor", e);
            }
        });
    }
}
