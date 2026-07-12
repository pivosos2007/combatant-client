/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris.patch;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ShaderPatchCompilerCli {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"");

    private ShaderPatchCompilerCli() {
    }

    static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: validateIrisPatches -Pshaderpack=<shaderpack.zip>");
        }

        Path shaderpack = Path.of(args[0]).toAbsolutePath().normalize();
        String shaderPackName = shaderpack.getFileName().toString();
        try (ZipFile zip = new ZipFile(shaderpack.toFile())) {
            String root = findShaderRoot(zip);
            Map<String, ImmutableList<String>> sources = new HashMap<>();
            List<String> targetPaths = ShaderPatchEngine.targetPaths(shaderPackName);
            if (targetPaths.isEmpty()) {
                throw new IllegalStateException("No shader patch manifest selected for " + shaderPackName);
            }
            for (String target : targetPaths) {
                sources.put(target, ImmutableList.copyOf(expand(zip, root, target, new HashSet<>())));
            }

            ShaderPatchEngine.Session session = ShaderPatchEngine.newSession(shaderPackName);
            for (String target : targetPaths) {
                session.patch(target, sources.get(target), sources::get);
            }
        }

        List<String> diagnostics = ShaderPatchEngine.diagnostics();
        diagnostics.forEach(System.out::println);
        boolean applied = diagnostics.stream().anyMatch(line -> line.contains(": applied"));
        boolean rejected = diagnostics.stream().anyMatch(line -> line.contains("rejected"));
        if (!applied || rejected) {
            throw new IllegalStateException("No complete shader patch manifest compiled successfully");
        }
    }

    private static List<String> expand(ZipFile zip, String root, String path, Set<String> stack) throws IOException {
        String normalized = normalizePath(path);
        if (!stack.add(normalized)) {
            throw new IOException("Shader include cycle at " + normalized);
        }

        ZipEntry entry = zip.getEntry(root + "shaders" + normalized);
        if (entry == null) {
            throw new IOException("Missing shader source " + normalized);
        }

        String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        ArrayList<String> output = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = INCLUDE.matcher(line);
            if (matcher.find()) {
                output.addAll(expand(zip, root, resolveInclude(normalized, matcher.group(1)), stack));
            } else {
                output.add(line);
            }
        }
        stack.remove(normalized);
        return output;
    }

    private static String findShaderRoot(ZipFile zip) throws IOException {
        return zip.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.endsWith("shaders/shaders.properties"))
                .map(name -> name.substring(0, name.length() - "shaders/shaders.properties".length()))
                .min(java.util.Comparator.comparingInt(String::length))
                .orElseThrow(() -> new IOException("Archive does not contain shaders/shaders.properties"));
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        String[] parts = normalized.split("/");
        ArrayList<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!clean.isEmpty()) {
                    clean.remove(clean.size() - 1);
                }
                continue;
            }
            clean.add(part);
        }
        return "/" + String.join("/", clean);
    }

    private static String resolveInclude(String currentPath, String includePath) {
        if (includePath.startsWith("/")) {
            return normalizePath(includePath);
        }
        String current = normalizePath(currentPath);
        int separator = current.lastIndexOf('/');
        String parent = separator <= 0 ? "/" : current.substring(0, separator + 1);
        return normalizePath(parent + includePath);
    }
}
