/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds a compact runtime index for Combatant's annotated builtin components. */
@SupportedAnnotationTypes("*")
@SupportedOptions(CombatantIndexProcessor.INDEX_NAME_OPTION)
public final class CombatantIndexProcessor extends AbstractProcessor {
    static final String INDEX_NAME_OPTION = "combatant.indexName";

    private static final Map<String, String> TYPE_ANNOTATIONS = Map.ofEntries(
            Map.entry("combatant.client.features.module.ModuleInfo", "MODULE"),
            Map.entry("combatant.client.features.gui.hud.HudElementRegister", "HUD"),
            Map.entry("combatant.client.features.gui.hud.HudElementInfo", "HUD"),
            Map.entry("combatant.client.features.command.CommandInfo", "COMMAND"),
            Map.entry("combatant.client.features.gui.clickgui.sections.ClickGuiSectionInfo", "CLICK_GUI"),
            Map.entry("combatant.client.util.sound.SoundCatalog", "SOUND"),
            Map.entry("combatant.client.util.resources.asset.TextureCatalog", "TEXTURE_CATALOG"),
            Map.entry("combatant.client.util.resources.asset.FontCatalog", "FONT_CATALOG"),
            Map.entry("combatant.client.util.resources.asset.ScriptCatalog", "SCRIPT_CATALOG"),
            Map.entry("combatant.client.util.resources.asset.ResourceCatalog", "RESOURCE_CATALOG"),
            Map.entry("combatant.client.util.resources.asset.UiScriptAsset", "UI_SCRIPT")
    );
    private static final String ASSET_LOAD = "combatant.client.util.resources.asset.AssetLoad";
    private static final String CONFIG_OBJECT = "combatant.client.config.ConfigObject";
    private static final String PIPELINE_REGISTER =
            "combatant.client.render.engine.pipeline.PipelineRegister";
    private static final String RENDER_PIPELINE = "com.mojang.renderpearl.api.pipeline.RenderPipeline";

    private final Map<String, Set<String>> entries = new TreeMap<>();
    private boolean written;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            for (Element root : roundEnv.getRootElements()) {
                if (root instanceof TypeElement type) collect(type);
            }
            return false;
        }

        if (!written && !roundEnv.errorRaised()) {
            written = true;
            writeIndex();
        }
        return false;
    }

    private void collect(TypeElement type) {
        String binaryName = processingEnv.getElementUtils().getBinaryName(type).toString();
        for (Map.Entry<String, String> annotation : TYPE_ANNOTATIONS.entrySet()) {
            if (hasAnnotation(type, annotation.getKey())) add(annotation.getValue(), binaryName);
        }

        boolean hasAssetHook = false;
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD && hasAnnotation(enclosed, ASSET_LOAD)) {
                hasAssetHook = true;
            }
            if (enclosed instanceof VariableElement field && hasAnnotation(field, PIPELINE_REGISTER)) {
                collectPipeline(binaryName, field);
            }
            if (enclosed instanceof TypeElement nested) collect(nested);
        }
        if (hasAssetHook) add("ASSET_HOOK", binaryName);
        if (declaresConfigSingleton(type)) add("CONFIG", binaryName);
    }

    private void collectPipeline(String binaryName, VariableElement field) {
        Set<Modifier> modifiers = field.getModifiers();
        boolean validModifiers = modifiers.contains(Modifier.PUBLIC)
                && modifiers.contains(Modifier.STATIC)
                && modifiers.contains(Modifier.FINAL);
        boolean validType = processingEnv.getTypeUtils().erasure(field.asType()).toString()
                .equals(RENDER_PIPELINE);
        if (!validModifiers || !validType) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PipelineRegister requires a public static final RenderPipeline field",
                    field
            );
            return;
        }
        add("PIPELINE", binaryName + "#" + field.getSimpleName());
    }

    private boolean declaresConfigSingleton(TypeElement type) {
        TypeElement configObject = processingEnv.getElementUtils().getTypeElement(CONFIG_OBJECT);
        if (configObject == null) return false;
        TypeMirror configType = processingEnv.getTypeUtils().erasure(configObject.asType());
        for (Element enclosed : type.getEnclosedElements()) {
            if (!(enclosed instanceof VariableElement field) || !field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (processingEnv.getTypeUtils().isAssignable(
                    processingEnv.getTypeUtils().erasure(field.asType()), configType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotation(Element element, String annotationName) {
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            if (annotation.getAnnotationType().toString().equals(annotationName)) return true;
        }
        return false;
    }

    private void add(String kind, String binaryName) {
        entries.computeIfAbsent(kind, ignored -> new TreeSet<>()).add(binaryName);
    }

    private void writeIndex() {
        String indexName = processingEnv.getOptions().getOrDefault(INDEX_NAME_OPTION, "main");
        String resource = "META-INF/combatant/index/" + sanitize(indexName) + ".idx";
        try {
            FileObject output = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", resource);
            try (Writer writer = output.openWriter()) {
                writer.write("# Generated by CombatantIndexProcessor\n");
                for (Map.Entry<String, Set<String>> group : entries.entrySet()) {
                    for (String binaryName : group.getValue()) {
                        writer.write(group.getKey());
                        writer.write('\t');
                        writer.write(binaryName);
                        writer.write('\n');
                    }
                }
            }
        } catch (FilerException ignored) {
            // Another final round already produced the aggregating resource.
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write " + resource, error);
        }
    }

    private static String sanitize(String value) {
        String clean = value == null ? "main" : value.replaceAll("[^A-Za-z0-9_.-]", "");
        return clean.isBlank() ? "main" : clean;
    }
}
