package com.retro99.loops.sdk.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class JvmAsyncProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return JvmAsyncProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
