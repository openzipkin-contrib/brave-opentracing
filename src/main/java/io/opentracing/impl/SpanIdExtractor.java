package io.opentracing.impl;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;

import java.util.Map;
import java.util.Optional;

public class SpanIdExtractor {
    public static Optional<SpanId> toBraveId(Map<String, Object> traceState) {
        Long traceId = toLong(traceState.get(BraveHttpHeaders.TraceId.getName()));
        Long spanId = toLong(traceState.get(BraveHttpHeaders.SpanId.getName()));
        Long parentSpanId = toLong(traceState.get(BraveHttpHeaders.ParentSpanId.getName()));
        
        validateTraceState(traceId, spanId);
        
        if(traceId == null) {
            return Optional.empty();
        } else {
            SpanId braveSpanId = SpanId.builder()
                .traceId(traceId) // TODO support traceHighId
                .spanId(spanId)
                .parentId(parentSpanId)
                .build();
            return Optional.of(braveSpanId);
        }
    }
    
    private static void validateTraceState(Long traceId, Long spanId) {
        if (traceId != null && spanId == null) {
            throw new IllegalArgumentException("Invalid trace state. TraceId present but SpanId missing");
        } else if (traceId == null && spanId != null) {
            throw new IllegalArgumentException("Invalid trace state. TraceId missing but SpanId present");
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            return IdConversion.convertToLong(value.toString());
        }
    }
}
