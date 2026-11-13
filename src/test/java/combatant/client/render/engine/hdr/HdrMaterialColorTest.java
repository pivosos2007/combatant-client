/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

final class HdrMaterialColorTest {
    @Test
    void displayMaterialsPreserveBlackContributionAndWhiteLuminance() throws Exception {
        for (float[] color : new float[][]{{0, 0, 0}, {1, 1, 1}, {.5f, .5f, .5f},
                {1, .2f, .05f}, {.4f, .87f, 1}}) {
            for (float alpha : new float[]{0, .21f, .5f, 1}) {
                float[] actual = material(color, alpha, true);
                assertTrue(actual[3] >= -1e-6 && actual[3] <= 1 + 1e-6);
                float whiteLuminance = 0;
                float expectedWhiteLuminance = 0;
                float[] luma = {.2126f, .7152f, .0722f};
                for (int c = 0; c < 3; c++) {
                    assertTrue(Float.isFinite(actual[c]));
                    float black = actual[c] * actual[3];
                    assertEquals(SceneColor.srgbToLinear(color[c] * alpha), black, 3e-6,
                            "premultiplied source contribution");
                    whiteLuminance += luma[c] * (black + 1 - actual[3]);
                    expectedWhiteLuminance += luma[c] * SceneColor.srgbToLinear(1 - alpha + color[c] * alpha);
                }
                assertEquals(expectedWhiteLuminance, whiteLuminance, 3e-6);
            }
        }
        assertEquals(SceneColor.srgbToLinear(.5f), material(new float[]{1, 1, 1}, .5f, true)[3], 3e-6);
        assertEquals(1 - SceneColor.srgbToLinear(.5f), material(new float[]{0, 0, 0}, .5f, true)[3], 3e-6);
    }

    @Test
    void materialsWithoutDisplayOpacityKeepLinearColorAndOriginalAlpha() throws Exception {
        float[] color = {.4f, .7f, 1};
        float[] actual = material(color, .21f, false);
        for (int c = 0; c < 3; c++) assertEquals(SceneColor.srgbToLinear(color[c]), actual[c], 3e-6);
        assertEquals(.21f, actual[3], 1e-6);
    }

    // Literal inputs produce straight-line SPIR-V; evaluating it requires no GPU context.
    private static float[] material(float[] color, float alpha, boolean displayOpacity) throws Exception {
        String functions = Files.readString(Path.of("src/main/resources/assets/combatant/shaders/include/scene_color.glsl"));
        String source = "#version 450\n#define COMBATANT_SCENE_LINEAR_COLOR\n"
                + (displayOpacity ? "#define COMBATANT_HDR_DISPLAY_OPACITY\n" : "") + functions
                + "\nlayout(location=0) out vec4 resultColor;\nvoid main() { resultColor = combatantMaterial(vec4("
                + String.format(Locale.ROOT, "%.9f, %.9f, %.9f, %.9f", color[0], color[1], color[2], alpha)
                + ")); }\n";
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        try {
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
            long result = shaderc_compile_into_spv_assembly(compiler, source, shaderc_fragment_shader,
                    "material-reference.frag", "main", options);
            try {
                assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                        shaderc_result_get_error_message(result));
                String assembly = StandardCharsets.UTF_8.decode(shaderc_result_get_bytes(result)).toString();
                var store = Pattern.compile("OpStore\\s+%\\w+\\s+(%\\w+)").matcher(assembly);
                assertTrue(store.find(), assembly);
                Map<String, String[]> instructions = new HashMap<>();
                var definition = Pattern.compile("(?m)^\\s*(%\\w+) = (.+)$").matcher(assembly);
                while (definition.find()) instructions.put(definition.group(1), definition.group(2).trim().split("\\s+"));
                return evaluate(store.group(1), instructions, new HashMap<>());
            } finally {
                shaderc_result_release(result);
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private static float[] evaluate(String id, Map<String, String[]> instructions, Map<String, float[]> values) {
        if (values.containsKey(id)) return values.get(id);
        String[] op = instructions.get(id);
        assertNotNull(op, "undefined SPIR-V value " + id);
        float[] result;
        switch (op[0]) {
            case "OpConstant" -> result = new float[]{Float.parseFloat(op[2])};
            case "OpConstantNull" -> {
                String[] type = instructions.get(op[1]);
                result = new float[type[0].equals("OpTypeVector") ? Integer.parseInt(type[2]) : 1];
            }
            case "OpConstantComposite", "OpCompositeConstruct" -> {
                result = new float[op.length - 2];
                for (int i = 0; i < result.length; i++) result[i] = evaluate(op[i + 2], instructions, values)[0];
            }
            case "OpCompositeExtract" -> result = new float[]{evaluate(op[2], instructions, values)[Integer.parseInt(op[3])]};
            case "OpFAdd", "OpFSub", "OpFMul", "OpFDiv", "OpDot", "OpVectorTimesScalar", "OpExtInst" -> {
                String operation = op[0].equals("OpExtInst") ? op[3] : op[0];
                int start = op[0].equals("OpExtInst") ? 4 : 2;
                float[][] args = Arrays.stream(op, start, op.length)
                        .map(arg -> evaluate(arg, instructions, values)).toArray(float[][]::new);
                int size = Arrays.stream(args).mapToInt(arg -> arg.length).max().orElseThrow();
                result = new float[size];
                for (int c = 0; c < size; c++) {
                    float a = args[0][Math.min(c, args[0].length - 1)];
                    float b = args[1][Math.min(c, args[1].length - 1)];
                    float t = args.length > 2 ? args[2][Math.min(c, args[2].length - 1)] : 0;
                    result[c] = switch (operation) {
                        case "OpFAdd" -> a + b;
                        case "OpFSub" -> a - b;
                        case "OpFMul", "OpDot", "OpVectorTimesScalar" -> a * b;
                        case "OpFDiv" -> a / b;
                        case "Step" -> b < a ? 0 : 1;
                        case "FMix" -> a * (1 - t) + b * t;
                        case "FMax" -> Math.max(a, b);
                        case "Pow" -> (float) Math.pow(a, b);
                        case "Fma" -> Math.fma(a, b, t);
                        default -> throw new AssertionError("unsupported SPIR-V operation " + operation);
                    };
                }
                if (operation.equals("OpDot")) {
                    float sum = 0;
                    for (float component : result) sum += component;
                    result = new float[]{sum};
                }
            }
            default -> throw new AssertionError("unsupported SPIR-V instruction " + String.join(" ", op));
        }
        values.put(id, result);
        return result;
    }
}
