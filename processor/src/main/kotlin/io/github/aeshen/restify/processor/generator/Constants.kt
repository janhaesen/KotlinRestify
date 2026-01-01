package io.github.aeshen.restify.processor.generator

import com.squareup.kotlinpoet.ClassName

const val BASE_PACKAGE = "io.github.aeshen.restify"
const val RUNTIME_PACKAGE = "$BASE_PACKAGE.runtime"

val collectionRawTypes =
    setOf(
        ClassName("kotlin.collections", "List"),
        ClassName("kotlin.collections", "Collection"),
        ClassName("kotlin.collections", "Set"),
        ClassName("kotlin.collections", "Iterable"),
        ClassName("kotlin", "Array"),
        ClassName("java.util", "List"),
        ClassName("java.util", "Collection"),
        ClassName("java.util", "Set"),
        ClassName("java.lang", "Iterable"),
    )
