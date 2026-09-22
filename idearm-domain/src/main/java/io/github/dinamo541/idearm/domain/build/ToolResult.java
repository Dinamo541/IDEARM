package io.github.dinamo541.idearm.domain.build;
/** A null exit code means the step did not complete with a trustworthy exit sentinel. */
public record ToolResult(ToolInvocation invocation, Integer exitCode, String output) {}
