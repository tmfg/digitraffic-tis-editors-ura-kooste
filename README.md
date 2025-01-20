###### Digitraffic / Travel Information Services

# URA / Kooste

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/kura-0.1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

## Troubleshooting

### `esbuild` missing when running tests/dev mode

If you get a stacktrace like the one below complaining about missing executable:

```
java.lang.RuntimeException:
 java.lang.RuntimeException:
  io.quarkus.builder.BuildException:
   Build failure: Build failed due to errors
    [error]: Build step io.quarkiverse.web.bundler.deployment.BundlingProcessor#bundle threw an exception:
     java.io.UncheckedIOException:
      java.io.IOException:
       Cannot run program "/var/folders/nj/fnq0l71j37v94vqm0241gn9r0000gq/T/esbuild-0.23.0-mvnpm-0.0.8/package/bin/esbuild" (in directory "/Users/{USER_HOME}/code/Fintraffic/digitraffic-tis-editors-ura-kooste/target/web-bundler/test"): error=2, No such file or directory
```

This is caused by temporary file/cache mismatch between the used tooling, e.g. you try to run tests from IntelliJ 
IDEA while you've been running devmode in terminal.

To resolve this, navigate to the parent directory of the tool and simply force remove all of it:
```shell
(cd /var/folders/nj/fnq0l71j37v94vqm0241gn9r0000gq/T/ && rm -r esbuild-0.23.0-mvnpm-0.0.8)
```

---

Copyright Fintraffic 2023-2025. Licensed under the EUPL-1.2 or later.
