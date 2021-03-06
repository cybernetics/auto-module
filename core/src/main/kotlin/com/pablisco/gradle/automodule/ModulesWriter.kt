package com.pablisco.gradle.automodule

import com.pablisco.gradle.automodule.utils.camelCase
import com.pablisco.gradle.automodule.utils.snakeCase
import com.squareup.kotlinpoet.*
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import java.io.File
import java.nio.file.Path
import com.squareup.kotlinpoet.FileSpec.Companion.builder as file
import com.squareup.kotlinpoet.FunSpec.Companion.constructorBuilder as constructor
import com.squareup.kotlinpoet.FunSpec.Companion.getterBuilder as getter
import com.squareup.kotlinpoet.PropertySpec.Companion.builder as property
import com.squareup.kotlinpoet.TypeSpec.Companion.classBuilder as type

internal fun ModuleNode.writeTo(
    directory: Path,
    fileName: String,
    rootModuleName: String
) {
    val rootTypeName = rootModuleName.camelCase()
    file("", fileName)
        .addImport("org.gradle.kotlin.dsl", "project")
        .addProperty(
            property(rootModuleName, ClassName("", rootTypeName))
                .receiver(DependencyHandler::class.asClassName())
                .getter(getter().addStatement("return $rootTypeName(this)").build())
                .build()
        )
        .addType(ModuleNode(rootTypeName, null, children).toType())
        .build().writeTo(directory)
}

private fun ModuleNode.toType(): TypeSpec =
    type(name.camelCase()).apply {
        if (hasNoChildren() || path == null) {
            primaryConstructor(constructor().addParameter(dependencyHandlerProperty).build())
        } else {
            primaryConstructor(
                constructor()
                    .addParameter(dependencyHandlerProperty)
                    .addParameter(dependencyProperty)
                    .build()
            )
            addSuperinterface(
                Dependency::class.asClassName(),
                delegate = CodeBlock.of("dependency")
            )
        }
        children.forEach { child -> write(child) }
    }.build()

private fun TypeSpec.Builder.write(node: ModuleNode) = node.apply {
    when {
        hasNoChildren() -> {
            addProperty(
                property(name.snakeCase(), Dependency::class.asClassName())
                    .initializer("""dh.project("$path")""").build()
            )
        }
        hasChildren() -> {
            addType(toType())
            addProperty(
                property(name.snakeCase(), ClassName.bestGuess(name.camelCase()))
                    .initializer("""${name.camelCase()}(dh)""").build()
            )
        }
    }
}

fun Path.toGradleCoordinates(): String = ":" + toString().replace(File.separatorChar, ':')

private val dependencyHandlerProperty =
    ParameterSpec("dh", DependencyHandler::class.asClassName())

private val ModuleNode.dependencyProperty
    get() = ParameterSpec.builder("dependency", Dependency::class.asClassName())
        .defaultValue("""dh.project("$path")""")
        .build()
