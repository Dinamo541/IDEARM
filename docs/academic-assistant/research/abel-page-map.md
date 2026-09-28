# Research Note: Abel Source Readability & Topic Mapping (AA-P0-02)

> **Date:** 2026-09-25  
> **Status:** Analyzed and Documented Limits  
> **Target Work:** Peter Abel, *Lenguaje Ensamblador y Programación para IBM PC y Compatibles*, 3.ª edición (283 pp.)

---

## 1. Environment Assessment & Physical Extraction Limits

- **Document State:** The local file is a legacy rasterized/scanned PDF without an embedded text layer or embedded font outlines.
- **Extraction Test:** Executing `pdftotext` on a 20-page sample yielded only 20 bytes of whitespace/header artifacts.
- **OCR Tooling Availability:** The runtime environment lacks optical character recognition engines (`tesseract`, `pdftoppm`, ghostscript, or ImageMagick). Python environment does not contain installed raster/OCR bindings.
- **Methodological Rule:** In strict accordance with the Academic Assistant charter, **no technical assertions may be made based on unverified citations**.

---

## 2. Topic Mapping and Primary Authority Delegation

Because Abel cannot be parsed via automated OCR in this session, topics originally slated for Abel are primary-delegated to verifiable text-bearing authorities:

| Topic Intended for Abel | Secondary / Authoritative Replacement | Specific Section & Page |
|---|---|---|
| **Real Mode Memory Segmentation** | William Stallings, 7.ª ed. / Kip R. Irvine, 5.ª ed. | Stallings §11.2 (pp. 415–417); Irvine §2.2 (pp. 34–38) |
| **Historical IBM PC Architecture & Bus** | William Stallings, 7.ª ed. | Stallings §3.4 «Estructuras de interconexión del bus» |
| **DOS Memory Models (Tiny, Small, Medium, Compact, Large)** | Kip R. Irvine, 5.ª ed. | Irvine §8.2.8 «Modelos de memoria» (pp. 250–252) |
| **Keyboard Input & INT 21h Function 0Ah** | Kip R. Irvine, 5.ª ed. / Ralf Brown's Interrupt List | Irvine §13.2.3 (pp. 443–445); RBIL Table 01344 |
| **Video Display & INT 10h BIOS Services** | Kip R. Irvine, 5.ª ed. (Appendix C) | Irvine Apéndice C «Interrupciones del BIOS y de MS-DOS» (pp. 650–658) |
| **A20 Gate Historical Context** | Intel SDM Revision 093, Vol. 3A | Intel SDM Vol. 3A §20.1.4 «Address Wraparound and A20M#» |

---

## 3. Mano Highlighted Copy Note

- The marked copy of M. Morris Mano, *Diseño Digital* 3.ª ed. contains visual highlights indicating course scope.
- In accordance with the project instructions, highlighted exclusions represent curricular context, not an instruction to omit architectural fundamentals from the IDE assistant. All foundational concepts (two's complement, binary arithmetic, flip-flops) remain fully documented.
