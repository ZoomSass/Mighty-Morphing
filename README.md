# Mighty Morphing Patches

![Mighty Morphing Patches](banner.png)

Patches for Joey for Reddit 2.1.6.5, package `o.o.joey`.

Add the source in Morphe:

https://morphe.software/add-source?github=ZoomSass/Mighty-Morphing

<!-- PATCHES_START EXPANDED -->
Release [1.1.1](https://github.com/ZoomSass/Mighty-Morphing/releases/tag/v1.1.1) on `main`. 6 patches.

Joey

Works with 2.1.6.5.

- Dismiss without tearing. Swiping a photo away to close it looks smooth instead of tearing.
- Fix login page. Makes Reddit's login page load and work properly inside Joey, and clears the cookie banner out of the way.
- Inline comment media. Shows GIFs, pictures and subreddit emojis right in the comments instead of as links, kept small so threads stay readable. Tap a GIF or picture to open it full size.
- Log in with your own client ID. Lets Joey log in with another app's client ID, for when Joey's own login stops working. Sign in to RedReader once, then paste the App ID from Reddit's "you authorized an app" email. Leave it empty and nothing changes.
  - Client ID: The App ID from Reddit's "you authorized an app" email. Leave empty to keep Joey's own login.
  - Redirect URI: Has to match the app the ID came from. The default works for RedReader.
  - User agent: Sent with Reddit requests. The default is RedReader's.
- RedGifs with sound. RedGifs clips play with their sound, in HD when it's available.
- Skip ads and checks. Removes ads everywhere and also drops the Play Store install check and crash reporting.

<!-- PATCHES_END -->

Issues: https://github.com/ZoomSass/Mighty-Morphing/issues

## License

Mighty Morphing Patches are licensed under the [GNU General Public License v3.0](LICENSE).
