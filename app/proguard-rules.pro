# Twofold release rules.
#
# Deliberately empty, and verified rather than assumed.
#
# The usual reason to add keep rules here would be RevenueCat, whose offerings parsing uses
# reflection that R8 is happy to strip. Both purchases-10.16.1.aar and
# purchases-store-galaxy-10.16.1.aar ship their own consumer proguard.txt, so those rules are
# applied automatically at merge time. Adding our own would be redundant and would quietly go stale
# against the library's.
#
# Compose and androidx.window likewise ship consumer rules.
#
# The minified release build was installed and driven end to end on both a foldable and a
# flat-screen emulator — launch, SAF import, PDF render, posture split, signature — with no
# ClassNotFoundException, NoSuchMethodError or NoClassDefFoundError.
#
# If you add a library that reflects or deserializes, check whether it ships consumer rules before
# writing anything here.
