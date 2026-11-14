# Xposed reaches module classes through assets and reflective discovery.
# Keep module names and members, while R8 removes unused library components.
-keep class !io.github.rikumi.gboardhints.R,!io.github.rikumi.gboardhints.R$*,io.github.rikumi.gboardhints.** { *; }
-dontwarn de.robv.android.xposed.**

-dontwarn io.github.libxposed.annotation.**
