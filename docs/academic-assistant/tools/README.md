# Audit tools for the academic assistant plan

Two throwaway programs used to produce the measured numbers in
[`../annex-a-audit-and-inventory.md`](../annex-a-audit-and-inventory.md). They are **not** production code
and are not part of any Maven module: they read only the public API of
`io.github.dinamo541.idearm.language.catalog`, so they can be compiled against that package alone.

They exist so the counts in the plan can be reproduced instead of trusted.

## Run them

From the repository root, with a scratch directory of your choice as `$OUT`:

```bash
# 1. Compile the catalog package as it stands in the working tree.
javac -d "$OUT/cat-classes" \
      idearm-language/src/main/java/io/github/dinamo541/idearm/language/catalog/*.java

# 2. Compile and run the inventory.
javac -cp "$OUT/cat-classes" -d "$OUT" docs/academic-assistant/tools/Inventory.java
java  -cp "$OUT/cat-classes;$OUT" Inventory          # ';' on Windows, ':' on Linux

# 3. Compile and run the false-positive check.
javac -cp "$OUT/cat-classes" -d "$OUT" docs/academic-assistant/tools/FalsePositives.java
java  -cp "$OUT/cat-classes;$OUT" FalsePositives
```

`Inventory` prints entry counts, alias resolution, flag-table shapes, the behaviour of the CPU filter, what the
search finds for concept and punctuation queries, and `suggest()` samples.

`FalsePositives` prints which real x86 mnemonics the catalog does not know and what it would suggest instead. The
list of mnemonics it checks is hand-written from the families named in the plan; extend it when the corpus grows.

## The static-initializer measurement

The 9406-byte figure in the annex comes from the same compiled class:

```bash
javap -c -p "$OUT/cat-classes/io/github/dinamo541/idearm/language/catalog/InstructionCatalog.class"
```

The last bytecode offset inside `static {}` is the number quoted; the per-method limit of the class file format is
65535 bytes.
