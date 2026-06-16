package com.retro99.loops.sdk.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

class JvmAsyncProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val ANNOTATION_FQN = "com.retro99.loops.sdk.ksp.JvmAsync"

        private val AUTO_IMPORTED_PACKAGES = setOf(
            "kotlin",
            "kotlin.annotation",
            "kotlin.collections",
            "kotlin.comparisons",
            "kotlin.io",
            "kotlin.ranges",
            "kotlin.sequences",
            "kotlin.text",
        )
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_FQN)
        val retry = mutableListOf<KSAnnotated>()

        for (symbol in symbols) {
            if (symbol is KSClassDeclaration) {
                val result = processClass(symbol)
                if (!result) {
                    retry.add(symbol)
                }
            }
        }

        return retry
    }

    private fun processClass(classDeclaration: KSClassDeclaration): Boolean {
        return try {
            val packageName = classDeclaration.packageName.asString()
            val className = classDeclaration.simpleName.asString()

            val suspendFunctions = classDeclaration.declarations
                .filterIsInstance<KSFunctionDeclaration>()
                .filter { func ->
                    func.modifiers.contains(Modifier.SUSPEND) &&
                        !func.modifiers.contains(Modifier.PRIVATE)
                }
                .toList()

            if (suspendFunctions.isEmpty()) return true

            // Generic functions can't be expressed as a plain extension wrapper without
            // re-declaring the type parameters, which this generator does not support.
            // Fail loudly rather than emit uncompilable code.
            val generic = suspendFunctions.firstOrNull { func -> func.typeParameters.isNotEmpty() }
            if (generic != null) {
                logger.error(
                    "@JvmAsync cannot generate an async wrapper for generic suspend function " +
                        "'${generic.simpleName.asString()}'; remove the type parameters or the " +
                        "@JvmAsync annotation.",
                    generic,
                )
                return true
            }

            val functions = suspendFunctions.map { func ->
                val funcName = func.simpleName.asString()
                val returnType = func.returnType?.resolve()
                val paramList = func.parameters.map { param ->
                    ParamInfo(
                        name = param.name?.asString() ?: "arg",
                        type = param.type.resolve(),
                        hasDefault = param.hasDefault,
                    )
                }
                FunctionInfo(funcName, returnType, paramList)
            }

            val imports = collectImports(functions)
            imports.add("java.util.concurrent.CompletableFuture")
            imports.add("kotlinx.coroutines.future.future")

            val content = generateCode(packageName, className, functions, imports)

            val classFile = classDeclaration.containingFile
            val deps = if (classFile != null) {
                Dependencies(false, classFile)
            } else {
                Dependencies(false)
            }
            codeGenerator.createNewFile(deps, packageName, "${className}Async")
                .use { it.write(content.toByteArray(Charsets.UTF_8)) }

            true
        } catch (e: Exception) {
            logger.error("Error processing class: ${e.message}", classDeclaration)
            false
        }
    }

    private fun collectImports(functions: List<FunctionInfo>): MutableSet<String> {
        val imports = mutableSetOf<String>()
        for ((_, returnType, params) in functions) {
            if (returnType != null) {
                collectTypeImports(returnType, imports)
            }
            for (param in params) {
                collectTypeImports(param.type, imports)
            }
        }
        return imports
    }

    private fun collectTypeImports(type: KSType, imports: MutableSet<String>) {
        val qn = type.declaration.qualifiedName?.asString() ?: return
        if (!isAutoImported(qn)) {
            imports.add(qn)
        }
        for (arg in type.arguments) {
            arg.type?.resolve()?.let { collectTypeImports(it, imports) }
        }
    }

    private fun isAutoImported(qualifiedName: String): Boolean {
        return AUTO_IMPORTED_PACKAGES.any { prefix ->
            qualifiedName == prefix || qualifiedName.startsWith("$prefix.")
        }
    }

    private fun generateCode(
        packageName: String,
        className: String,
        functions: List<FunctionInfo>,
        imports: Set<String>,
    ): String {
        return buildString {
            appendLine("@file:JvmName(\"${className}Async\")")
            appendLine("@file:Suppress(\"unused\")")
            appendLine()

            if (packageName.isNotEmpty()) {
                appendLine("package $packageName")
                appendLine()
            }

            imports.sorted().forEach { import ->
                appendLine("import $import")
            }
            appendLine()

            for ((funcName, returnType, params) in functions) {
                val returnTypeStr = if (returnType != null) {
                    formatType(returnType)
                } else {
                    "Unit"
                }

                // Emit the full-arity wrapper plus one shorter overload for each trailing
                // defaulted parameter, so callers (especially Java) can omit defaults that
                // the underlying suspend function already supplies.
                for (arity in overloadArities(params)) {
                    val included = params.take(arity)

                    val paramStr = included.joinToString(", ") { param ->
                        "${param.name}: ${formatType(param.type)}"
                    }

                    // Delegate with named arguments so omitted trailing params fall back to
                    // the suspend function's declared defaults.
                    val argStr = included.joinToString(", ") { param ->
                        "${param.name} = ${param.name}"
                    }

                    appendLine(
                        "fun $className.${funcName}Async($paramStr): " +
                            "CompletableFuture<$returnTypeStr> ="
                    )
                    // The async scope is owned by LoopsHttp and reached via the `http` property,
                    // shared by LoopsClient and every sub-API (see LoopsHttp.asyncScope).
                    appendLine(
                        "    http.asyncScope.future { $funcName($argStr) }"
                    )
                    appendLine()
                }
            }
        }
    }

    private fun formatType(type: KSType): String {
        val simpleName = type.declaration.qualifiedName?.asString()
            ?.substringAfterLast(".") ?: "Any"
        val typeArgs = type.arguments.map { arg ->
            // A null `arg.type` is a star projection (`*`).
            arg.type?.let { formatType(it.resolve()) } ?: "*"
        }
        val result = if (typeArgs.isNotEmpty()) {
            "$simpleName<${typeArgs.joinToString(", ")}>"
        } else {
            simpleName
        }
        return if (type.isMarkedNullable) "$result?" else result
    }

    /**
     * Arities to generate wrappers for, from shortest to full. The full arity is always
     * included; shorter ones drop a trailing run of defaulted parameters. A defaulted
     * parameter that is followed by a required one cannot be dropped, so the shortest
     * arity never goes below the index of the last required parameter.
     */
    private fun overloadArities(params: List<ParamInfo>): List<Int> {
        val fullArity = params.size
        val lastRequiredIndex = params.indexOfLast { param -> !param.hasDefault }
        val minArity = lastRequiredIndex + 1
        return (minArity..fullArity).toList()
    }

    private data class FunctionInfo(
        val funcName: String,
        val returnType: KSType?,
        val params: List<ParamInfo>,
    )

    private data class ParamInfo(
        val name: String,
        val type: KSType,
        val hasDefault: Boolean,
    )
}
