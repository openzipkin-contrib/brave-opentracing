# OpenTracing Java Bridge for Zipkin

This library is a Java bridge between the Brave/Zipkin API and OpenTracing. It allows its users to write portable (in the [OpenTracing](http://opentracing.io) sense) instrumentation that's translated into Brave instrumentation transparently.

## Required Reading

In order to understand OpenTracing API, one must first be familiar with the [OpenTracing project](http://opentracing.io) and [terminology](http://opentracing.io/spec/) more generally.

## Example

Some example code demonstrating how the OpenTracing API is to be used.

Code in the first process…

    BraveTracer tracer = new BraveTracer();

    // start a span
    try ( Span span0 = tracer.buildSpan("span-0")
            .withTag("description", "top level initial span in the original process")
            .start() ) {

        // do something

        // start another span
        try ( Span span1 = tracer.buildSpan("span-1")
                .asChildOf(span0)
                .withTag("description", "the first inner span in the original process")
                .start() ) {

            // do something

            // start another span
            try ( Span span2 = tracer.buildSpan("span-2")
                    .asChildOf(span1)
                    .withTag("description", "the second inner span in the original process")
                    .start() ) {

                // do something

                // cross process boundary
                Map<String,String> map = new HashMap<>();
                tracer.inject(span2.context(), Format.Builtin.HTTP_HEADERS, new TextMapInjectAdapter(map));

                // request.addHeaders(map);
                // request.doGet();
            }
        }
    }

Code in the second process

    // Map<String,String> map = request.getHeaders();

    try ( Span span3 = tracer.buildSpan("span-3")
            .asChildOf(tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(map)))
            .withTag("description", "the third inner span in the second process")
            .start() ) {

        // do something

        // start another span
        try ( Span span4 = tracer.buildSpan("span-4")
                .asChildOf(span3)
                .withTag("description", "the fourth inner span in the second process")
                .start() ) {

            // do something

            // cross process boundary
            map = new HashMap<>();
            tracer.inject(span4.context(), Format.Builtin.HTTP_HEADERS, new TextMapInjectAdapter(map));

            // request.addHeaders(map);
            // request.doGet();
        }
    }

This code repeats from process to process, as far through the stack as required.

## Status

OpenTracing spans created are represented by Brave's local spans. When an OpenTracing span is injected into a protocol's wire a client Brave span is created. Likewise when an OpenTracing span is extracted from a protocol wire a server Brave span is created.

There is a mismatch between the two APIs in how state of the current span is held. OpenTracing does not hold any such state and passes in the
 current span intended to be a parent is an explicit action in the API, while in Brave this state is known and such action not required. OpenTracing's
 expectations are honoured here and Brave's internal state is overridden as needed.

It is noted that it probably would have been simpler to have implemented against lower APIs in brave, like directly against the collector and spans,
 than the top level Brave api. Hopefully this improvement happens in the short-term, otherwise the API usage for the user will not change.
