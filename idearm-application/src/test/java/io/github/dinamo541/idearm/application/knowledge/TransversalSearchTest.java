package io.github.dinamo541.idearm.application.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AA-P3-02: Transversal Knowledge Search and Acceptance Case 9")
class TransversalSearchTest {

    private static QueryKnowledge queryEngine;

    @BeforeAll
    static void setUp() {
        queryEngine = new QueryKnowledge();
    }

    @Test
    @DisplayName("Case 9.1: 'dos puntos' and ':' return syntax entries distinguished by type")
    void testSearchDosPuntosAndColon() {
        // Search in Spanish: "dos puntos"
        List<SearchResultGroup> groupsEs = queryEngine.search("dos puntos", "es");
        assertFalse(groupsEs.isEmpty(), "Results must not be empty for 'dos puntos'");
        SearchResultGroup topGroupEs = groupsEs.getFirst();
        assertEquals(KnowledgeEntityKind.SYNTAX, topGroupEs.kind(), "Top group for 'dos puntos' must be SYNTAX");
        assertTrue(topGroupEs.items().stream().anyMatch(i -> i.id().startsWith("syntax.common.colon")),
                "Must return colon syntax entries for 'dos puntos'");

        // Search with literal punctuation: ":"
        List<SearchResultGroup> groupsColon = queryEngine.search(":", "es");
        assertFalse(groupsColon.isEmpty(), "Results must not be empty for ':'");
        SearchResultGroup topGroupColon = groupsColon.getFirst();
        assertEquals(KnowledgeEntityKind.SYNTAX, topGroupColon.kind(), "Top group for ':' must be SYNTAX");
        assertTrue(topGroupColon.items().stream().anyMatch(i -> i.id().startsWith("syntax.common.colon")),
                "Must return colon syntax entries for ':'");
    }

    @Test
    @DisplayName("Case 9.2: 'corchetes' and '[' return syntax entries distinguished by type")
    void testSearchCorchetesAndBrackets() {
        // Search in Spanish: "corchetes"
        List<SearchResultGroup> groupsEs = queryEngine.search("corchetes", "es");
        assertFalse(groupsEs.isEmpty(), "Results must not be empty for 'corchetes'");
        SearchResultGroup topGroupEs = groupsEs.getFirst();
        assertEquals(KnowledgeEntityKind.SYNTAX, topGroupEs.kind(), "Top group for 'corchetes' must be SYNTAX");
        assertTrue(topGroupEs.items().stream().anyMatch(i -> i.id().contains("bracket")),
                "Must return bracket syntax entries for 'corchetes'");

        // Search with literal punctuation: "["
        List<SearchResultGroup> groupsBrk = queryEngine.search("[", "es");
        assertFalse(groupsBrk.isEmpty(), "Results must not be empty for '['");
        SearchResultGroup topGroupBrk = groupsBrk.getFirst();
        assertEquals(KnowledgeEntityKind.SYNTAX, topGroupBrk.kind(), "Top group for '[' must be SYNTAX");
    }

    @Test
    @DisplayName("Case 9.3 & 9.4: 'acarreo' and 'carry' return CF flag and carry instructions")
    void testSearchAcarreoAndCarry() {
        for (String q : List.of("acarreo", "carry")) {
            List<SearchResultGroup> groups = queryEngine.search(q, "es");
            assertFalse(groups.isEmpty(), "Results must not be empty for '" + q + "'");

            boolean hasRegisterOrFlag = groups.stream()
                    .filter(g -> g.kind() == KnowledgeEntityKind.REGISTER)
                    .flatMap(g -> g.items().stream())
                    .anyMatch(i -> i.title().equalsIgnoreCase("FLAGS") || i.title().equalsIgnoreCase("EFLAGS")
                            || i.subtitle().toLowerCase().contains("cf") || i.subtitle().toLowerCase().contains("acarreo"));
            assertTrue(hasRegisterOrFlag, "Search for '" + q + "' must return CF flag / FLAGS register");

            boolean hasInstruction = groups.stream()
                    .filter(g -> g.kind() == KnowledgeEntityKind.INSTRUCTION)
                    .flatMap(g -> g.items().stream())
                    .anyMatch(i -> i.title().equalsIgnoreCase("ADC") || i.title().equalsIgnoreCase("SBB")
                            || i.title().equalsIgnoreCase("STC") || i.title().equalsIgnoreCase("CLC"));
            assertTrue(hasInstruction, "Search for '" + q + "' must return carry instructions (ADC, SBB, STC, CLC)");
        }
    }

    @Test
    @DisplayName("Case 9.5: 'segmento extra' returns ES register")
    void testSearchSegmentoExtra() {
        List<SearchResultGroup> groups = queryEngine.search("segmento extra", "es");
        assertFalse(groups.isEmpty(), "Results must not be empty for 'segmento extra'");

        SearchResultGroup regGroup = groups.stream()
                .filter(g -> g.kind() == KnowledgeEntityKind.REGISTER)
                .findFirst()
                .orElse(null);
        assertNotNull(regGroup, "Must contain REGISTER group");
        assertTrue(regGroup.items().stream().anyMatch(i -> i.title().equalsIgnoreCase("ES")),
                "Must return ES register for 'segmento extra'");
    }

    @Test
    @DisplayName("Case 9.6: 'stack' returns stack registers and instructions")
    void testSearchStack() {
        List<SearchResultGroup> groups = queryEngine.search("stack", "en");
        assertFalse(groups.isEmpty(), "Results must not be empty for 'stack'");

        boolean hasStackReg = groups.stream()
                .filter(g -> g.kind() == KnowledgeEntityKind.REGISTER)
                .flatMap(g -> g.items().stream())
                .anyMatch(i -> i.title().equalsIgnoreCase("SS") || i.title().equalsIgnoreCase("SP") || i.title().equalsIgnoreCase("BP"));
        assertTrue(hasStackReg, "Must return SS, SP, or BP for 'stack'");

        boolean hasStackInstr = groups.stream()
                .filter(g -> g.kind() == KnowledgeEntityKind.INSTRUCTION)
                .flatMap(g -> g.items().stream())
                .anyMatch(i -> i.title().equalsIgnoreCase("PUSH") || i.title().equalsIgnoreCase("POP"));
        assertTrue(hasStackInstr, "Must return PUSH or POP for 'stack'");
    }

    @Test
    @DisplayName("Case 9.7: 'interrupción 21h' returns INT 21h services")
    void testSearchInterrupcion21h() {
        List<SearchResultGroup> groups = queryEngine.search("interrupción 21h", "es");
        assertFalse(groups.isEmpty(), "Results must not be empty for 'interrupción 21h'");

        SearchResultGroup serviceGroup = groups.stream()
                .filter(g -> g.kind() == KnowledgeEntityKind.SERVICE)
                .findFirst()
                .orElse(null);
        assertNotNull(serviceGroup, "Must contain SERVICE group for 'interrupción 21h'");
        assertTrue(serviceGroup.items().stream().anyMatch(i -> i.id().startsWith("dos.int21")),
                "Must return DOS INT 21h services for 'interrupción 21h'");
    }

    @Test
    @DisplayName("Case 9.8: Alias query 'sal' resolves to SHL instruction with INSTRUCTION type")
    void testSearchAliasSal() {
        List<SearchResultGroup> groups = queryEngine.search("sal", "es");
        assertFalse(groups.isEmpty(), "Results must not be empty for alias 'sal'");

        SearchResultGroup instrGroup = groups.stream()
                .filter(g -> g.kind() == KnowledgeEntityKind.INSTRUCTION)
                .findFirst()
                .orElse(null);
        assertNotNull(instrGroup, "Must contain INSTRUCTION group for alias 'sal'");
        assertTrue(instrGroup.items().stream().anyMatch(i -> i.title().equalsIgnoreCase("SHL")),
                "Searching for alias 'sal' must resolve to SHL instruction");
    }
}
