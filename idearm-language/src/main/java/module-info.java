module io.github.dinamo541.idearm.language {
    requires transitive io.github.dinamo541.idearm.domain;

    exports io.github.dinamo541.idearm.language.lexer;
    exports io.github.dinamo541.idearm.language.parser;
    exports io.github.dinamo541.idearm.language.model;
    exports io.github.dinamo541.idearm.language.index;
    exports io.github.dinamo541.idearm.language.catalog;
    exports io.github.dinamo541.idearm.language.linter;
}
