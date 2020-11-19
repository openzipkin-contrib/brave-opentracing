# OpenTracing Java Bridge for Zipkin

[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)
[![Build Status](https://github.com/openzipkin-contrib/brave-opentracing/workflows/test/badge.svg)](https://github.com/openzipkin-contrib/brave-opentracing/actions?query=workflow%3Atest)
[![Maven Central](https://img.shields.io/maven-central/v/io.opentracing.brave/brave-opentracing.svg)](https://search.maven.org/search?q=g:io.opentracing.brave%20AND%20a:brave-opentracing)

This library is a Java bridge between the [Brave/Zipkin Api](https://github.com/openzipkin/brave/tree/master/brave#brave-api-v4) and OpenTracing. It allows its users to write portable (in the [OpenTracing](http://opentracing.io) sense) instrumentation that's translated into Brave instrumentation transparently.

## Compatibility

[opentracing-api](https://github.com/opentracing/opentracing-java) has broken compatibility
on most releases, which limits the ability for this project to provide a large version range.

Here are the versions currently available, noting only the latest version of
opentracing-api is likely to have new work in this repository.

Version | opentracing-api version
--------|-------------------------
0.34.0+ | 0.32.0, 0.33.0
0.33.13 | 0.31.0

## Required Reading

In order to understand OpenTracing Api, one must first be familiar with the [OpenTracing project](http://opentracing.io) and [terminology](https://github.com/opentracing/specification/blob/master/specification.md) more generally.

To understand how Zipkin and Brave work, you can look at [Zipkin Architecture](http://zipkin.io/pages/architecture.html) and [Brave Api](https://github.com/openzipkin/brave/tree/master/brave#brave-api-v4) documentation.

## Setup

Firstly, you need a Tracer, configured to [report to Zipkin](https://github.com/openzipkin/zipkin-reporter-java).

```java
// Configure a reporter, which controls how often spans are sent
//   (the dependency is io.zipkin.reporter2:zipkin-sender-okhttp3)
sender = OkHttpSender.create("http://127.0.0.1:9411/api/v2/spans");
spanReporter = AsyncReporter.create(sender);

// If you want to support baggage, create a field you would like to
// propagate and configure it with `BaggagePropagation`
COUNTRY_CODE = BaggageField.create("country-code");

// Baggage does not need to be sent remotely via headers, but if you configure
// with `addRemoteField()`, it will be
propagationFactory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                                       .addRemoteField(COUNTRY_CODE)
                                       .build()

// Now, create a Brave tracing component with the service name you want to see in Zipkin.
//   (the dependency is io.zipkin.brave:brave)
braveTracing = Tracing.newBuilder()
                      .localServiceName("my-service")
                      .propagationFactory(propagationFactory)
                      .spanReporter(spanReporter)
                      .build();

// use this to create an OpenTracing Tracer
tracer = BraveTracer.create(braveTracing);
countryCode = span.getBaggageItem(COUNTRY_CODE.name());

// You can later unwrap the underlying Brave Api as needed
braveTracing = tracer.unwrap();
countryCode = COUNTRY_CODE.get(span.unwrap().context());
```

Note: If you haven't updated to a server running the [Zipkin v2 api](https://zipkin.io/zipkin-api/#/default/post_spans), you
can use the old Zipkin format like this:

```java
sender = OkHttpSender.json("http://127.0.0.1:9411/api/v1/spans");
spanReporter = AsyncReporter.builder(sender).build(SpanEncoder.JSON_V1);
```
## Artifacts
The artifact published is `brave-opentracing` under the group ID `io.opentracing.brave`

### Library Releases
Releases are at [Sonatype](https://oss.sonatype.org/content/repositories/releases) and [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.opentracing.brave%22)

### Library Snapshots
Snapshots are uploaded to [Sonatype](https://oss.sonatype.org/content/repositories/snapshots) after
commits to master.
