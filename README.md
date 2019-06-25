[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# OpenTracing Java Bridge for Zipkin

This library is a Java bridge between the [Brave/Zipkin Api](https://github.com/openzipkin/brave/tree/master/brave#brave-api-v4) and OpenTracing. It allows its users to write portable (in the [OpenTracing](http://opentracing.io) sense) instrumentation that's translated into Brave instrumentation transparently.

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

// If you want to support baggage, indicate the fields you'd like to
// whitelist, in this case "country-code" and "user-id". On the wire,
// they will be prefixed like "baggage-country-code"
propagationFactory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
                                .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
                                .build();

// Now, create a Brave tracing component with the service name you want to see in Zipkin.
//   (the dependency is io.zipkin.brave:brave)
braveTracing = Tracing.newBuilder()
                      .localServiceName("my-service")
                      .propagationFactory(propagationFactory)
                      .spanReporter(spanReporter)
                      .build();

// use this to create an OpenTracing Tracer
tracer = BraveTracer.create(braveTracing);

// You can later unwrap the underlying Brave Api as needed
braveTracer = tracer.unwrap();
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
Releases are uploaded to [Bintray](https://bintray.com/openzipkin/maven/zipkin) and synchronized to [Maven Cen
tral](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.opentracing.brave%22)
### Library Snapshots
Snapshots are uploaded to [JFrog](https://oss.jfrog.org/artifactory/oss-snapshot-local) after commits to maste
r.

   [ci-img]: https://travis-ci.org/openzipkin-contrib/brave-opentracing.svg?branch=master
   [ci]: https://travis-ci.org/openzipkin-contrib/brave-opentracing
   [maven-img]: https://img.shields.io/maven-central/v/io.opentracing.brave/brave-opentracing.svg?maxAge=2592000
   [maven]: http://search.maven.org/#search%7Cga%7C1%7Cbrave-opentracing
