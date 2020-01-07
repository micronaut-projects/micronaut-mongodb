# Micronaut MongoDB

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.configuration/micronaut-mongo-reactive.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.configuration%22%20AND%20a:%22micronaut-mongo-reactive%22)
[![Build Status](https://github.com/micronaut-projects/micronaut-mongodb/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-mongodb/actions)

Integrates Micronaut and MongoDB.

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-mongodb/latest/guide) for more information.

## Snapshots and Releases

Snaphots are automatically published to JFrog OSS using [Github Actions](https://github.com/micronaut-projects/micronaut-mongodb/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to JCenter and Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-mongodb/actions).

A release is performed with the following steps:

* [Edit the version](https://github.com/micronaut-projects/micronaut-mongodb/edit/master/gradle.properties) specified by `projectVersion` in `gradle.properties` to a semantic, unreleased version. Example `1.0.0`
* [Create a new release](https://github.com/micronaut-projects/micronaut-mongodb/releases/new). The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-mongodb/actions?query=workflow%3ARelease) to check it passed successfully.
* Celebrate!
