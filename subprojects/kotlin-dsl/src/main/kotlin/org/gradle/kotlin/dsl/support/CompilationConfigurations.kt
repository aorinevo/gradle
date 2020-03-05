/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.support

import org.gradle.api.HasImplicitReceiver
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.kotlin.dsl.precompile.v1.scriptResolverEnvironmentOf
import org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver
import org.gradle.kotlin.dsl.tooling.models.EditorPosition
import org.gradle.kotlin.dsl.tooling.models.EditorReportSeverity
import org.jetbrains.kotlin.scripting.definitions.annotationsForSamWithReceivers
import java.io.File
import java.net.URL
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ExternalSourceCode
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath


internal
object KotlinBuildScriptCompilationConfiguration : ScriptCompilationConfiguration({

    kotlinDslScriptTemplate(
        implicitReceiver = Project::class,
        compilationConfiguration = KotlinBuildScriptCompilationConfiguration::class
    )
})


internal
object KotlinSettingsScriptCompilationConfiguration : ScriptCompilationConfiguration({

    kotlinDslScriptTemplate(
        implicitReceiver = Settings::class,
        compilationConfiguration = KotlinSettingsScriptCompilationConfiguration::class
    )
})


internal
object KotlinInitScriptCompilationConfiguration : ScriptCompilationConfiguration({

    kotlinDslScriptTemplate(
        implicitReceiver = Gradle::class,
        compilationConfiguration = KotlinInitScriptCompilationConfiguration::class
    )
})


private
fun ScriptCompilationConfiguration.Builder.kotlinDslScriptTemplate(
    implicitReceiver: KClass<*>,
    compilationConfiguration: KClass<*>
) {

    implicitReceivers(
        implicitReceiver
    )

    compilerOptions(
        "-jvm-target", "1.8",
        "-Xjsr305=strict",
        "-XXLanguage:+NewInference",
        "-XXLanguage:+SamConversionForKotlinFunctions"
    )

    defaultImports(
        "org.gradle.kotlin.dsl.*",
        "org.gradle.api.*"
    )

    jvm {
        dependenciesFromClassContext(
            compilationConfiguration,
            "gradle-kotlin-dsl",
            "gradle-api",
            "kotlin-stdlib", "kotlin-reflect"
// Full classpath extracted from gradle-kotlin-dsl-classpath.properties
// projects=gradle-base-services,gradle-native,gradle-logging,gradle-process-services,gradle-persistent-cache,gradle-core-api,gradle-model-core,gradle-core,gradle-base-services-groovy,gradle-file-collections,gradle-files,gradle-resources,gradle-build-cache,gradle-tooling-api,gradle-kotlin-dsl-tooling-models,kotlin-compiler-embeddable-1.3.50-patched-for-gradle
//            "gradle-kotlin-dsl-tooling-models",
//            "gradle-tooling-api",
//            "gradle-core-api",
//            "gradle-core",
//            "gradle-base-services",
//            "gradle-native",
//            "gradle-logging",
//            "gradle-process-services",
//            "gradle-persistent-cache",
//            "gradle-model-core",
//            "gradle-base-services-groovy",
//            "gradle-file-collections",
//            "gradle-files",
//            "gradle-resources",
//            "gradle-build-cache",
//            "kotlin-compiler-embeddable-1.3.50-patched-for-gradle",
//            "gradle-kotlin-dsl-tooling-models",
//            "groovy-all"
        )
    }

    annotationsForSamWithReceivers(
        HasImplicitReceiver::class
    )

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    refineConfiguration {
        beforeCompiling { context ->
            refineKotlinScriptConfiguration(context)
        }
    }
}


private
fun refineKotlinScriptConfiguration(
    context: ScriptConfigurationRefinementContext
): ResultWithDiagnostics.Success<ScriptCompilationConfiguration> {

    val script = context.script

    val diagnostics = mutableListOf<ScriptDiagnostic>()

    val scriptExternalDependencies = KotlinBuildScriptDependenciesResolver().run {
        resolve(
            scriptContentsOf(script),
            scriptResolverEnvironmentOf(context)!!,
            { severity, message, pos -> diagnostics.add(scriptDiagnosticOf(script, message, severity, pos)) },
            null
        )
    }.get()

    return context.compilationConfiguration.with {
        scriptExternalDependencies?.apply {
            updateClasspath(classpath.toList())
            defaultImports(imports.toList())
            ide.dependenciesSources(JvmDependency(sources.toList()))
            javaHome?.let { javaHome ->
                jvm.jdkHome(File(javaHome))
            }
        }
    }.asSuccess(diagnostics)
}


private
fun scriptContentsOf(script: SourceCode) =
    object : KotlinBuildScriptDependenciesResolver.ScriptContents {
        override val file: File? by lazy {
            (script as? ExternalSourceCode)?.externalLocation?.toFileOrNull()
        }
        override val text: CharSequence?
            get() = script.text
    }


private
fun scriptDiagnosticOf(
    script: SourceCode,
    message: String,
    severity: EditorReportSeverity,
    position: EditorPosition?
) = ScriptDiagnostic(
    message,
    ScriptDiagnostic.Severity.values()[severity.ordinal],
    script.name,
    position?.run {
        SourceCode.Location(
            SourceCode.Position(if (line == 0) 0 else line - 1, column)
        )
    }
)


internal
fun URL.toFileOrNull() =
    try {
        File(toURI().schemeSpecificPart).canonicalFile
    } catch (e: java.net.URISyntaxException) {
        if (protocol != "file") null
        else File(file).canonicalFile
    }
