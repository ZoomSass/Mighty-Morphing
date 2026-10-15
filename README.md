# Mighty Morphing Patches

![Mighty Morphing Patches](banner.png)

Patches for Joey for Reddit 2.1.6.5. Add the source in Morphe with this link:

https://morphe.software/add-source?github=ZoomSass/Mighty-Morphing

They target the original Joey package, `o.o.joey`, version 2.1.6.5.

**Skip ads and checks** is on by default. Ads stay off, including inline ads. The purchase prompt, the rate-the-app prompt, and the purchase-error dialog are skipped. The home-screen update nag is removed, the Play installer check is emptied, and Crashlytics reporting is turned into no-ops.

**RedGifs with sound** is on by default. A clip asks RedGifs for a temporary token, then plays the HD file, or the SD file if HD is missing. Silent files and Reddit's silent preview are skipped.

**Dismiss without tearing** is on by default. While a still photo is dragged away, it is drawn on a software layer so the picture does not tear. Video stays on the hardware path. On Android 11 and newer the viewer window is see-through.

**Rename to JoeyX** is off by default. Turn it on to use package `o.o.joeyx`, the matching permission and provider names, and the launcher name JoeyX. That installs beside the original app. Leave it off and a normal patch run stays `o.o.joey`, so it can still be combined with other Joey patches. After the rename, Morphe will not see that APK as Joey.

<!-- PATCHES_START EXPANDED -->

<!-- Do not modify this section by hand. The patch list is generated when release.yml creates a new release. -->

<!-- PATCHES_END -->

## License

Mighty Morphing Patches are licensed under the [GNU General Public License v3.0](LICENSE).
