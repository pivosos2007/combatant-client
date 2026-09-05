/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.shader;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source-level shader cost audit populated from the exact preprocessed source given to Mojang. */
public enum ShaderCostRegistry {
    ;

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\r\\n]*");
    private static final Pattern BASIC_ALU = Pattern.compile("(?<![+\\-*/%])[+\\-*/%](?![+\\-*/%=])|(?:<<|>>|&|\\||\\^)");
    private static final Pattern TRANSCENDENTAL = calls("sin", "cos", "tan", "asin", "acos", "atan", "pow", "exp", "exp2", "log", "log2", "sqrt", "inversesqrt");
    private static final Pattern TEXTURE = calls("texture", "textureLod", "textureGrad", "texelFetch", "textureProj", "textureGather");
    private static final Pattern BRANCH = Pattern.compile("\\b(?:if|else|switch|case)\\b|\\?");
    private static final Pattern LOOP = Pattern.compile("\\b(?:for|while|do)\\b");
    private static final Pattern DISCARD = Pattern.compile("\\bdiscard\\b");
    private static final Pattern VECTOR_ALU = calls("dot", "cross", "normalize", "length", "distance", "reflect", "refract", "mix", "smoothstep", "step", "clamp", "min", "max", "abs", "sign", "floor", "ceil", "fract", "mod", "fma", "dFdx", "dFdy", "fwidth");
    private static final ConcurrentMap<String, ShaderCostEstimate> ESTIMATES = new ConcurrentHashMap<>();

    public static ShaderCostEstimate analyze(Identifier id, ShaderType type, String source) {
        String shaderId = id != null ? id.toString() : "<unknown>";
        String stage = type != null ? type.toString().toLowerCase(java.util.Locale.ROOT) : "unknown";
        String clean = stripComments(source != null ? source : "");
        int basic = count(BASIC_ALU, clean) + count(VECTOR_ALU, clean) * 2;
        int transcendental = count(TRANSCENDENTAL, clean);
        int texture = count(TEXTURE, clean);
        int branches = count(BRANCH, clean);
        int loops = count(LOOP, clean);
        int discards = count(DISCARD, clean);
        int score = saturatingScore(basic, transcendental, texture, branches, loops, discards);
        ShaderCostEstimate estimate = new ShaderCostEstimate(
                shaderId,
                stage,
                clean.isEmpty() ? 0 : clean.split("\\R", -1).length,
                basic,
                transcendental,
                texture,
                branches,
                loops,
                discards,
                score
        );
        ESTIMATES.put(key(shaderId, stage), estimate);
        return estimate;
    }

    public static ShaderCostEstimate get(String shaderId, String stage) {
        if (shaderId == null || shaderId.isBlank()) return ShaderCostEstimate.NONE;
        ShaderCostEstimate exact = ESTIMATES.get(key(shaderId, stage));
        if (exact != null) return exact;
        for (ShaderCostEstimate estimate : ESTIMATES.values()) {
            if (shaderId.equals(estimate.shaderId())) return estimate;
        }
        return ShaderCostEstimate.NONE;
    }

    public static List<ShaderCostEstimate> top(int limit) {
        int bounded = Math.max(0, limit);
        if (bounded == 0) return List.of();
        return ESTIMATES.values().stream()
                .sorted(Comparator.comparingInt(ShaderCostEstimate::weightedScore).reversed())
                .limit(bounded)
                .toList();
    }

    public static Collection<ShaderCostEstimate> all() {
        return List.copyOf(ESTIMATES.values());
    }

    public static void clear() {
        ESTIMATES.clear();
    }

    private static String stripComments(String source) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll(" ")).replaceAll(" ");
    }

    private static int count(Pattern pattern, String source) {
        int result = 0;
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) result++;
        return result;
    }

    private static int saturatingScore(int alu, int trans, int texture, int branches, int loops, int discards) {
        long score = (long) alu + (long) trans * 8L + (long) texture * 20L
                + (long) branches * 4L + (long) loops * 12L + (long) discards * 2L;
        return score >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) score;
    }

    private static String key(String id, String stage) {
        return id + '|' + (stage == null ? "unknown" : stage);
    }

    private static Pattern calls(String... names) {
        return Pattern.compile("\\b(?:" + String.join("|", names) + ")\\s*\\(");
    }
}
