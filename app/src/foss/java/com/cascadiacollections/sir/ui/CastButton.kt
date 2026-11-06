package com.cascadiacollections.sir.ui

import androidx.compose.ui.Modifier

/**
 * FOSS build: no Cast icon. Mirrors `src/play/.../ui/CastButton.kt`'s signature
 * exactly so shared code in `main` compiles against either flavor unchanged — see
 * `CastFeatureManager` for the same convention applied to the non-UI Cast API.
 * Not `@Composable`: it emits nothing, and a plain function is callable from
 * composable code all the same (lint: ComposeRedundantComposable). It keeps the
 * PascalCase name so callers match the play flavor, hence the FunctionNaming suppress.
 */
@Suppress("UNUSED_PARAMETER", "FunctionNaming")
fun CastButton(modifier: Modifier = Modifier) = Unit
