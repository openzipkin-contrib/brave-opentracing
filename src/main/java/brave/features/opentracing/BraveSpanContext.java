package brave.features.opentracing;

import brave.propagation.TraceContext;
import io.opentracing.SpanContext;

import java.util.Collections;
import java.util.Map;

/**
 * Created by ddcbdevins on 1/24/17.
 */
public class BraveSpanContext implements SpanContext {

    private TraceContext traceContext;
    private boolean extracted;

    static SpanContext wrap(TraceContext traceContext) {
        return new BraveSpanContext();
    }

    public TraceContext unwrap() {
        return traceContext;
    }

    /**
     * @return all zero or more baggage items propagating along with the associated Span
     * @see Span#setBaggageItem(String, String)
     * @see Span#getBaggageItem(String)
     */
    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        // TODO brave doesn't seem to support baggage yet
        return Collections.EMPTY_LIST;
    }

    public boolean isExtracted() {
        return extracted;
    }

    public void setExtracted(boolean extracted) {
        this.extracted = extracted;
    }
}
