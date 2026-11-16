import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.fletching.table)
    `maven-publish`
}

val fmlVersion = property("fml") as String
val fmlLoaderSource = rootProject.file("fml/v$fmlVersion/loader/src/main/java/net/neoforged/fml/loading/FMLLoader.java")
val patchedFmlSourcesDirectory = layout.buildDirectory.dir("generated/sources/kilt-fml-copy")
val copyKiltFmlSources by tasks.registering(Copy::class) {
    from(rootProject.file("fml/v$fmlVersion/loader/src/main/java"))
    exclude(
        "net/neoforged/fml/loading/FMLLoader.java",
        "net/neoforged/fml/loading/moddiscovery/ModFile.java",
        "net/neoforged/fml/loading/moddiscovery/locators/JarInJarDependencyLocator.java"
    )
    into(patchedFmlSourcesDirectory)
}

val patchedFmlLoaderDirectory = layout.buildDirectory.dir("generated/sources/kilt-fml")
val modFileSource = rootProject.file("fml/v$fmlVersion/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModFile.java")
val patchedModFileDirectory = layout.buildDirectory.dir("generated/sources/kilt-modfile")
val jarInJarSource = rootProject.file("fml/v$fmlVersion/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/locators/JarInJarDependencyLocator.java")
val patchedJarInJarDirectory = layout.buildDirectory.dir("generated/sources/kilt-jarjar")

val patchKiltJarInJarLocator by tasks.registering {
    dependsOn(copyKiltFmlSources)
    inputs.file(jarInJarSource)
    outputs.file(patchedJarInJarDirectory.map { it.file("JarInJarDependencyLocator.java") })

    doLast {
        val original = jarInJarSource.readText()
        val patched = original
            .replace(
                "private static String extractEmbeddedJarFile(",
                "public static String extractEmbeddedJarFile("
            )
            .replace(
                "private static void moveExtractedFileIntoPlace(",
                "public static void moveExtractedFileIntoPlace("
            )

        val output = patchedJarInJarDirectory.get().file("JarInJarDependencyLocator.java").asFile
        output.parentFile.mkdirs()
        output.writeText(patched)
    }
}

val patchKiltModFile by tasks.registering {
    dependsOn(copyKiltFmlSources)
    inputs.file(modFileSource)
    outputs.file(patchedModFileDirectory.map { it.file("ModFile.java") })

    doLast {
        var patched = modFileSource.readText()
        if (!patched.contains("import xyz.bluspring.twill.loader.knit.NeoForgeMod;")) {
            patched = patched.replace(
                "import org.slf4j.Logger;\n",
                "import org.slf4j.Logger;\nimport xyz.bluspring.twill.loader.knit.NeoForgeMod;\n"
            )
        }

        val associationFieldName = "twill" + 36.toChar() + "knitAssociation"
        if (!patched.contains(associationFieldName)) {
            val associationMarker = "    @Nullable\n    private CompletableFuture<ModFileScanData> futureScanResult;\n"
            if (!patched.contains(associationMarker)) {
                throw GradleException("Unsupported FML $fmlVersion ModFile.java: cannot locate futureScanResult")
            }
            patched = patched.replace(
                associationMarker,
                associationMarker + "\n    // Twill: associate the discovered FML file with its Knit container.\n    public @Nullable NeoForgeMod twill$" + "knitAssociation;\n"
            )
        }

        if (!patched.contains("public ModFile(IModFile existingFile)")) {
            val constructorMarker = "    @Override\n    public String getId() {"
            if (!patched.contains(constructorMarker)) {
                throw GradleException("Unsupported FML $fmlVersion ModFile.java: cannot locate getId")
            }
            val adapterConstructor = """
    // Twill: copy a generic IModFile into the concrete FML representation.
    @ApiStatus.Internal
    public ModFile(IModFile existingFile) {
        this.id = existingFile.getId();
        this.contents = existingFile.getContents();
        this.discoveryAttributes = existingFile.getDiscoveryAttributes();
        this.jarModuleInfo = JarModuleInfo.from(this.contents);
        this.modFileType = existingFile.getType();
        this.mixinConfigs = List.of();
        this.accessTransformers = List.of();
        this.jarVersion = Optional.ofNullable(this.contents.getManifest().getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION)).orElse("0.0NONE");
        this.modFileInfo = existingFile.getModFileInfo();
    }

"""
            patched = patched.replace(constructorMarker, adapterConstructor + constructorMarker)
        }

        val output = patchedModFileDirectory.get().file("ModFile.java").asFile
        output.parentFile.mkdirs()
        output.writeText(patched)
    }
}

val patchKiltFmlLoader by tasks.registering {
    dependsOn(copyKiltFmlSources)
    inputs.file(fmlLoaderSource)
    outputs.file(patchedFmlLoaderDirectory.map { it.file("FMLLoader.java") })

    doLast {
        val dollar = 36.toChar()
        val twillCreateName = "twill" + dollar + "create"
        val twillDiscoverName = "twill" + dollar + "discover"
        val twillSetupName = "twill" + dollar + "setup"
        var patched = fmlLoaderSource.readText()

        // Kilt/Twill drives FML discovery and class transformation from Fabric's
        // Knot loader. Port the small Kilt-owned FML hooks onto the selected
        // FML source version rather than forking the entire loader.
        patched = patched
            .replace("import net.neoforged.fml.common.asm.AccessTransformerService;\n", "")
            .replace("import net.neoforged.accesstransformer.api.AccessTransformerEngine;\n", "")
            .replace("import xyz.bluspring.kilt.loader.asm.InactiveClassProcessor;\n", "")

        if (!patched.contains("import xyz.bluspring.twill.loader.neoforge.TwillBindings;")) {
            patched = patched.replace(
                "import org.slf4j.Logger;\n",
                "import org.slf4j.Logger;\nimport xyz.bluspring.twill.loader.neoforge.TwillBindings;\n"
            )
        }

        patched = patched
            .replace(
                "private LanguageProviderLoader languageProviderLoader;",
                "public LanguageProviderLoader languageProviderLoader;"
            )
            .replace(
                "private ModuleLayer gameLayer;",
                "private ModuleLayer gameLayer = ModuleLayer.boot();"
            )
            .replace(
                "volatile IBindingsProvider bindings;",
                "volatile IBindingsProvider bindings = TwillBindings.INSTANCE;"
            )
            .replace(
                "private void loadPlugins(",
                "public void loadPlugins("
            )
            .replace(
                "private class LaunchContextAdapter",
                "public class LaunchContextAdapter"
            )
            .replace(
                "private static ClassProcessorSet createClassProcessorSet(",
                "public static ClassProcessorSet createClassProcessorSet("
            )
            .replace(
                "    record DiscoveryResult(",
                "    public record DiscoveryResult("
            )
            .replace(
                "builtInProcessors.add(mixinFacade.getClassProcessor());",
                "// Twill: Fabric/Kilt owns the shared Mixin pipeline."
            )
            .replace(
                "builtInProcessors.add(mixinFacade.getGeneratingClassProcessor());",
                "// Twill: Fabric/Kilt owns synthetic Mixin class generation."
            )

        // FML 12's stock path starts an independent Mixin service. Twill uses
        // Fabric's already-running Mixin instead, and its createClassProcessorSet
        // is called with null startup arguments and no MixinFacade.
        patched = Regex(
            "(?s)        if \\(startupArgs\\.cleanDist\\(\\)\\) \\{.*?        \\}\\n"
        ).replace(patched, "")

        val accessTransformerMethod = Regex(
            "(?s)    private static ClassProcessor createAccessTransformerService\\(DiscoveryResult discoveryResult\\) \\{.*?\\n    \\}"
        )
        if (!accessTransformerMethod.containsMatchIn(patched)) {
            throw GradleException("Unsupported FML $fmlVersion: cannot find createAccessTransformerService")
        }
        patched = accessTransformerMethod.replace(
            patched,
            """
    private static ClassProcessor createAccessTransformerService(DiscoveryResult discoveryResult) {
        // Kilt applies access transformers through its Fabric/Knot transformation path.
        return xyz.bluspring.twill.compat.InactiveAccessTransformerProcessor.INSTANCE;
    }
            """.trimIndent()
        )

        // FML 11 already has the Kilt hooks. Add their FML 12 equivalents only
        // when absent so the same profile can also compile its older Stonecutter targets.
        val twillCreate = """
    public static FMLLoader $twillCreateName(@Nullable Instrumentation instrumentation, StartupArgs startupArgs, ClassLoader initialLoader, boolean isProduction) {
        PathPrettyPrinting.addRoot(startupArgs.gameDirectory());
        var loader = new FMLLoader(
                initialLoader,
                startupArgs.programArgs(),
                Objects.requireNonNullElseGet(startupArgs.dist(), () -> detectDist(initialLoader)),
                isProduction,
                startupArgs.gameDirectory());

        try {
            FMLPaths.loadAbsolutePaths(startupArgs.gameDirectory());
            FMLConfig.load();

            var launchContext = loader.new LaunchContextAdapter();
            for (var claimedFile : startupArgs.claimedFiles()) {
                launchContext.addLocated(claimedFile.toPath());
            }

            loader.loadEarlyServices(startupArgs);

            ServiceLoaderUtil.loadEarlyServices(launchContext, EarlyBootstrapper.class, List.of())
                    .forEach(bootstrap -> {
                        LOGGER.info("Running early bootstrap plugin {}", bootstrap.name());
                        bootstrap.bootstrap(loader.programArgs);
                    });

            ImmediateWindowHandler.load(launchContext, startupArgs.headless(), loader.programArgs);
            if (loader.versionInfo.neoForgeVersion() != null) {
                ImmediateWindowHandler.setNeoForgeVersion(loader.versionInfo.neoForgeVersion());
            }
            if (loader.versionInfo.mcVersion() != null) {
                ImmediateWindowHandler.setMinecraftVersion(loader.versionInfo.mcVersion());
            }

            ImmediateWindowHandler.updateProgress("Launching minecraft");
            ImmediateWindowHandler.renderTick();
            return loader;
        } catch (RuntimeException | Error e) {
            try {
                loader.close();
            } catch (Throwable t) {
                e.addSuppressed(t);
            }
            throw e;
        }
    }
        """.trimIndent()

        if (!patched.contains("public static FMLLoader $twillCreateName(")) {
            val marker = "    public static FMLLoader create(StartupArgs startupArgs)"
            if (!patched.contains(marker)) {
                throw GradleException("Unsupported FML $fmlVersion: cannot locate FMLLoader.create")
            }
            patched = patched.replace(marker, twillCreate + "\n\n" + marker)
        }

        val twillDiscover: String = if (patched.contains("public ModDiscoverer.Result runDiscovery()")) {
            """
    public ModDiscoverer.Result $twillDiscoverName() {
        return runDiscovery();
    }
            """.trimIndent()
        } else {
            """
    public ModDiscoverer.Result $twillDiscoverName() {
        var progress = StartupNotificationManager.prependProgressBar("Discovering mods...", 0);
        var additionalLocators = new ArrayList<IModFileCandidateLocator>();

        // Fabric Loader already owns Minecraft and the mods directory.
        additionalLocators.add(new InDevFolderLocator());
        additionalLocators.add(new InDevJarLocator());

        var modDiscoverer = new ModDiscoverer(new LaunchContextAdapter(), additionalLocators);
        var result = modDiscoverer.discoverMods(earlyServicesJars);

        var neoForgeVersion = versionInfo.neoForgeVersion();
        var minecraftVersion = versionInfo.mcVersion();
        for (var modFile : result.modFiles()) {
            var mods = modFile.getModFileInfo().getMods();
            if (mods.isEmpty()) {
                continue;
            }
            var mainMod = mods.getFirst();
            switch (modFile.getId()) {
                case "minecraft" -> minecraftVersion = mainMod.getVersion().toString();
                case "neoforge" -> neoForgeVersion = mainMod.getVersion().toString();
            }
        }

        versionInfo = new VersionInfo(neoForgeVersion, minecraftVersion, getVersionInfo().neoFormVersion());
        versionSupportMatrix = new VersionSupportMatrix(versionInfo);
        progress.complete();
        ImmediateWindowHandler.setMinecraftVersion(versionInfo.mcVersion());
        ImmediateWindowHandler.setNeoForgeVersion(versionInfo.neoForgeVersion());
        return result;
    }
            """.trimIndent()
        }

        if (!patched.contains("public ModDiscoverer.Result $twillDiscoverName(")) {
            val setupMarker = "    public DiscoveryResult $twillSetupName("
            val processorMarker = "    public static ClassProcessorSet createClassProcessorSet("
            val insertAt = when {
                patched.contains(setupMarker) -> setupMarker
                patched.contains(processorMarker) -> processorMarker
                else -> throw GradleException("Unsupported FML $fmlVersion: cannot locate Twill discovery insertion point")
            }
            patched = patched.replace(insertAt, twillDiscover + "\n\n" + insertAt)
        }

        val twillSetup = """
    public DiscoveryResult $twillSetupName(ModDiscoverer.Result discoveryResult) {
        loadingModList = ModSorter.sort(discoveryResult.modFiles(), discoveryResult.discoveryIssues());

        Map<IModInfo, JarResource> enumExtensionsByMod = new HashMap<>();
        for (var modFile : loadingModList.getAllModFiles()) {
            for (var mod : modFile.getModInfos()) {
                mod.getConfig().<String>getConfigElement("enumExtensions").ifPresent(file -> {
                    var resource = mod.getOwningFile().getFile().getContents().get(file);
                    if (resource == null) {
                        ModLoader.addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.enumextender.file_not_found", file).withAffectedMod(mod));
                        return;
                    }
                    enumExtensionsByMod.put(mod, resource);
                });
            }
        }
        RuntimeEnumExtender.loadEnumPrototypes(enumExtensionsByMod);

        backgroundScanHandler = new BackgroundScanHandler(loadingModList.getAllModFiles());
        return this.discoveryResult = new DiscoveryResult(
                loadingModList.getPlugins().stream().map(mfi -> (ModFile) mfi.getFile()).toList(),
                loadingModList.getModFiles().stream().map(ModFileInfo::getFile).toList(),
                loadingModList.getGameLibraries().stream().map(mf -> (ModFile) mf).toList(),
                loadingModList.getModLoadingIssues());
    }
        """.trimIndent()

        if (!patched.contains("public DiscoveryResult $twillSetupName(")) {
            val processorMarker = "    public static ClassProcessorSet createClassProcessorSet("
            if (!patched.contains(processorMarker)) {
                throw GradleException("Unsupported FML $fmlVersion: cannot locate class processor insertion point")
            }
            patched = patched.replace(processorMarker, twillSetup + "\n\n" + processorMarker)
        }

        val output = patchedFmlLoaderDirectory.get().file("FMLLoader.java").asFile
        output.parentFile.mkdirs()
        output.writeText(patched)
    }
}

version = "${rootProject.property("mod_version")}+${stonecutter.current.version}"
group = rootProject.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

sourceSets {
    getByName("main") {
        java.srcDir("src/main/java")
        java.srcDir("src/main/kotlin")

        // Use an isolated FML source copy with the three Kilt/Twill adapters
        // overlaid separately. The original classes are excluded during copy,
        // not via SourceDirectorySet.exclude (which would also hide the overlays).
        java.srcDir(patchedFmlSourcesDirectory)
        java.srcDir(patchedJarInJarDirectory)
        java.srcDir(patchedModFileDirectory)
        java.srcDir(patchedFmlLoaderDirectory)

        resources.srcDir("src/main/resources")
        resources.srcDir(rootProject.file("fml/v$fmlVersion/loader/src/main/resources"))

        // Kilt owns the Jar-in-Jar discovery path.
        resources.exclude(
            "META-INF/services/net.neoforged.neoforgespi.locating.IDependencyLocator"
        )
    }
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

repositories {
    maven("https://mvn.devos.one/releases")
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.florianreuth.de/snapshots")
    maven("https://maven.bawnorton.com/releases")
    maven("https://maven.enjarai.dev/mirrors")
    maven("https://api.modrinth.com/maven")
    maven("https://jitpack.io")
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${findProperty("minecraft_override") ?: stonecutter.current.version}")
    implementation(libs.fabric.loader)
    implementation(libs.fabric.kotlin)

    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api")}")

    val fmlProperties = Properties()
    fmlProperties.load(rootProject.file("fml/v$fmlVersion/gradle.properties").inputStream())
    api(include("net.neoforged:bus:${fmlProperties.getProperty("eventbus_version")}")!!)
    api(include("net.neoforged:accesstransformers:${fmlProperties.getProperty("accesstransformers_version")}")!!)
    api(include("net.neoforged:mergetool:${fmlProperties.getProperty("mergetool_version")}")!!)
    api(include("net.neoforged:JarJarSelector:${fmlProperties.getProperty("jarjar_version")}")!!)
    api(include("net.neoforged:JarJarMetadata:${fmlProperties.getProperty("jarjar_version")}")!!)

    // FML 12 moved annotations to JSpecify and uses SDL for its early error display.
    // Keep these optional for the older FML 11 profiles.
    fmlProperties.getProperty("asm_version")?.let { asmVersion ->
        api(include("org.ow2.asm:asm:${asmVersion}")!!)
        api(include("org.ow2.asm:asm-tree:${asmVersion}")!!)
        api(include("org.ow2.asm:asm-commons:${asmVersion}")!!)
    }
    fmlProperties.getProperty("jspecify_version")?.let { jspecifyVersion ->
        api(include("org.jspecify:jspecify:${jspecifyVersion}")!!)
    }
    fmlProperties.getProperty("lwjgl_version")?.let { lwjglVersion ->
        implementation(platform("org.lwjgl:lwjgl-bom:${lwjglVersion}"))
        if (fmlVersion == "12") {
            implementation("org.lwjgl:lwjgl-sdl")
        }
    }

    api(include("org.apache.maven:maven-artifact:${fmlProperties.getProperty("apache_maven_artifact_version")}")!!)
    api(include("com.electronwill.night-config:core:${fmlProperties.getProperty("nightconfig_version")}")!!)
    api(include("com.electronwill.night-config:toml:${fmlProperties.getProperty("nightconfig_version")}")!!)

    try {
        api(include(fletchingTable.modrinth("modmenu-badges-lib", stonecutter.current.version, "fabric"))!!)
    } catch (_: Throwable) {}

    implementation(libs.massasmer)
    include(libs.massasmer)

    api(libs.knit.loader.fabric)
    include(libs.bundles.knit.loader)

    api(libs.bundles.tiny.codecs)
    include(libs.bundles.tiny.codecs)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", stonecutter.current.version)
    filteringCharset = "UTF-8"

    exclude("log4j2.component.properties") // We don't need this

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("mc_version_range")!!,
            "loader_version" to libs.versions.fabric.loader.get(),
            "kotlin_loader_version" to libs.versions.fabric.kotlin.get(),
        )
    }
}

tasks.matching { it.name == "compileJava" || it.name == "compileKotlin" }.configureEach {
    dependsOn(patchKiltFmlLoader, patchKiltJarInJarLocator, patchKiltModFile)
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
