package brave.features.opentracing;

import brave.propagation.TraceContext;
import io.opentracing.SpanContext;

import java.util.Collections;
import java.util.Map;

public class BraveSpanContext implements SpanContext {

    private TraceContext traceContext;

    static BraveSpanContext wrap(TraceContext traceContext) {
        BraveSpanContext context = new BraveSpanContext();
        context.traceContext = traceContext;
        return context;
    }

    final TraceContext unwrap() {
        return traceContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        // brave doesn't support baggage
        return Collections.EMPTY_SET;
    }
}
