# Twofold release rules.
#
# This file was empty until PdfBox arrived, on the reasoning that RevenueCat, Compose and
# androidx.window all ship consumer rules inside their AARs. That reasoning held for those three
# and does not hold in general — PdfBox needed a rule here, and R8 refused to build until it got
# one. Check whether a new library ships consumer rules; do not assume it.

# PdfBox references an optional JPEG 2000 decoder it does not bundle. R8 treats the dangling
# reference as fatal.
#
# The consequence is real but narrow: a PDF whose pages are JPEG 2000 images will fail to render
# those images. Policy documents are text or JPEG, so this is an acceptable trade against adding a
# decoder dependency for a format nobody in this workflow uses. If a scanned JP2 policy ever turns
# up, the fix is to add com.gemalto:jp2-android rather than to widen this rule.
-dontwarn com.gemalto.jp2.**

# ML Kit loads its recognizer implementations reflectively through Play Services descriptors.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
