/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris.patch;

import com.google.common.collect.ImmutableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public enum ShaderPatchCompiler {
    ;
    private static final Pattern GLOBAL_DECLARATION = Pattern.compile("^(?:flat\\s+|smooth\\s+|noperspective\\s+|centroid\\s+)*(?:in|out|uniform|varying)\\b.*;");

    public static PatchProgram parse(Reader source) throws IOException, CompileException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        ArrayList<Probe> probes = new ArrayList<>();
        ArrayList<Operation> operations = new ArrayList<>();
        int schemaVersion = 1;
        String line;
        int lineNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(";")) {
                continue;
            }
            if (!trimmed.startsWith("@")) {
                throw new CompileException("Expected directive at line " + lineNumber);
            }

            String[] parts = trimmed.substring(1).split("\\s+");
            String directive = parts[0];
            switch (directive) {
                case "patch" -> {
                    if (parts.length != 2) {
                        throw new CompileException("Invalid patch schema directive at line " + lineNumber);
                    }
                    try {
                        schemaVersion = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        throw new CompileException("Invalid patch schema at line " + lineNumber);
                    }
                    if (schemaVersion != 1 && schemaVersion != 2) {
                        throw new CompileException("Unsupported patch schema " + schemaVersion + " at line " + lineNumber);
                    }
                }
                case "require" -> probes.add(new Probe(ProbeType.REQUIRE_HASH, readHash(parts, 1, lineNumber)));
                case "forbid" -> probes.add(new Probe(ProbeType.FORBID_HASH, readHash(parts, 1, lineNumber)));
                case "require_hash" -> probes.add(new Probe(ProbeType.REQUIRE_HASH, readHash(parts, 1, lineNumber)));
                case "forbid_hash" -> probes.add(new Probe(ProbeType.FORBID_HASH, readHash(parts, 1, lineNumber)));
                case "require_ident" -> probes.add(new Probe(ProbeType.REQUIRE_IDENT, readName(parts, 1, lineNumber)));
                case "forbid_ident" -> probes.add(new Probe(ProbeType.FORBID_IDENT, readName(parts, 1, lineNumber)));
                case "require_function" ->
                        probes.add(new Probe(ProbeType.REQUIRE_FUNCTION, readName(parts, 1, lineNumber)));
                case "forbid_function" ->
                        probes.add(new Probe(ProbeType.FORBID_FUNCTION, readName(parts, 1, lineNumber)));
                case "require_call" -> probes.add(new Probe(ProbeType.REQUIRE_CALL, readName(parts, 1, lineNumber)));
                case "forbid_call" -> probes.add(new Probe(ProbeType.FORBID_CALL, readName(parts, 1, lineNumber)));
                case "require_uniform" ->
                        probes.add(new Probe(ProbeType.REQUIRE_UNIFORM, readName(parts, 1, lineNumber)));
                case "forbid_uniform" ->
                        probes.add(new Probe(ProbeType.FORBID_UNIFORM, readName(parts, 1, lineNumber)));
                case "require_define" ->
                        probes.add(new Probe(ProbeType.REQUIRE_DEFINE, readName(parts, 1, lineNumber)));
                case "forbid_define" -> probes.add(new Probe(ProbeType.FORBID_DEFINE, readName(parts, 1, lineNumber)));
                case "insert_before", "insert_after", "replace_line", "remove_line" -> {
                    Selector selector = readHashSelector(parts, 1, lineNumber);
                    operations.add(new Operation(
                            OperationType.valueOf(directive.toUpperCase(Locale.ROOT)),
                            selector,
                            null,
                            false,
                            directive.equals("remove_line") ? List.of() : readPayload(reader, lineNumber)
                    ));
                }
                case "replace_range", "replace_between" ->
                        operations.add(readHashRangeOperation(parts, reader, lineNumber, directive));
                case "insert_zone" -> operations.add(new Operation(
                        OperationType.INSERT_BEFORE,
                        readZoneSelector(parts, lineNumber),
                        null,
                        false,
                        readPayload(reader, lineNumber)
                ));
                case "insert_before_hash", "insert_after_hash", "replace_line_hash", "remove_line_hash" ->
                        operations.add(readSingleSelectorOperation(parts, reader, lineNumber, directive, SelectorKind.HASH));
                case "insert_before_ident", "insert_after_ident", "replace_line_ident", "remove_line_ident" ->
                        operations.add(readSingleSelectorOperation(parts, reader, lineNumber, directive, SelectorKind.IDENT));
                case "insert_before_function", "replace_line_function", "remove_line_function" ->
                        operations.add(readSingleSelectorOperation(parts, reader, lineNumber, directive, SelectorKind.FUNCTION));
                case "insert_after_function_open" ->
                        operations.add(readSingleSelectorOperation(parts, reader, lineNumber, directive, SelectorKind.FUNCTION_OPEN));
                case "insert_before_define", "insert_after_define", "replace_line_define", "remove_line_define" ->
                        operations.add(readSingleSelectorOperation(parts, reader, lineNumber, directive, SelectorKind.DEFINE));
                case "insert_before_drawbuffers", "insert_after_drawbuffers", "replace_line_drawbuffers",
                     "remove_line_drawbuffers" ->
                        operations.add(readSingleSelectorOperation(parts, reader, lineNumber, directive, SelectorKind.DRAWBUFFERS));
                case "insert_after_stage_begin" ->
                        operations.add(readSingleSelectorOperation(parts, reader, lineNumber, directive, SelectorKind.STAGE_BEGIN));
                default -> throw new CompileException("Unknown directive @" + directive + " at line " + lineNumber);
            }
        }

        if (operations.isEmpty()) {
            throw new CompileException("Patch contains no operations");
        }
        return new PatchProgram(schemaVersion, List.copyOf(probes), List.copyOf(operations));
    }

    public static CompiledPatch compile(PatchProgram program, ImmutableList<String> source) throws CompileException {
        return compile(program, source, CompileMode.PREFLIGHT);
    }

    public static CompiledPatch compile(PatchProgram program, ImmutableList<String> source, CompileMode mode) throws CompileException {
        return compile(program, source, mode, Set.of());
    }

    public static CompiledPatch compile(PatchProgram program, ImmutableList<String> source, CompileMode mode, Set<ShaderStage> stages) throws CompileException {
        if (mode == CompileMode.PREFLIGHT && !containsCombatantPayload(source)) {
            validateNoSourceExcerpt(program, source);
        }

        SourceIndex index = SourceIndex.create(source);
        validateProbes(program, index);

        ArrayList<Edit> edits = new ArrayList<>(program.operations().size());
        for (Operation operation : program.operations()) {
            int anchor = find(index, operation.anchor());
            switch (operation.type()) {
                case INSERT_BEFORE -> edits.add(new Edit(anchor, anchor, operation.payload()));
                case INSERT_AFTER -> edits.add(new Edit(anchor + 1, anchor + 1, operation.payload()));
                case REPLACE_LINE -> edits.add(new Edit(anchor, anchor + 1, operation.payload()));
                case REMOVE_LINE -> edits.add(new Edit(anchor, anchor + 1, List.of()));
                case REPLACE_RANGE -> {
                    int end = find(index, operation.end());
                    if (end < anchor) {
                        throw new CompileException("Range end precedes start");
                    }
                    edits.add(new Edit(anchor, operation.includeEnd() ? end + 1 : end, operation.payload()));
                }
                case REPLACE_BETWEEN -> {
                    int end = find(index, operation.end());
                    if (end <= anchor) {
                        throw new CompileException("Range end does not follow start");
                    }
                    edits.add(new Edit(anchor + 1, end, operation.payload()));
                }
            }
        }

        validateNonOverlapping(edits);
        validateStageBounds(edits, source, stages);
        validateDelimiterDelta(edits, source);
        edits.sort(Comparator.comparingInt(Edit::start)
                .thenComparingInt(Edit::end)
                .reversed());
        return new CompiledPatch(List.copyOf(edits));
    }

    public static ImmutableList<String> apply(CompiledPatch patch, ImmutableList<String> source) {
        ArrayList<String> output = new ArrayList<>(source);
        for (Edit edit : patch.edits()) {
            output.subList(edit.start(), edit.end()).clear();
            output.addAll(edit.start(), edit.payload());
        }
        return ImmutableList.copyOf(output);
    }

    public static String signature(String sourceLine) {
        String normalized = normalizeLine(sourceLine);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Operation readHashRangeOperation(String[] parts, BufferedReader reader, int lineNumber, String directive) throws IOException, CompileException {
        if (parts.length < 3 || parts.length > 6) {
            throw new CompileException("Invalid " + directive + " at line " + lineNumber);
        }
        Selector start = new Selector(
                SelectorKind.HASH,
                validateHash(parts[1], lineNumber),
                readPositive(parts, 3, 1, lineNumber),
                parts.length <= 3
        );
        Selector end = new Selector(
                SelectorKind.HASH,
                validateHash(parts[2], lineNumber),
                readPositive(parts, 4, 1, lineNumber),
                parts.length <= 4
        );
        boolean includeEnd = parts.length >= 6 && Boolean.parseBoolean(parts[5]);
        return new Operation(
                OperationType.valueOf(directive.toUpperCase(Locale.ROOT)),
                start,
                end,
                includeEnd,
                readPayload(reader, lineNumber)
        );
    }

    private static Operation readSingleSelectorOperation(String[] parts,
                                                         BufferedReader reader,
                                                         int lineNumber,
                                                         String directive,
                                                         SelectorKind kind) throws IOException, CompileException {
        OperationType operationType = operationType(directive);
        Selector selector = readSelector(parts, kind, lineNumber);
        return new Operation(
                operationType,
                selector,
                null,
                false,
                operationType == OperationType.REMOVE_LINE ? List.of() : readPayload(reader, lineNumber)
        );
    }

    private static OperationType operationType(String directive) throws CompileException {
        if (directive.startsWith("insert_before")) return OperationType.INSERT_BEFORE;
        if (directive.startsWith("insert_after")) return OperationType.INSERT_AFTER;
        if (directive.startsWith("replace_line")) return OperationType.REPLACE_LINE;
        if (directive.startsWith("remove_line")) return OperationType.REMOVE_LINE;
        throw new CompileException("Unsupported operation directive @" + directive);
    }

    private static Selector readZoneSelector(String[] parts, int lineNumber) throws CompileException {
        if (parts.length != 2) {
            throw new CompileException("Invalid insert_zone directive at line " + lineNumber);
        }
        String zone = parts[1].toLowerCase(Locale.ROOT);
        if (!zone.equals("declarations") && !zone.equals("functions")) {
            throw new CompileException("Unsupported insert_zone " + parts[1] + " at line " + lineNumber);
        }
        return new Selector(SelectorKind.ZONE, zone, 1, false);
    }

    private static Selector readSelector(String[] parts, SelectorKind kind, int lineNumber) throws CompileException {
        if (kind == SelectorKind.DRAWBUFFERS) {
            if (parts.length < 1 || parts.length > 2) {
                throw new CompileException("Invalid drawbuffers selector at line " + lineNumber);
            }
            return new Selector(SelectorKind.DRAWBUFFERS, "DRAWBUFFERS", readPositive(parts, 1, 1, lineNumber), parts.length == 1);
        }
        if (parts.length < 2 || parts.length > 3) {
            throw new CompileException("Invalid selector at line " + lineNumber);
        }
        String value = kind == SelectorKind.HASH ? validateHash(parts[1], lineNumber) : validateName(parts[1], lineNumber);
        return new Selector(kind, value, readPositive(parts, 2, 1, lineNumber), parts.length == 2);
    }

    private static Selector readHashSelector(String[] parts, int signatureIndex, int lineNumber) throws CompileException {
        if (parts.length < signatureIndex + 1 || parts.length > signatureIndex + 2) {
            throw new CompileException("Invalid hash anchor at line " + lineNumber);
        }
        return new Selector(
                SelectorKind.HASH,
                validateHash(parts[signatureIndex], lineNumber),
                readPositive(parts, signatureIndex + 1, 1, lineNumber),
                parts.length == signatureIndex + 1
        );
    }

    private static String readHash(String[] parts, int index, int lineNumber) throws CompileException {
        if (parts.length != index + 1) {
            throw new CompileException("Invalid hash directive at line " + lineNumber);
        }
        return validateHash(parts[index], lineNumber);
    }

    private static String readName(String[] parts, int index, int lineNumber) throws CompileException {
        if (parts.length != index + 1) {
            throw new CompileException("Invalid identifier directive at line " + lineNumber);
        }
        return validateName(parts[index], lineNumber);
    }

    private static String validateHash(String hash, int lineNumber) throws CompileException {
        String normalized = hash.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new CompileException("Invalid structural hash at line " + lineNumber);
        }
        return normalized;
    }

    private static String validateName(String name, int lineNumber) throws CompileException {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new CompileException("Invalid identifier '" + name + "' at line " + lineNumber);
        }
        return name;
    }

    private static int readPositive(String[] parts, int index, int fallback, int lineNumber) throws CompileException {
        if (parts.length <= index) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(parts[index]);
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new CompileException("Invalid occurrence at line " + lineNumber);
        }
    }

    private static List<String> readPayload(BufferedReader reader, int directiveLine) throws IOException, CompileException {
        ArrayList<String> payload = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().equals("@end")) {
                return List.copyOf(payload);
            }
            payload.add(line);
        }
        throw new CompileException("Unterminated payload after line " + directiveLine);
    }

    private static void validateProbes(PatchProgram program, SourceIndex index) throws CompileException {
        ArrayList<String> missing = new ArrayList<>();
        ArrayList<String> forbidden = new ArrayList<>();
        for (Probe probe : program.probes()) {
            boolean present = switch (probe.type()) {
                case REQUIRE_HASH, FORBID_HASH -> index.availableHashes().contains(probe.value());
                case REQUIRE_IDENT, FORBID_IDENT -> hasIdentifier(index.source(), probe.value());
                case REQUIRE_FUNCTION, FORBID_FUNCTION ->
                        findFunctionStart(index.source(), probe.value(), 1, false) >= 0;
                case REQUIRE_CALL, FORBID_CALL -> hasCall(index.source(), probe.value());
                case REQUIRE_UNIFORM, FORBID_UNIFORM -> hasUniform(index.source(), probe.value());
                case REQUIRE_DEFINE, FORBID_DEFINE -> hasDefine(index.source(), probe.value());
            };
            if (probe.type().required() && !present) {
                missing.add(probe.type().label() + " " + probe.value());
            } else if (!probe.type().required() && present) {
                forbidden.add(probe.type().label() + " " + probe.value());
            }
        }
        if (!missing.isEmpty()) {
            throw new CompileException("Required structural probes are absent: " + String.join(", ", missing));
        }
        if (!forbidden.isEmpty()) {
            throw new CompileException("Forbidden structural probes are present: " + String.join(", ", forbidden));
        }
    }

    private static int find(SourceIndex index, Selector selector) throws CompileException {
        return switch (selector.kind()) {
            case HASH -> findHash(index.hashes(), selector);
            case IDENT -> findLine(index.source(), selector, line -> hasIdentifier(line, selector.value()));
            case FUNCTION -> findFunctionStartChecked(index.source(), selector);
            case FUNCTION_OPEN -> findFunctionOpenChecked(index.source(), selector);
            case DEFINE -> findLine(index.source(), selector, line -> isDefine(line, selector.value()));
            case DRAWBUFFERS -> findLine(index.source(), selector, line -> line.contains("DRAWBUFFERS"));
            case STAGE_BEGIN -> findLine(index.source(), selector, line -> isStageBegin(line, selector.value()));
            case ZONE -> findZone(index.source(), selector.value());
        };
    }

    private static int findHash(List<String> hashes, Selector selector) throws CompileException {
        int occurrence = 0;
        int result = -1;
        for (int i = 0; i < hashes.size(); i++) {
            if (!hashes.get(i).equals(selector.value())) {
                continue;
            }
            occurrence++;
            if (occurrence == selector.occurrence()) {
                result = i;
            }
        }
        if (result < 0) {
            throw new CompileException("Anchor not found: hash " + selector.value() + "#" + selector.occurrence());
        }
        if (selector.requireUnique() && occurrence != 1) {
            throw new CompileException("Anchor is ambiguous; specify occurrence: hash " + selector.value());
        }
        return result;
    }

    private static int findLine(List<String> source, Selector selector, LinePredicate predicate) throws CompileException {
        int occurrence = 0;
        int result = -1;
        for (int i = 0; i < source.size(); i++) {
            if (!predicate.matches(source.get(i))) {
                continue;
            }
            occurrence++;
            if (occurrence == selector.occurrence()) {
                result = i;
            }
        }
        if (result < 0) {
            throw new CompileException("Anchor not found: " + selector.kind().name().toLowerCase(Locale.ROOT) + " " + selector.value() + "#" + selector.occurrence());
        }
        if (selector.requireUnique() && occurrence != 1) {
            throw new CompileException("Anchor is ambiguous; specify occurrence: " + selector.kind().name().toLowerCase(Locale.ROOT) + " " + selector.value());
        }
        return result;
    }

    private static int findFunctionStartChecked(List<String> source, Selector selector) throws CompileException {
        int index = findFunctionStart(source, selector.value(), selector.occurrence(), selector.requireUnique());
        if (index < 0) {
            throw new CompileException("Anchor not found: function " + selector.value() + "#" + selector.occurrence());
        }
        return index;
    }

    private static int findFunctionOpenChecked(List<String> source, Selector selector) throws CompileException {
        int start = findFunctionStartChecked(source, new Selector(SelectorKind.FUNCTION, selector.value(), selector.occurrence(), selector.requireUnique()));
        for (int i = start; i < source.size(); i++) {
            String line = stripLineComment(source.get(i));
            int brace = line.indexOf('{');
            int semicolon = line.indexOf(';');
            if (brace >= 0 && (semicolon < 0 || brace < semicolon)) {
                return i;
            }
            if (semicolon >= 0) {
                break;
            }
        }
        throw new CompileException("Function opening brace not found: " + selector.value() + "#" + selector.occurrence());
    }

    private static int findFunctionStart(List<String> source, String functionName, int requestedOccurrence, boolean requireUnique) {
        int occurrence = 0;
        int result = -1;
        for (int i = 0; i < source.size(); i++) {
            if (!looksLikeFunctionStart(source.get(i), functionName)) {
                continue;
            }
            occurrence++;
            if (occurrence == requestedOccurrence) {
                result = i;
            }
        }
        if (result < 0) {
            return -1;
        }
        if (requireUnique && occurrence != 1) {
            return -2;
        }
        return result;
    }

    private static int findZone(List<String> source, String zone) {
        if (zone.equals("declarations")) {
            for (int i = 0; i < source.size(); i++) {
                String trimmed = source.get(i).trim();
                if (GLOBAL_DECLARATION.matcher(trimmed).matches()) {
                    return i;
                }
            }
        }
        for (int i = 0; i < source.size(); i++) {
            if (looksLikeAnyFunctionStart(source.get(i))) {
                return i;
            }
        }
        return 0;
    }

    private static boolean isStageBegin(String line, String stageMacro) {
        String stripped = stripLineComment(line).trim();
        return stripped.equals("#ifdef " + stageMacro) || stripped.equals("#if defined " + stageMacro) || stripped.equals("#if defined(" + stageMacro + ")");
    }

    private static boolean looksLikeAnyFunctionStart(String line) {
        String stripped = stripLineComment(line).trim();
        if (stripped.isEmpty() || stripped.startsWith("#")) return false;
        return stripped.matches(".*\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*") && !stripped.endsWith(";");
    }

    private static boolean looksLikeFunctionStart(String line, String functionName) {
        String stripped = stripLineComment(line).trim();
        if (stripped.isEmpty() || stripped.startsWith("#")) return false;
        if (!hasIdentifier(stripped, functionName)) return false;
        int nameIndex = stripped.indexOf(functionName);
        int paren = stripped.indexOf('(', nameIndex + functionName.length());
        if (paren < 0) return false;
        int semicolon = stripped.indexOf(';');
        return semicolon < 0 || semicolon > paren;
    }

    private static boolean hasCall(List<String> source, String name) {
        String needle = name + "(";
        for (String line : source) {
            String stripped = stripLineComment(line);
            if (stripped.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUniform(List<String> source, String name) {
        for (String line : source) {
            String stripped = stripLineComment(line).trim();
            if (stripped.startsWith("uniform ") && hasIdentifier(stripped, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDefine(List<String> source, String name) {
        for (String line : source) {
            if (isDefine(line, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDefine(String line, String name) {
        String stripped = stripLineComment(line).trim();
        if (!stripped.startsWith("#define")) return false;
        String rest = stripped.substring("#define".length()).trim();
        if (rest.startsWith(name)) {
            return rest.length() == name.length() || !isIdentifierPart(rest.charAt(name.length()));
        }
        return false;
    }

    private static boolean hasIdentifier(List<String> source, String identifier) {
        for (String line : source) {
            if (hasIdentifier(line, identifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIdentifier(String line, String identifier) {
        String stripped = stripLineComment(line);
        int from = 0;
        while (from < stripped.length()) {
            int index = stripped.indexOf(identifier, from);
            if (index < 0) return false;
            int before = index - 1;
            int after = index + identifier.length();
            boolean validBefore = before < 0 || !isIdentifierPart(stripped.charAt(before));
            boolean validAfter = after >= stripped.length() || !isIdentifierPart(stripped.charAt(after));
            if (validBefore && validAfter) {
                return true;
            }
            from = index + identifier.length();
        }
        return false;
    }

    private static boolean isIdentifierPart(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static String stripLineComment(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private static boolean containsCombatantPayload(List<String> source) {
        for (String line : source) {
            if (line.contains("combatant") || line.contains("COMBATANT_")) {
                return true;
            }
        }
        return false;
    }

    private static void validateNoSourceExcerpt(PatchProgram program, List<String> source) throws CompileException {
        List<String> normalizedSource = source.stream().map(ShaderPatchCompiler::normalizeLine).toList();
        for (Operation operation : program.operations()) {
            List<String> payload = operation.payload().stream().map(ShaderPatchCompiler::normalizeLine).toList();
            for (int payloadStart = 0; payloadStart + 2 < payload.size(); payloadStart++) {
                if (payload.get(payloadStart).isEmpty()
                        || payload.get(payloadStart + 1).isEmpty()
                        || payload.get(payloadStart + 2).isEmpty()) {
                    continue;
                }
                for (int sourceStart = 0; sourceStart + 2 < normalizedSource.size(); sourceStart++) {
                    if (payload.get(payloadStart).equals(normalizedSource.get(sourceStart))
                            && payload.get(payloadStart + 1).equals(normalizedSource.get(sourceStart + 1))
                            && payload.get(payloadStart + 2).equals(normalizedSource.get(sourceStart + 2))) {
                        throw new CompileException("Patch payload reproduces a target source excerpt");
                    }
                }
            }
        }
    }

    private static String normalizeLine(String sourceLine) {
        String source = sourceLine == null ? "" : sourceLine;
        StringBuilder normalized = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char character = source.charAt(i);
            if (!Character.isWhitespace(character)) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private static void validateNonOverlapping(List<Edit> edits) throws CompileException {
        ArrayList<Edit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end));
        int previousEnd = -1;
        for (Edit edit : sorted) {
            if (edit.start() < previousEnd) {
                throw new CompileException("Patch operations overlap");
            }
            previousEnd = Math.max(previousEnd, edit.end());
        }
    }

    private static void validateStageBounds(List<Edit> edits, List<String> source, Set<ShaderStage> stages) throws CompileException {
        if (stages == null || stages.isEmpty() || stages.contains(ShaderStage.ANY) || !sourceHasStageBlocks(source)) {
            return;
        }
        for (Edit edit : edits) {
            ShaderStage stage = stageBeforeLine(source, edit.start());
            if (stage == null || !stages.contains(stage)) {
                throw new CompileException("Patch edit escapes allowed shader stage(s): editLine=" + (edit.start() + 1) + " stage=" + (stage == null ? "none" : stage.name().toLowerCase(Locale.ROOT)) + " allowed=" + stages);
            }
        }
    }

    private static boolean sourceHasStageBlocks(List<String> source) {
        for (String line : source) {
            String stripped = stripLineComment(line).trim();
            if (stripped.contains("FRAGMENT_SHADER") || stripped.contains("VERTEX_SHADER") || stripped.contains("GEOMETRY_SHADER") || stripped.contains("COMPUTE_SHADER")) {
                return true;
            }
        }
        return false;
    }

    private static ShaderStage stageBeforeLine(List<String> source, int lineIndex) {
        ArrayList<ShaderStage> stack = new ArrayList<>();
        int end = Math.min(Math.max(lineIndex, 0), source.size());
        for (int i = 0; i < end; i++) {
            String stripped = stripLineComment(source.get(i)).trim();
            ShaderStage begin = stageFromDirective(stripped);
            if (begin != null) {
                stack.add(begin);
            } else if (stripped.startsWith("#endif") && !stack.isEmpty()) {
                stack.remove(stack.size() - 1);
            }
        }
        for (int i = stack.size() - 1; i >= 0; i--) {
            ShaderStage stage = stack.get(i);
            if (stage != ShaderStage.ANY) {
                return stage;
            }
        }
        return null;
    }

    private static ShaderStage stageFromDirective(String stripped) {
        if (!stripped.startsWith("#if")) {
            return null;
        }
        if (stripped.contains("FRAGMENT_SHADER")) return ShaderStage.FRAGMENT;
        if (stripped.contains("VERTEX_SHADER")) return ShaderStage.VERTEX;
        if (stripped.contains("GEOMETRY_SHADER")) return ShaderStage.GEOMETRY;
        if (stripped.contains("COMPUTE_SHADER")) return ShaderStage.COMPUTE;
        return null;
    }

    private static void validateDelimiterDelta(List<Edit> edits, List<String> source) throws CompileException {
        for (char open : new char[]{'{', '(', '['}) {
            char close = switch (open) {
                case '{' -> '}';
                case '(' -> ')';
                case '[' -> ']';
                default -> throw new IllegalStateException();
            };
            int delta = 0;
            for (Edit edit : edits) {
                delta += delimiterBalance(edit.payload(), open, close);
                delta -= delimiterBalance(source.subList(edit.start(), edit.end()), open, close);
            }
            if (delta != 0) {
                throw new CompileException("Patch changes " + open + close + " delimiter balance");
            }
        }
    }

    private static int delimiterBalance(List<String> lines, char open, char close) {
        int balance = 0;
        for (String line : lines) {
            for (int i = 0; i < line.length(); i++) {
                char character = line.charAt(i);
                if (character == open) balance++;
                if (character == close) balance--;
            }
        }
        return balance;
    }

    public enum CompileMode {
        PREFLIGHT,
        APPLICATION
    }

    public enum ShaderStage {
        ANY,
        FRAGMENT,
        VERTEX,
        GEOMETRY,
        COMPUTE;

        public static ShaderStage parse(String value) throws CompileException {
            if (value == null || value.isBlank()) {
                return ANY;
            }
            try {
                return ShaderStage.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new CompileException("Unsupported shader stage '" + value + "'");
            }
        }
    }

    public enum ProbeType {
        REQUIRE_HASH(true, "hash"),
        FORBID_HASH(false, "hash"),
        REQUIRE_IDENT(true, "ident"),
        FORBID_IDENT(false, "ident"),
        REQUIRE_FUNCTION(true, "function"),
        FORBID_FUNCTION(false, "function"),
        REQUIRE_CALL(true, "call"),
        FORBID_CALL(false, "call"),
        REQUIRE_UNIFORM(true, "uniform"),
        FORBID_UNIFORM(false, "uniform"),
        REQUIRE_DEFINE(true, "define"),
        FORBID_DEFINE(false, "define");

        private final boolean required;
        private final String label;

        ProbeType(boolean required, String label) {
            this.required = required;
            this.label = label;
        }

        private boolean required() {
            return required;
        }

        private String label() {
            return label;
        }
    }

    public enum SelectorKind {
        HASH,
        IDENT,
        FUNCTION,
        FUNCTION_OPEN,
        DEFINE,
        DRAWBUFFERS,
        STAGE_BEGIN,
        ZONE
    }

    public enum OperationType {
        INSERT_BEFORE,
        INSERT_AFTER,
        REPLACE_LINE,
        REMOVE_LINE,
        REPLACE_RANGE,
        REPLACE_BETWEEN
    }

    private interface LinePredicate {
        boolean matches(String line);
    }

    public record PatchProgram(
            int schemaVersion,
            List<Probe> probes,
            List<Operation> operations
    ) {
    }

    public record CompiledPatch(List<Edit> edits) {
    }

    public record Edit(int start, int end, List<String> payload) {
    }

    public record Probe(ProbeType type, String value) {
    }

    public record Selector(SelectorKind kind, String value, int occurrence, boolean requireUnique) {
    }

    public record Operation(
            OperationType type,
            Selector anchor,
            Selector end,
            boolean includeEnd,
            List<String> payload
    ) {
    }

    private record SourceIndex(
            ImmutableList<String> source,
            List<String> hashes,
            Set<String> availableHashes
    ) {
        private static SourceIndex create(ImmutableList<String> source) {
            ArrayList<String> hashes = new ArrayList<>(source.size());
            Set<String> available = new HashSet<>(source.size());
            for (String line : source) {
                String hash = signature(line);
                hashes.add(hash);
                available.add(hash);
            }
            return new SourceIndex(source, List.copyOf(hashes), Set.copyOf(available));
        }
    }

    public static final class CompileException extends Exception {
        public CompileException(String message) {
            super(message);
        }
    }
}
