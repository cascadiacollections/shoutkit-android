# FOSS variant: Play Core and the Cast framework are Play-flavor dependencies, so the
# FOSS base APK contains no GMS references of its own. `:cast` is still declared in
# `dynamicFeatures` (AGP has no per-flavor form of that setting), so keep suppressing
# R8 warnings for the classes it would otherwise pull into the reference graph.
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.play.core.**

# Reproducible builds: F-Droid builds this flavor without the :cast module, which shifts
# the order R8 assigns short names in, so the same source produced classes.dex files that
# differed only in obfuscated names. The FOSS source is public, so renaming protects
# nothing; R8 still shrinks and optimizes.
-dontobfuscate
