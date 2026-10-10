/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Build-time GLSL -> Java std140 / std430 binding generator. Standalone JDK source launcher:
 * java tools/uniform-codegen/UniformCodegen.java generate <shader-root> <manifest> <java-output-root> [metadata-output-root]
 * java tools/uniform-codegen/UniformCodegen.java discover <shader-root> <metadata-output-root>
 * java tools/uniform-codegen/UniformCodegen.java test
 *
 * The manifest contains one shader path per line. Add #BlockName only when that shader has
 * multiple explicit std140/std430 blocks. Generated classes are named BlockNameBinding.
 */
public final class UniformCodegen {
    private static final String PACKAGE = "combatant.client.render.engine.uniform.generated";
    private static final Pattern BLOCK = Pattern.compile("\\blayout\\s*\\(([^)]*)\\)\\s*(?:(?:readonly|writeonly|coherent|restrict|volatile)\\s+)*(uniform|buffer)\\s+(\\w+)\\s*\\{");
    private static final Pattern STRUCT = Pattern.compile("\\bstruct\\s+(\\w+)\\s*\\{([^{}]*)}\\s*;", Pattern.DOTALL);
    private static final Pattern MEMBER = Pattern.compile("^(\\w+)\\s+(\\w+)\\s*(?:\\[\\s*([^]]+)\\s*])?$");
    private static final Pattern DEFINE = Pattern.compile("(?m)^\\s*#define\\s+(\\w+)\\s+([^\\r\\n]+)");
    private static final Pattern INTEGER_CONSTANT = Pattern.compile("\\bconst\\s+int\\s+(\\w+)\\s*=\\s*([^;]+);");
    private static final Pattern LOCAL_IMPORT = Pattern.compile("(?m)^\\s*#moj_import\\s+<combatant:([A-Za-z0-9_./-]+)>[ \t]*$");
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private UniformCodegen() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("test")) {
            selfTest();
            System.out.println("UniformCodegen: all layout tests passed");
        } else if (args.length == 3 && args[0].equals("discover")) {
            discover(Path.of(args[1]), Path.of(args[2]));
        } else if (args.length == 3 && args[0].equals("verify")) {
            verifyBindings(Path.of(args[1]), Path.of(args[2]));
        } else if ((args.length == 4 || args.length == 5) && args[0].equals("generate")) {
            generate(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]),
                    args.length == 5 ? Path.of(args[4]) : null);
        } else {
            throw new IllegalArgumentException("Usage: discover <shaders-dir> <metadata-output-dir> | verify <metadata-file> <java-source-root> | generate <shaders-dir> <manifest> <generated-java-dir> [metadata-output-dir] | test");
        }
    }

    private static void generate(Path root, Path manifest, Path output, Path metadataOutput) throws IOException {
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("Missing shader directory " + root);
        if (!Files.isRegularFile(manifest)) throw new IllegalArgumentException("Missing uniform binding manifest " + manifest);
        root = root.toAbsolutePath().normalize();
        Path packageDir = output.resolve(PACKAGE.replace('.', '/'));
        Files.createDirectories(packageDir);
        Map<String, Path> classes = new LinkedHashMap<>();
        Set<Path> generated = new HashSet<>();
        Set<String> entries = new HashSet<>();
        List<Binding> bindings = new ArrayList<>();
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1).trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            int separator = line.indexOf('#');
            String relativeText = (separator < 0 ? line : line.substring(0, separator)).trim();
            String blockName = separator < 0 ? null : line.substring(separator + 1).trim();
            if (relativeText.isEmpty() || separator >= 0 && (blockName.isEmpty() || blockName.indexOf('#') >= 0)) {
                throw new IllegalArgumentException(manifest + ":" + lineNumber + ": expected shader/path or shader/path#BlockName");
            }
            Path relative = Path.of(relativeText).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IllegalArgumentException(manifest + ":" + lineNumber + ": shader must be relative to " + root);
            }
            String entryKey = relative.toString() + "#" + (blockName == null ? "" : blockName);
            if (!entries.add(entryKey)) throw new IllegalArgumentException(manifest + ":" + lineNumber + ": duplicate entry " + line);
            Path shader = root.resolve(relative).normalize();
            if (!shader.startsWith(root) || !Files.isRegularFile(shader)) {
                throw new IllegalArgumentException(manifest + ":" + lineNumber + ": missing shader " + relative);
            }
            String source = readShader(shader, root, new HashSet<>());
            Binding binding = parse(source, shader.toString(), blockName);
            bindings.add(binding);
            Path previous = classes.putIfAbsent(binding.className, shader);
            if (previous != null) {
                throw new IllegalArgumentException("Generated class '" + binding.className + "' selected from both " + previous + " and " + shader);
            }
            Path target = packageDir.resolve(binding.className + ".java");
            String java = emit(binding);
            if (!Files.exists(target) || !Files.readString(target, StandardCharsets.UTF_8).equals(java)) {
                Files.writeString(target, java, StandardCharsets.UTF_8);
            }
            generated.add(target);
            System.out.println(binding.className + " <= " + root.relativize(shader) + "#" + binding.blockName
                    + " (" + binding.standard + ", " + binding.layout.size + " bytes)");
        }
        // Delete stale generated bindings when the manifest changes. Only own package.
        try (Stream<Path> paths = Files.list(packageDir)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (!generated.contains(file)) Files.delete(file);
            }
        }
        if (classes.isEmpty()) throw new IllegalArgumentException("No uniform bindings declared in " + manifest);
        if (metadataOutput != null) writeMetadata(metadataOutput,
                bindings.stream().map(binding -> new MetadataBinding(binding.blockName, binding)).toList());
    }

    private static void discover(Path root, Path metadataOutput) throws IOException {
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("Missing shader directory " + root);
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Map<String, Binding> byName = new LinkedHashMap<>();
        Map<String, String> origins = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(absoluteRoot)) {
            for (Path shader : paths.filter(Files::isRegularFile)
                    .filter(UniformCodegen::isShaderSource).sorted().toList()) {
                String relative = absoluteRoot.relativize(shader).toString().replace('\\', '/');
                String source = readShader(shader, absoluteRoot, new HashSet<>());
                for (Binding binding : parseAll(source, shader.toString())) {
                    String schemaKey = binding.blockName + "@" + binding.standard.name().toLowerCase(Locale.ROOT);
                    Binding previous = byName.putIfAbsent(schemaKey, binding);
                    if (previous != null && !signature(previous).equals(signature(binding))) {
                        if (layoutSubset(previous.layout, binding.layout)) {
                            byName.put(schemaKey, binding);
                            origins.put(schemaKey, relative);
                        } else if (!layoutSubset(binding.layout, previous.layout)) {
                            throw new IllegalArgumentException("Incompatible GLSL layout for block " + schemaKey
                                    + " in " + origins.get(schemaKey) + " and " + relative
                                    + ". Shared blocks may omit trailing members but cannot change offsets/types.");
                        }
                    }
                    origins.putIfAbsent(schemaKey, relative);
                }
            }
        }
        if (byName.isEmpty()) throw new IllegalArgumentException("No explicit std140/std430 blocks found under " + root);
        List<MetadataBinding> metadata = new ArrayList<>();
        for (Binding binding : byName.values()) {
            String otherStandard = binding.blockName + "@" + (binding.standard == Standard.STD140 ? "std430" : "std140");
            boolean ambiguous = byName.containsKey(otherStandard);
            String key = ambiguous && binding.standard == Standard.STD430
                    ? binding.blockName + "@std430" : binding.blockName;
            metadata.add(new MetadataBinding(key, binding));
        }
        writeMetadata(metadataOutput, metadata);
        System.out.println("Discovered " + byName.size() + " uniform layouts");
    }

    /** Expands local Mojang shader imports for declaration analysis, including array constants. */
    private static String readShader(Path shader, Path root, Set<Path> visiting) throws IOException {
        shader = shader.toAbsolutePath().normalize();
        if (!shader.startsWith(root) || !Files.isRegularFile(shader)) {
            throw new IllegalArgumentException("Shader import is missing or outside shader root: " + shader);
        }
        if (!visiting.add(shader)) throw new IllegalArgumentException("Recursive GLSL import: " + shader);
        try {
            String contents = Files.readString(shader, StandardCharsets.UTF_8);
            Matcher matcher = LOCAL_IMPORT.matcher(contents);
            StringBuilder expanded = new StringBuilder();
            while (matcher.find()) {
                Path imported = root.resolve("include").resolve(matcher.group(1)).normalize();
                matcher.appendReplacement(expanded, Matcher.quoteReplacement("\n" + readShader(imported, root, visiting) + "\n"));
            }
            matcher.appendTail(expanded);
            return expanded.toString();
        } finally {
            visiting.remove(shader);
        }
    }

    /** Fails the Java build when a constant CPU binding references a GLSL-absent field. */
    private static void verifyBindings(Path metadataFile, Path sourceRoot) throws IOException {
        if (!Files.isRegularFile(metadataFile)) throw new IllegalArgumentException("Missing generated metadata " + metadataFile);
        if (!Files.isDirectory(sourceRoot)) throw new IllegalArgumentException("Missing Java source root " + sourceRoot);
        Map<String, Map<String, String>> contracts = new LinkedHashMap<>();
        String block = null;
        for (String line : Files.readAllLines(metadataFile, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\\t", -1);
            if (fields[0].equals("block")) {
                if (fields.length != 5 || contracts.containsKey(fields[1])) throw new IllegalArgumentException("Bad block in metadata: " + line);
                block = fields[1];
                contracts.put(block, new LinkedHashMap<>());
            } else if (fields[0].equals("member")) {
                if (block == null || fields.length != 6) throw new IllegalArgumentException("Bad metadata member: " + line);
                if (contracts.get(block).putIfAbsent(fields[1], fields[2]) != null)
                    throw new IllegalArgumentException("Duplicate member in generated metadata: " + block + "." + fields[1]);
            } else if (fields[0].equals("end")) {
                if (block == null) throw new IllegalArgumentException("Stray metadata block end");
                block = null;
            } else if (!line.isBlank() && !line.startsWith("COMBATANT_UNIFORM_BINDINGS\t")) {
                throw new IllegalArgumentException("Unexpected metadata: " + line);
            }
        }
        if (block != null || contracts.isEmpty()) throw new IllegalArgumentException("Incomplete or empty generated uniform metadata");
        Pattern declaredBlock = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*=\\s*ShaderUniformBindings\\.block\\(\\s*\"([^\"]+)\"\\s*\\)");
        Pattern declaredWriter = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*=\\s*([A-Za-z_]\\w*)\\.writer\\(\\)");
        Pattern selectedWriter = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*=\\s*[^;]*?\\?\\s*([A-Za-z_]\\w*)\\s*:\\s*([A-Za-z_]\\w*)\\s*;");
        Pattern write = Pattern.compile("\\.(vec4|mat4)\\s*\\(\\s*\"([^\"]+)\"");
        Pattern memberRead = Pattern.compile("\\b([A-Za-z_]\\w*)\\.member\\(\\s*\"([^\"]+)\"");
        int checked = 0;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path java : paths.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String source = stripComments(Files.readString(java, StandardCharsets.UTF_8));
                if (!source.contains("ShaderUniformBindings")) continue;
                Map<String, String> blocks = new LinkedHashMap<>();
                Matcher matcher = declaredBlock.matcher(source);
                while (matcher.find()) {
                    if (!contracts.containsKey(matcher.group(2))) {
                        throw new IllegalArgumentException(java + ": unknown shader block " + matcher.group(2));
                    }
                    blocks.put(matcher.group(1), matcher.group(2));
                    checked++;
                }
                Map<String, String> writers = new LinkedHashMap<>();
                matcher = declaredWriter.matcher(source);
                while (matcher.find()) {
                    String owner = blocks.get(matcher.group(2));
                    if (owner == null) throw new IllegalArgumentException(java + ": cannot resolve writer " + matcher.group(1));
                    writers.put(matcher.group(1), owner);
                }
                matcher = selectedWriter.matcher(source);
                while (matcher.find()) {
                    String left = writers.get(matcher.group(2)), right = writers.get(matcher.group(3));
                    if (left != null && left.equals(right)) writers.put(matcher.group(1), left);
                }
                matcher = memberRead.matcher(source);
                while (matcher.find()) {
                    String contract = blocks.get(matcher.group(1));
                    if (contract != null) {
                        if (!contracts.get(contract).containsKey(matcher.group(2))) {
                            throw new IllegalArgumentException(java + ": " + contract + "." + matcher.group(2) + " is missing");
                        }
                        checked++;
                    }
                }
                matcher = write.matcher(source);
                while (matcher.find()) {
                    int start = matcher.start();
                    int afterBoundary = Math.max(source.lastIndexOf(';', start),
                            Math.max(source.lastIndexOf('{', start), source.lastIndexOf('}', start))) + 1;
                    String expression = source.substring(afterBoundary, start);
                    String binding = null;
                    int lastPosition = -1;
                    for (Map.Entry<String, String> writer : writers.entrySet()) {
                        Matcher reference = Pattern.compile("\\b" + Pattern.quote(writer.getKey()) + "\\b").matcher(expression);
                        while (reference.find()) {
                            if (reference.start() >= lastPosition) {
                                binding = writer.getValue();
                                lastPosition = reference.start();
                            }
                        }
                    }
                    if (binding == null) {
                        throw new IllegalArgumentException(java + ": cannot resolve generated writer for ."
                                + matcher.group(1) + "(\"" + matcher.group(2) + "\")");
                    }
                    String actual = contracts.get(binding).get(matcher.group(2));
                    if (!matcher.group(1).equals(actual)) {
                        throw new IllegalArgumentException(java + ": " + binding + "." + matcher.group(2)
                                + " is " + (actual == null ? "missing" : actual) + ", but written as " + matcher.group(1));
                    }
                    checked++;
                }
            }
        }
        if (checked == 0) throw new IllegalArgumentException("No Java uniform bindings were verified");
        System.out.println("Verified " + checked + " static Java GLSL binding references");
    }

    private static boolean isShaderSource(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".vert") || name.endsWith(".frag") || name.endsWith(".geom")
                || name.endsWith(".comp") || name.endsWith(".glsl");
    }

    private static List<Binding> parseAll(String source, String file) {
        String withoutComments = stripComments(source);
        Matcher matcher = BLOCK.matcher(withoutComments);
        ArrayList<String> names = new ArrayList<>();
        while (matcher.find()) {
            String layout = matcher.group(1);
            if (Pattern.compile("(?:^|,)\\s*std(?:140|430)\\s*(?:,|$)").matcher(layout).find()) {
                String name = matcher.group(3);
                if (!names.contains(name)) names.add(name);
            }
        }
        ArrayList<Binding> bindings = new ArrayList<>(names.size());
        for (String name : names) bindings.add(parse(source, file, name));
        return bindings;
    }

    private static boolean layoutSubset(Layout smaller, Layout larger) {
        if (smaller.size > larger.size || smaller.members.size() > larger.members.size()) return false;
        for (int i = 0; i < smaller.members.size(); i++) {
            Member a = smaller.members.get(i), b = larger.members.get(i);
            if (!a.name.equals(b.name) || !a.type.equals(b.type) || a.offset != b.offset
                    || a.count != b.count || a.array != b.array || a.stride != b.stride
                    || (a.nested == null) != (b.nested == null)) return false;
            if (a.nested != null && !layoutSubset(a.nested, b.nested)) return false;
        }
        return true;
    }

    private static String signature(Binding binding) {
        StringBuilder value = new StringBuilder(binding.standard.name()).append(':').append(binding.layout.size);
        appendSignature(value, binding.layout);
        return value.toString();
    }

    private static void appendSignature(StringBuilder value, Layout layout) {
        for (Member member : layout.members) {
            value.append('|').append(member.name).append(':').append(member.type).append(':')
                    .append(member.offset).append(':').append(member.count).append(':').append(member.stride);
            if (member.nested != null) appendSignature(value, member.nested);
        }
    }

    private static void writeMetadata(Path output, List<MetadataBinding> bindings) throws IOException {
        Path target = output.resolve("combatant/uniform-bindings-v2.tsv");
        Files.createDirectories(target.getParent());
        // Remove artifacts left by older generator versions so they cannot be packaged accidentally.
        Files.deleteIfExists(target.resolveSibling("uniform-bindings-v1.tsv"));
        StringBuilder text = new StringBuilder("COMBATANT_UNIFORM_BINDINGS\t2\n");
        for (MetadataBinding metadata : bindings) {
            Binding binding = metadata.binding;
            text.append("block\t").append(metadata.key).append('\t').append(binding.blockName).append('\t')
                    .append(binding.standard.name().toLowerCase(Locale.ROOT)).append('\t')
                    .append(binding.layout.size).append('\n');
            for (Member member : binding.layout.members) {
                if (member.nested != null) {
                    throw new IllegalArgumentException(binding.source + ": struct metadata requires an explicit runtime schema extension: " + member.name);
                }
                text.append("member\t").append(member.name).append('\t').append(member.type).append('\t')
                        .append(member.offset).append('\t').append(member.array ? member.count : 1).append('\t')
                        .append(member.array ? member.stride : 0).append('\n');
            }
            text.append("end\n");
        }
        String value = text.toString();
        if (!Files.exists(target) || !Files.readString(target, StandardCharsets.UTF_8).equals(value)) {
            Files.writeString(target, value, StandardCharsets.UTF_8);
        }
    }

    private static Binding parse(String source, String file, String requestedBlock) {
        Map<String, String> defines = new HashMap<>();
        Matcher defineMatcher = DEFINE.matcher(source);
        while (defineMatcher.find()) {
            String value = defineMatcher.group(2).replaceAll("//.*$", "").trim();
            if (!value.isEmpty() && defines.putIfAbsent(defineMatcher.group(1), value) != null
                    && !defines.get(defineMatcher.group(1)).equals(value)) {
                throw new IllegalArgumentException(file + ": conflicting definitions for #define " + defineMatcher.group(1));
            }
        }
        String withoutComments = stripComments(source);
        Matcher integerMatcher = INTEGER_CONSTANT.matcher(withoutComments);
        while (integerMatcher.find()) {
            String name = integerMatcher.group(1);
            String value = integerMatcher.group(2).trim();
            if (defines.containsKey(name) && !defines.get(name).equals(value)) {
                throw new IllegalArgumentException(file + ": conflicting GLSL integer constant " + name);
            }
            defines.put(name, value);
        }
        Map<String, List<Member>> structs = new LinkedHashMap<>();
        Matcher structMatcher = STRUCT.matcher(withoutComments);
        while (structMatcher.find()) {
            String name = structMatcher.group(1);
            if (structs.putIfAbsent(name, parseMembers(structMatcher.group(2), defines, file)) != null)
                throw new IllegalArgumentException(file + ": duplicate GLSL struct " + name);
        }
        List<Binding> candidates = new ArrayList<>();
        Matcher block = BLOCK.matcher(withoutComments);
        while (block.find()) {
            String layoutText = block.group(1);
            boolean std140 = Pattern.compile("(?:^|,)\\s*std140\\s*(?:,|$)").matcher(layoutText).find();
            boolean std430 = Pattern.compile("(?:^|,)\\s*std430\\s*(?:,|$)").matcher(layoutText).find();
            if (!std140 && !std430) continue;
            String blockName = block.group(3);
            if (requestedBlock != null && !requestedBlock.equals(blockName)) continue;
            if (std140 && std430) throw new IllegalArgumentException(file + ": both std140 and std430 declared for " + blockName);
            if (block.group(2).equals("buffer") && std140)
                throw new IllegalArgumentException(file + ": std140 storage buffers are not supported");
            if (layoutText.matches("(?s).*\\b(row_major|offset|align)\\b.*"))
                throw new IllegalArgumentException(file + ": explicit offset/align and row_major require a dedicated generator extension");
            int close = withoutComments.indexOf('}', block.end());
            if (close < 0 || withoutComments.substring(block.end(), close).contains("{"))
                throw new IllegalArgumentException(file + ": nested block declaration is invalid: " + blockName);
            String body = withoutComments.substring(block.end(), close);
            List<Member> members = parseMembers(body, defines, file + " " + blockName);
            Standard standard = std140 ? Standard.STD140 : Standard.STD430;
            Layout layout = layout(members, structs, standard, new HashSet<>(), file);
            String className = upperCamel(blockName) + "Binding";
            if (!CLASS_NAME.matcher(className).matches()) {
                throw new IllegalArgumentException(file + ": block name cannot produce a Java binding class: " + blockName);
            }
            candidates.add(new Binding(className, blockName, standard, layout, structs, file));
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(file + ": no explicit std140/std430 block"
                    + (requestedBlock == null ? " found" : " named " + requestedBlock));
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException(file + ": multiple std140/std430 blocks "
                    + candidates.stream().map(Binding::blockName).toList() + "; select one with shader/path#BlockName");
        }
        return candidates.getFirst();
    }

    private static String stripComments(String input) {
        return input.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\r\\n]*", " ");
    }

    private static List<Member> parseMembers(String body, Map<String, String> defines, String context) {
        List<Member> members = new ArrayList<>();
        for (String raw : body.split(";", -1)) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            Matcher m = MEMBER.matcher(s);
            if (!m.matches()) throw new IllegalArgumentException(context + ": unsupported GLSL declaration '" + s + "'");
            String type = m.group(1), name = m.group(2);
            if (members.stream().anyMatch(member -> member.name.equals(name)))
                throw new IllegalArgumentException(context + ": duplicate member " + name);
            boolean array = m.group(3) != null;
            int count = array ? evaluate(m.group(3), defines, new HashSet<>()) : 1;
            if (count <= 0) throw new IllegalArgumentException(context + ": array must have positive fixed length: " + name);
            members.add(new Member(type, name, array, count));
        }
        return members;
    }

    private static int evaluate(String expression, Map<String, String> defines, Set<String> seen) {
        class Expression {
            int pos;
            final String s;
            Expression(String s) { this.s = s; }
            void spaces() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
            boolean eat(char c) { spaces(); if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; } return false; }
            int expression() {
                int value = term();
                while (true) {
                    if (eat('+')) value = Math.addExact(value, term());
                    else if (eat('-')) value = Math.subtractExact(value, term());
                    else return value;
                }
            }
            int term() {
                int value = factor();
                while (true) {
                    if (eat('*')) value = Math.multiplyExact(value, factor());
                    else if (eat('/')) { int divisor = factor(); if (divisor == 0) throw new IllegalArgumentException("Division by zero"); value /= divisor; }
                    else return value;
                }
            }
            int factor() {
                if (eat('+')) return factor();
                if (eat('-')) return Math.negateExact(factor());
                if (eat('(')) {
                    int value = expression();
                    if (!eat(')')) throw new IllegalArgumentException("Unclosed constant expression: " + s);
                    return value;
                }
                spaces();
                int start = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
                if (start == pos) throw new IllegalArgumentException("Unexpected token in GLSL constant expression: " + s);
                String token = s.substring(start, pos);
                if (Character.isDigit(token.charAt(0))) return Integer.parseInt(token);
                String replacement = defines.get(token);
                if (replacement == null) throw new IllegalArgumentException("Unknown GLSL array constant " + token + " in " + s);
                if (!seen.add(token)) throw new IllegalArgumentException("Recursive GLSL #define: " + token);
                try { return evaluate(replacement, defines, seen); }
                finally { seen.remove(token); }
            }
        }
        Expression parser = new Expression(expression);
        int n = parser.expression();
        parser.spaces();
        if (parser.pos != expression.length()) throw new IllegalArgumentException("Unsupported GLSL constant expression: " + expression);
        return n;
    }

    private enum Standard { STD140, STD430 }
    private static final class Member {
        final String type, name;
        final boolean array;
        final int count;
        int offset, stride, occupied;
        Layout nested;
        Member(String type, String name, boolean array, int count) {
            this.type = type; this.name = name; this.array = array; this.count = count;
        }
    }
    private record Layout(List<Member> members, int alignment, int size) { }
    private record Binding(String className, String blockName, Standard standard, Layout layout,
                           Map<String, List<Member>> structs, String source) { }
    private record MetadataBinding(String key, Binding binding) { }
    private record Value(int alignment, int size, int columns, int rows, boolean floating, boolean bool, boolean matrix) { }

    private static Value primitive(String name, Standard standard) {
        if (name.equals("float") || name.equals("int") || name.equals("uint") || name.equals("bool"))
            return new Value(4, 4, 1, 1, name.equals("float"), name.equals("bool"), false);
        Matcher vector = Pattern.compile("([biu]?vec)([234])").matcher(name);
        if (vector.matches()) {
            int rows = Integer.parseInt(vector.group(2));
            return new Value(rows == 2 ? 8 : 16, rows * 4, 1, rows, name.startsWith("vec"), name.startsWith("bvec"), false);
        }
        Matcher matrix = Pattern.compile("mat([234])(?:x([234]))?").matcher(name);
        if (matrix.matches()) {
            int columns = Integer.parseInt(matrix.group(1));
            int rows = matrix.group(2) == null ? columns : Integer.parseInt(matrix.group(2));
            int columnAlignment = rows == 2 ? 8 : 16;
            int matrixStride = align(rows * 4, standard == Standard.STD140 ? 16 : columnAlignment);
            return new Value(Math.max(columnAlignment, standard == Standard.STD140 ? 16 : 1), matrixStride * columns,
                    columns, rows, true, false, true);
        }
        return null;
    }

    private static Layout layout(List<Member> declarations, Map<String, List<Member>> structs,
                                 Standard standard, Set<String> visiting, String context) {
        List<Member> members = new ArrayList<>();
        int cursor = 0, maximumAlignment = 1;
        for (Member declaration : declarations) {
            Member m = new Member(declaration.type, declaration.name, declaration.array, declaration.count);
            Value p = primitive(m.type, standard);
            int size, alignment;
            if (p != null) { alignment = p.alignment; size = p.size; }
            else {
                List<Member> nested = structs.get(m.type);
                if (nested == null) throw new IllegalArgumentException(context + ": unsupported GLSL member type " + m.type);
                if (!visiting.add(m.type)) throw new IllegalArgumentException(context + ": recursive GLSL struct " + m.type);
                try { m.nested = layout(nested, structs, standard, visiting, context); }
                finally { visiting.remove(m.type); }
                alignment = m.nested.alignment;
                size = m.nested.size;
            }
            if (m.array) alignment = Math.max(alignment, standard == Standard.STD140 ? 16 : 1);
            m.stride = m.array ? align(size, alignment) : 0;
            m.occupied = m.array ? Math.multiplyExact(m.stride, m.count) : size;
            cursor = align(cursor, alignment);
            m.offset = cursor;
            cursor = Math.addExact(cursor, m.occupied);
            maximumAlignment = Math.max(maximumAlignment, alignment);
            members.add(m);
        }
        maximumAlignment = Math.max(maximumAlignment, standard == Standard.STD140 ? 16 : 1);
        return new Layout(members, maximumAlignment, align(cursor, maximumAlignment));
    }

    private static int align(int size, int alignment) { return Math.multiplyExact((Math.addExact(size, alignment - 1) / alignment), alignment); }

    private static String emit(Binding b) {
        StringBuilder out = new StringBuilder();
        line(out, "/* Generated by tools/uniform-codegen/UniformCodegen.java from " + Path.of(b.source).getFileName().toString() + ". DO NOT EDIT. */");
        line(out, "package " + PACKAGE + ";");
        line(out, "");
        line(out, "import java.nio.ByteBuffer;");
        line(out, "import java.nio.ByteOrder;");
        line(out, "import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;");
        if (b.standard == Standard.STD140) {
            line(out, "import com.mojang.blaze3d.buffers.GpuBufferSlice;");
            line(out, "import combatant.client.render.engine.core.CombatantRenderSystem;");
        }
        if (b.standard == Standard.STD430) {
            line(out, "import combatant.client.render.engine.rhi.shader.Std430StructLayout;");
            line(out, "import combatant.client.render.engine.rhi.shader.Std430Type;");
        }
        if (containsMatrix(b.layout)) {
            line(out, "import org.joml.*;");
        }
        line(out, "");
        line(out, "/** Generated " + b.standard.name().toLowerCase(Locale.ROOT) + " CPU layout for GLSL block " + b.blockName + ". */");
        line(out, "public final class " + b.className + " {");
        line(out, "    public static final String BLOCK_NAME = \"" + b.blockName + "\";");
        line(out, "    public static final String STANDARD = \"" + b.standard.name().toLowerCase(Locale.ROOT) + "\";");
        line(out, "    public static final int SIZE = " + b.layout.size + ";");
        emitOffsets(out, b.layout, "", 0);
        if (b.standard == Standard.STD430) {
            line(out, "    public static final Std430StructLayout LAYOUT = " + layoutExpression(b.layout, "        ") + ";");
        }
        line(out, "");
        line(out, "    private " + b.className + "() { }");
        line(out, "");
        line(out, "    public static final class Writer implements CombatantUniformAllocator.UniformWriter {");
        line(out, "        private final ByteBuffer data = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.nativeOrder());");
        line(out, "        private final ByteBuffer readView = data.asReadOnlyBuffer().order(data.order());");
        line(out, "        public int byteSize() { return SIZE; }");
        line(out, "        public ByteBuffer buffer() { return readView.position(0).limit(SIZE); }");
        line(out, "        @Override public void write(ByteBuffer target) {");
        line(out, "            if (target.remaining() < SIZE) throw new IllegalArgumentException(\"GPU destination smaller than \" + SIZE);");
        line(out, "            int position = target.position();");
        line(out, "            target.put(position, data, 0, SIZE);");
        line(out, "            target.position(position + SIZE);");
        line(out, "        }");
        if (b.standard == Standard.STD140) {
            line(out, "        public GpuBufferSlice upload(int expectedWritesPerFrame) {");
            line(out, "            return CombatantRenderSystem.uniforms().write(");
            line(out, "                    \"Combatant - \" + BLOCK_NAME + \" UBO\", SIZE, expectedWritesPerFrame, this);");
            line(out, "        }");
        }
        line(out, "");
        emitSetters(out, b.layout, b.standard, "", "", "", new ArrayList<>());
        line(out, "    }");
        line(out, "}");
        return out.toString();
    }

    private static String layoutExpression(Layout l, String prefix) {
        StringBuilder sb = new StringBuilder("Std430StructLayout.builder()");
        for (Member m : l.members) {
            if (m.nested != null) {
                sb.append(m.array ? ".structArray(\"" : ".struct(\"").append(m.name).append("\", ")
                        .append(layoutExpression(m.nested, prefix + "    "));
                if (m.array) sb.append(", ").append(m.count);
                sb.append(")");
            } else {
                sb.append(m.array ? ".array(\"" : ".member(\"").append(m.name).append("\", Std430Type.")
                        .append(m.type.toUpperCase(Locale.ROOT)).append(m.array ? ", " + m.count + ")" : ")");
            }
        }
        return sb.append(".build()").toString();
    }

    private static boolean containsMatrix(Layout l) {
        for (Member m : l.members) if (m.nested != null ? containsMatrix(m.nested) : m.type.startsWith("mat")) return true;
        return false;
    }

    private static void emitOffsets(StringBuilder out, Layout l, String prefix, int base) {
        for (Member m : l.members) {
            String key = prefix + m.name;
            String constant = "OFFSET_" + constantName(key);
            line(out, "    public static final int " + constant + " = " + (base + m.offset) + ";");
            if (m.array) {
                line(out, "    public static final int COUNT_" + constantName(key) + " = " + m.count + ";");
                line(out, "    public static final int STRIDE_" + constantName(key) + " = " + m.stride + ";");
            }
            if (m.nested != null) emitOffsets(out, m.nested, key + "_", base + m.offset);
        }
    }

    private static String upperCamel(String name) {
        StringBuilder out = new StringBuilder();
        for (String piece : name.split("_+")) {
            if (piece.isEmpty()) continue;
            out.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
        }
        return out.toString();
    }

    private static String constantName(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
    }

    private record Index(String variable, int count, int stride) { }

    private static void emitSetters(StringBuilder out, Layout l, Standard standard, String path, String base,
                                    String javaName, List<Index> indices) {
        for (Member m : l.members) {
            String componentName = javaName + upperCamel(m.name);
            String offset = base.isEmpty() ? Integer.toString(m.offset) : base + " + " + m.offset;
            List<Index> next = new ArrayList<>(indices);
            if (m.array) {
                String indexName = "index" + next.size();
                next.add(new Index(indexName, m.count, m.stride));
                offset += " + " + indexName + " * " + m.stride;
            }
            if (m.nested != null) {
                emitSetters(out, m.nested, standard, path + m.name + ".", offset, componentName, next);
                continue;
            }
            Value value = primitive(m.type, Standard.STD430);
            StringBuilder args = new StringBuilder();
            StringBuilder checks = new StringBuilder();
            for (Index i : next) {
                if (!args.isEmpty()) args.append(", ");
                args.append("int ").append(i.variable);
                checks.append("            if (").append(i.variable).append(" < 0 || ").append(i.variable).append(" >= ")
                        .append(i.count).append(") throw new IndexOutOfBoundsException(\"").append(path).append(m.name)
                        .append(" ").append(i.variable).append("=\" + ").append(i.variable).append(");\n");
            }
            int componentCount = value.columns * value.rows;
            if (value.matrix && value.columns == value.rows) {
                if (!args.isEmpty()) args.append(", ");
                args.append(matrixType(value)).append(" value");
            } else {
                for (int component = 0; component < componentCount; component++) {
                    if (!args.isEmpty()) args.append(", ");
                    args.append(value.bool ? "boolean" : value.floating ? "float" : "int").append(" v").append(component);
                }
            }
            line(out, "        public Writer set" + componentName + "(" + args + ") {");
            out.append(checks);
            line(out, "            int pos = " + offset + ";");
            if (value.matrix) {
                if (value.columns == value.rows) line(out, "            if (value == null) throw new IllegalArgumentException(\"" + m.name + " matrix is null\");");
                int matrixStride = align(value.rows * 4, standard == Standard.STD140 ? 16 : value.rows == 2 ? 8 : 16);
                for (int col = 0; col < value.columns; col++) {
                    for (int row = 0; row < value.rows; row++) {
                        line(out, "            data.putFloat(pos + " + (col * matrixStride + row * 4)
                                + ", " + (value.columns == value.rows ? "value.m" + col + row + "()" : "v" + (col * value.rows + row)) + ");");
                    }
                }
            } else {
                for (int component = 0; component < componentCount; component++) {
                    line(out, "            data.put" + (value.floating ? "Float" : "Int") + "(pos + " + (component * 4)
                            + ", " + (value.bool ? "(v" + component + " ? 1 : 0)" : "v" + component) + ");");
                }
            }
            line(out, "            return this;");
            line(out, "        }");
        }
    }

    private static String matrixType(Value v) {
        String suffix = v.columns == v.rows ? Integer.toString(v.columns) : v.columns + "x" + v.rows;
        return "Matrix" + suffix + "fc";
    }

    private static void line(StringBuilder out, String s) { out.append(s).append('\n'); }

    private static void selfTest() {
        testLayout("float a; vec3 b; float c; vec2 d;", Standard.STD140, 48,
                new int[]{0, 16, 28, 32}, new int[]{0,0,0,0});
        testLayout("float a; vec3 b; float c; vec2 d;", Standard.STD430, 48,
                new int[]{0, 16, 28, 32}, new int[]{0,0,0,0});
        testLayout("float a[3]; vec2 b[2]; mat2 c; mat3 d;", Standard.STD140, 160,
                new int[]{0,48,80,112}, new int[]{16,16,0,0});
        testLayout("float a[3]; vec2 b[2]; mat2 c; mat3 d;", Standard.STD430, 96,
                new int[]{0,16,32,48}, new int[]{4,8,0,0});
        String nested = "struct Child { float x; vec2 uv; };\n" +
                "layout(std430, binding = 1) buffer Node { Child child[2]; float end; };";
        Binding b = parse(nested, "test.comp", null);
        assertEq(40, b.layout.size, "struct size");
        assertEq(32, b.layout.members.get(1).offset, "struct end offset");
        assertEq(16, b.layout.members.getFirst().stride, "struct array stride");
        if (!emit(b).contains("setChildUv(int index0, float v0, float v1)")) throw new AssertionError("nested setters missing");
        String defines = "#define A 4\n#define B (A * 3)\nlayout(std140) uniform Test { vec4 a[B]; };";
        assertEq(192, parse(defines, "test.frag", null).layout.size, "macro expression");
        Binding selected = parse("layout(std140) uniform First { float a; };\n"
                + "layout(std430) buffer Second { float b; };", "multi.glsl", "Second");
        if (!selected.className.equals("SecondBinding")) throw new AssertionError("automatic class name");
        mustFail("layout(std140) uniform First { float a; }; layout(std430) buffer Second { float b; };",
                null, "multiple std140/std430 blocks");
        mustFail("layout(std430) buffer Bad { float a[]; };", null, "unsupported GLSL declaration");
        mustFail("layout(std430) buffer Bad { double a; };", null, "unsupported GLSL member type");
        mustFail("layout(std140, row_major) uniform Bad { mat3 a; };", null, "row_major");
        mustFail("#define N N\nlayout(std140) uniform Bad { vec4 a[N]; };", null, "Recursive");
    }

    private static void testLayout(String members, Standard standard, int size, int[] offsets, int[] strides) {
        Layout l = layout(parseMembers(members, Map.of(), "test"), Map.of(), standard, new HashSet<>(), "test");
        assertEq(size, l.size, "size " + members + " " + standard);
        for (int i = 0; i < offsets.length; i++) {
            assertEq(offsets[i], l.members.get(i).offset, "offset " + i + " " + standard);
            assertEq(strides[i], l.members.get(i).stride, "stride " + i + " " + standard);
        }
    }

    private static void assertEq(int expected, int actual, String context) {
        if (expected != actual) throw new AssertionError(context + ": expected " + expected + ", got " + actual);
    }

    private static void mustFail(String source, String blockName, String message) {
        try { parse(source, "test", blockName); }
        catch (IllegalArgumentException expected) {
            if (expected.getMessage().contains(message)) return;
            throw new AssertionError("Expected error '" + message + "', got '" + expected.getMessage() + "'");
        }
        throw new AssertionError("Expected to reject GLSL: " + source);
    }
}
