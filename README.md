[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# OpenTracing Java Bridge for Zipkin

This library is a Java bridge between the [Brave/Zipkin Api](https://github.com/openzipkin/brave/tree/master/brave#brave-api-v4) and OpenTracing. It allows its users to write portable (in the [OpenTracing](http://opentracing.io) sense) instrumentation that's translated into Brave instrumentation transparently.

## Required Reading

In order to understand OpenTracing Api, one must first be familiar with the [OpenTracing project](http://opentracing.io) and [terminology](http://opentracing.io/spec/) more generally.

To understand how Zipkin and Brave work, you can look at [Zipkin Architecture](http://zipkin.io/pages/architecture.html) and [Brave Api](https://github.com/openzipkin/brave/tree/master/brave#brave-api-v4) documentation.

## Setup

Firstly, you need a Tracer, configured to [report to Zipkin](https://github.com/openzipkin/zipkin-reporter-java).

```java
// Configure a reporter, which controls how often spans are sent
//   (the dependency is io.zipkin.reporter:zipkin-sender-okhttp3)
sender = OkHttpSender.json("http://127.0.0.1:9411/api/v2/spans");
spanReporter = AsyncReporter.v2(sender);

// Now, create a Brave tracing component with the service name you want to see in Zipkin.
//   (the dependency is io.zipkin.brave:brave)
braveTracing = Tracing.newBuilder()
                      .localServiceName("my-service")
                      .spanReporter(spanReporter)
                      .build();

// use this to create an OpenTracing Tracer
tracer = BraveTracer.create(braveTracing);

// You can later unwrap the underlying Brave Api as needed
braveTracer = tracer.unwrap();
```

Note: if you haven't updated to a server running the [Zipkin v2 api](http://zipkin.io/zipkin-api/#/default/post_spans), you
can use the old Zipkin format like this:

```java
sender = OkHttpSender.json("http://127.0.0.1:9411/api/v1/spans");
spanReporter = AsyncReporter.builder(sender).build(SpanEncoder.JSON_V1);
```

## Usage

Some example code demonstrating how the OpenTracing Api is to be used.

Code in the first process…

```java
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
```

Code in the second process

```java
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
```

This code repeats from process to process, as far through the stack as required.

   [ci-img]: https://travis-ci.org/openzipkin-contrib/brave-opentracing.svg?branch=master
   [ci]: https://travis-ci.org/openzipkin-contrib/brave-opentracing
   [maven-img]: https://img.shields.io/maven-central/v/io.opentracing.brave/brave-opentracing.svg?maxAge=2592000
   [maven]: http://search.maven.org/#search%7Cga%7C1%7Cbrave-opentracing
