package io.github.dinamo541.idearm.domain.build;
public record ProcessResult(int exitCode, String output, boolean cancelled, boolean timedOut) {}
