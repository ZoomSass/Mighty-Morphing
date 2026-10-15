package app.mightymorphing.patches.joey

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal

object AdsDisabledFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/Activities/BaseActivity;",
    name = "b1",
    returnType = "Z",
    strings = listOf("inside areAdsDisabled"),
)

object InlineAdsFingerprint : Fingerprint(
    definingClass = "Lmb/f;",
    name = "a",
    returnType = "Z",
    strings = listOf("PREF_INLINE_ADS"),
)

object RatePromptFingerprint : Fingerprint(
    definingClass = "Lx8/j;",
    name = "n0",
    returnType = "Z",
    strings = listOf("app_rate_prefs"),
)

object PurchaseErrorPromptFingerprint : Fingerprint(
    definingClass = "Lx8/j;",
    name = "q0",
    returnType = "Z",
    strings = listOf("TROUBLE_GETTING_PURCHASE_DETAILS"),
)

object UpdateNagFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/Activities/HomeActivity;",
    name = "J3",
    returnType = "V",
    filters = listOf(literal(0x1f4)),
)

object InstallerCheckFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/Initializers/ProcessLifeCyleListener;",
    name = "n",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)

object PlayCheckFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/Initializers/ProcessLifeCyleListener;",
    name = "p",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(literal(0x64)),
)

object PurchasePromptFingerprint : Fingerprint(
    definingClass = "Lua/b;",
    name = "q",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
)

object PurchaseDialogFingerprint : Fingerprint(
    definingClass = "Lua/a;",
    name = "r2",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(literal(0x7f1307a7)),
)

object PreviousCrashFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/MyApplication;",
    name = "j",
    returnType = "V",
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction is com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction &&
                instruction.reference.toString().contains("didCrashOnPreviousExecution")
        } == true
    },
)

object CrashlyticsToggleFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/MyApplication;",
    name = "u",
    returnType = "V",
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction is com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction &&
                instruction.reference.toString().contains("setCrashlyticsCollectionEnabled")
        } == true
    },
)

object RecordExceptionFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
    name = "recordException",
    returnType = "V",
    strings = listOf("A null value was passed to recordException"),
)

object SendReportsFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
    name = "sendUnsentReports",
    returnType = "V",
    parameters = listOf(),
)

object UncaughtHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/crashlytics/internal/common/CrashlyticsController;",
    name = "z",
    returnType = "V",
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction is com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction &&
                instruction.reference.toString().contains("setDefaultUncaughtExceptionHandler")
        } == true
    },
)

object NativeCrashFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/crashlytics/ndk/FirebaseCrashlyticsNdk;",
    name = "c",
    returnType = "V",
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction is com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction &&
                instruction.reference.toString().contains("SignalHandlerInstaller")
        } == true
    },
)

object RedgifsAuthFingerprint : Fingerprint(
    definingClass = "Lod/b;",
    name = "u",
    returnType = "Z",
    strings = listOf("https://api.redgifs.com/v2/oauth/client"),
)

object RedgifsTokenFieldFingerprint : Fingerprint(
    definingClass = "Lod/b;",
    name = "g",
    returnType = "Ljava/lang/String;",
    strings = listOf("access_token"),
)

object RedgifsExpiryFingerprint : Fingerprint(
    definingClass = "Lod/b;",
    name = "h",
    returnType = "J",
    strings = listOf("expires_in"),
)

object RedgifsUrlFingerprint : Fingerprint(
    definingClass = "Lod/b;",
    name = "m",
    returnType = "Ljava/lang/String;",
    strings = listOf("hd", "urls"),
)

object RedgifsResolveFingerprint : Fingerprint(
    definingClass = "Lod/b;",
    name = "n",
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;", "Z"),
)

object RedgifsDirectFingerprint : Fingerprint(
    definingClass = "Lab/l;",
    name = "s",
    returnType = "Lab/l\$h;",
    strings = listOf(".mp4", "gfycat.com"),
)

object RedgifsIdFingerprint : Fingerprint(
    definingClass = "Lab/l;",
    name = "G",
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;", "L", "Z"),
)

object RedditPreviewFingerprint : Fingerprint(
    definingClass = "Lzd/e0;",
    name = "f",
    returnType = "Ljava/lang/String;",
    strings = listOf("reddit_video_preview", "hls_url"),
)

object SwipeResetFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/CustomViews/AnySwipeLayout;",
    name = "f",
    returnType = "V",
    parameters = listOf(),
)

object SwipeCaptureFingerprint : Fingerprint(
    definingClass = "Lo/o/joey/CustomViews/AnySwipeLayout\$d;",
    name = "onViewCaptured",
    returnType = "V",
)

object ViewerWindowFingerprint : Fingerprint(
    definingClass = "Lzd/p1;",
    name = "b",
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;"),
)
