/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import java.util.regex.Pattern;

/** Converts legacy shaded color before fog, OIT weighting, and attachment blending. */
public final class HdrShaderSources {
    private static final Pattern FOG = Pattern.compile(
            "vec4\\s+(apply_fog|_linearFog)\\s*\\(vec4\\s+(\\w+)[^)]*\\)\\s*\\{");
    private static final Pattern OUTPUT = Pattern.compile(
            "layout\\s*\\(\\s*location\\s*=\\s*0\\s*\\)\\s*out\\s+vec4\\s+(\\w+)\\s*;");

    private HdrShaderSources() { }

    public static String linearize(String source, String colorFunctions) {
        String result = source;
        boolean fogCall = Pattern.compile("(?<!vec4 )\\b(?:apply_fog|_linearFog)\\s*\\(")
                .matcher(source).results().count() > 0;
        var fog = FOG.matcher(result);
        StringBuilder out = new StringBuilder();
        while (fog.find()) {
            String input = fog.group(2);
            String conversion = "\n#ifndef OIT_ACCUMULATE\n"
                    + convertColor(input)
                    + "fogColor.rgb = combatantLegacyToSceneLinear(fogColor.rgb);\n#else\n"
                    + "fogColor.rgb = combatantLegacyToSceneLinear(fogColor.rgb / max(" + input
                    + ".a, 1e-8)) * " + input + ".a;\n#endif\n";
            fog.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(fog.group() + conversion));
        }
        fog.appendTail(out);
        result = out.toString().replaceAll(
                "(vec4\\s+sampleColorForAccumulation\\s*\\(vec4\\s+color\\)\\s*\\{)",
                "$1\n color.rgb = combatantLegacyToSceneLinear(color.rgb);\n");
        if (!fogCall) {
            var output = OUTPUT.matcher(result);
            if (!output.find()) throw new IllegalArgumentException("HDR legacy shader has no vec4 color output");
            String name = output.group(1);
            result = result.replaceFirst("void\\s+main\\s*\\(\\s*\\)", "void combatantLegacyMain()");
            result += "\nvoid main() {\n combatantLegacyMain();\n"
                    + "#if !defined(OIT_ALPHA_ONLY) && !defined(OIT_ACCUMULATE)\n"
                    + convertColor(name) + "#endif\n}\n";
        }
        int insert = result.indexOf('\n') + 1;
        var extensions = Pattern.compile("(?m)^#extension[^\\n]*\\n").matcher(result);
        while (extensions.find()) insert = Math.max(insert, extensions.end());
        return result.substring(0, insert) + colorFunctions + "\n" + result.substring(insert);
    }

    private static String convertColor(String name) {
        return "#ifdef " + HdrPipelineVariants.PREMULTIPLIED_COLOR + "\n"
                + name + ".rgb = combatantLegacyToSceneLinear(" + name + ".rgb / max(" + name
                + ".a, 1e-8)) * " + name + ".a;\n#else\n"
                + name + ".rgb = combatantLegacyToSceneLinear(" + name + ".rgb);\n#endif\n";
    }
}
