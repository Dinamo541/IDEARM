package io.github.dinamo541.idearm.language.knowledge;

public enum Feature {
    X87("x87 Floating Point Unit"),
    MMX("MultiMedia eXtensions"),
    SSE("Streaming SIMD Extensions"),
    SSE2("Streaming SIMD Extensions 2"),
    SSE3("Streaming SIMD Extensions 3"),
    SSSE3("Supplemental Streaming SIMD Extensions 3"),
    SSE4_1("Streaming SIMD Extensions 4.1"),
    SSE4_2("Streaming SIMD Extensions 4.2"),
    AVX("Advanced Vector Extensions"),
    AVX2("Advanced Vector Extensions 2"),
    AVX512("Advanced Vector Extensions 512"),
    BMI1("Bit Manipulation Instruction Set 1"),
    BMI2("Bit Manipulation Instruction Set 2"),
    ADX("Multi-Precision Add-Carry Instruction Extensions"),
    AESNI("AES New Instructions"),
    SHA("Secure Hash Algorithm Extensions"),
    RDRAND("Read Random Number"),
    RDSEED("Read Random Seed");

    private final String description;

    Feature(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
