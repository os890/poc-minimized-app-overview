# POC: minimized app overview

Compact, value-free exports that describe an application (call flows, JPA mappings, JAX-RS API + CDI beans,
Maven modules, Angular UI) so that its structure can be reconstructed without the original code.
The sources live in the branch [`minimized`](../../tree/minimized): one minimized Java 8 file per topic.
Apart from the CDI interceptor (`jakarta.interceptor`, `jakarta.annotation`, `jakarta.enterprise.util`),
there are no dependencies.

## CDI call recorder (`interceptor`, `core`, `format`)

Records every business method call (incl. observers and JAX-RS resources) of `@Recorded` beans, without values.

```java
@Recorded @ApplicationScoped public class OrderService { ... }

CallRecorder.InMemoryCallRecordSink sink = new CallRecorder.InMemoryCallRecordSink();
CallRecorder.install(sink);                    // default: ServiceLoader sink, -Dcallrecorder.file or in-memory
// ... call the beans ...
System.out.println(CallRecording.CallRecordingReader.read(sink.content()).prettyPrint());
```

## JPA entity mappings (`jpa`)

Exports the mapping graph of a type and all its subtypes on the classpath; the generator rebuilds the entities.

```java
String export = EntityMappingAnalyzer.analyze(BaseEntity.class, "jakarta.validation");
EntityMappingAnalyzer.EntityMappingGenerator.of(export).basePackage("com.acme.model").writeTo(Paths.get("src/main/java"));
```

## JAX-RS API + CDI beans (`jaxrs`)

Exports resources, data types and the tree of injected CDI beans (interceptors, decorators, producers, events).

```java
String export = JaxRsApiAnalyzer.of(ShopApplication.class).keepStereotypes().analyze();
JaxRsApiAnalyzer.JaxRsApiGenerator.of(export).basePackage("com.acme.api").writeTo(Paths.get("src/main/java"));
```

## Maven modules (`maven`)

Exports module names, hierarchy and inter-module dependencies (no GAVs, versions or libraries).

```java
String export = MavenModuleAnalyzer.analyze(new File("pom.xml"), "it");   // profiles optional
MavenModuleAnalyzer.MavenModuleGenerator.of(export).groupId("com.acme").version("1.0.0-SNAPSHOT").writeTo(Paths.get("out"));
```

## Angular UI structure (`angular`)

Exports menus, page tree and per page the forms, controls, dialogs, tabs, steppers and tables (RxJS-aware).

```java
String export = AngularAppAnalyzer.analyze(new File("my-app"));
String full = AngularAppAnalyzer.of(new File("my-app")).withRoutes().withServices().analyze();
```

## License

[Apache License 2.0](LICENSE)
