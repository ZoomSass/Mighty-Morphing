package app.mightymorphing.extension.joey;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inline GIFs, images and emotes in Joey comments.
 *
 * Reddit only sends links or codes in body_html. The real files are in media_metadata,
 * parsed the same way as Infinity for Reddit (JSONUtils.parseMediaMetadata), including
 * the Giphy fallback for entries Reddit now marks "invalid".
 */
@SuppressWarnings("unused")
public final class CommentMedia {
    private static final String TAG = "MightyMedia";
    private static final String MARK = "#mmedia=";
    private static final int MAX_BYTES = 12 * 1024 * 1024;

    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\s[^>]*?href=\"([^\"]*)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMG = Pattern.compile(
            "<img\\s[^>]*?src=\"([^\"]*)\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT = Pattern.compile(
            "https?://[^\\s\"?#]+?/([^/\\s\"?#]+)\\.(jpe?g|png|gif|webp)(\\?[^\\s\"#]*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_HINT = Pattern.compile("\\.(jpe?g|png|gif|webp)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMGUR = Pattern.compile(
            "https?://i\\.imgur\\.com/(\\w+)\\.(jpe?g|png|webp)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)");

    private static final ExecutorService LOADER = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, byte[]> CACHE = new LruCache<String, byte[]>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return value.length;
        }
    };

    private CommentMedia() {
    }

    private static final class Media {
        String key;
        boolean emote;
        boolean gif;
        String thumb;
        String full;
        int w;
        int h;
    }

    // region HTML

    /**
     * Returns body_html with media turned into marked img tags, or the original html when
     * nothing applies. Called from the comment row with the raw media_metadata json.
     */
    public static String html(String html, String metadataJson) {
        if (html == null) return html;
        try {
            Map<String, Media> media = metadataJson == null ? new HashMap<>() : parse(metadataJson);
            if (media.isEmpty() && !DIRECT_HINT.matcher(html).find()) return html;

            boolean escaped = html.indexOf('<') < 0 && html.contains("&lt;");
            String work = escaped ? unescape(html) : html;
            String out = rewrite(work, media);
            if (out.equals(work)) {
                if (!media.isEmpty()) Log.d(TAG, "no match for " + media.keySet() + " in " + snippet(work));
                return html;
            }
            return escaped ? escape(out) : out;
        } catch (Throwable t) {
            Log.w(TAG, "html rewrite failed", t);
            return html;
        }
    }

    private static String rewrite(String html, Map<String, Media> media) {
        StringBuffer sb = new StringBuffer();
        Matcher m = ANCHOR.matcher(html);
        while (m.find()) {
            String href = unescape(m.group(1));
            Media item = matchUrl(href, media);
            if (item == null) item = matchText(unescape(m.group(2)), media);
            if (item == null && wholeParagraph(html, m.start(), m.end())) item = direct(href);
            String replacement = item == null ? m.group(0) : tag(item, item.emote ? null : href);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        html = sb.toString();

        sb = new StringBuffer();
        m = MARKDOWN_IMAGE.matcher(html);
        while (m.find()) {
            Media item = media.get(unescape(m.group(1)));
            String replacement = item == null ? m.group(0) : tag(item, null);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        html = sb.toString();

        sb = new StringBuffer();
        m = IMG.matcher(html);
        while (m.find()) {
            String src = unescape(m.group(1));
            Media item = src.contains(MARK) ? null : matchUrl(src, media);
            String replacement = item == null ? m.group(0) : tag(item, null);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean wholeParagraph(String html, int start, int end) {
        String before = html.substring(Math.max(0, start - 16), start).replaceAll("\\s+$", "");
        String after = html.substring(end, Math.min(html.length(), end + 16)).replaceAll("^\\s+", "");
        return before.toLowerCase().endsWith("<p>") && after.toLowerCase().startsWith("</p>");
    }

    /** A link that points straight at an image file. Its size is unknown until it loads. */
    private static Media direct(String href) {
        Matcher m = DIRECT.matcher(href);
        if (!m.matches()) return null;
        String ext = m.group(2).toLowerCase();
        Media item = new Media();
        item.key = href;
        item.gif = ext.equals("gif");
        item.full = href;
        item.thumb = href;
        Matcher imgur = IMGUR.matcher(href);
        if (!item.gif && imgur.matches()) {
            // Imgur's medium (320px) thumbnail.
            item.thumb = "https://i.imgur.com/" + imgur.group(1) + "m." + imgur.group(2);
        }
        return item;
    }

    private static Media matchUrl(String url, Map<String, Media> media) {
        if (url == null) return null;
        for (Media item : media.values()) {
            if (item.key.startsWith("giphy|")) {
                String id = item.key.split("\\|")[1];
                if (url.contains("giphy.com") && url.contains(id)) return item;
            } else if (item.key.startsWith("emote|")) {
                if (url.equals(item.thumb)) return item;
            } else if (url.contains("redd.it/" + item.key + ".") || url.contains("redd.it/" + item.key + "?")) {
                return item;
            }
        }
        return null;
    }

    private static Media matchText(String text, Map<String, Media> media) {
        if (text == null) return null;
        String trimmed = text.trim();
        Media item = media.get(trimmed);
        if (item != null) return item;
        Matcher m = MARKDOWN_IMAGE.matcher(trimmed);
        return m.matches() ? media.get(m.group(1)) : null;
    }

    private static String tag(Media item, String linkHref) {
        String href = item.emote ? "" : (linkHref != null && !linkHref.isEmpty() ? linkHref : item.full);
        String kind = item.emote ? "e" : (item.gif ? "g" : "i");
        String src = item.thumb + MARK + kind + "," + item.w + "," + item.h + "," + Uri.encode(href == null ? "" : href);
        return "<img src=\"" + attr(src) + "\">";
    }

    // endregion

    // region media_metadata

    private static Map<String, Media> parse(String json) throws Exception {
        Map<String, Media> out = new HashMap<>();
        JSONObject root = new JSONObject(json);
        for (Iterator<String> it = root.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONObject entry = root.optJSONObject(key);
            if (entry == null) continue;

            if ("invalid".equals(entry.optString("status"))) {
                if (key.startsWith("giphy|")) {
                    String[] parts = key.split("\\|");
                    if (parts.length >= 2) {
                        Media item = new Media();
                        item.key = key;
                        item.gif = true;
                        item.thumb = "https://media.giphy.com/media/" + parts[1] + "/200.gif";
                        item.full = "https://giphy.com/gifs/" + parts[1];
                        item.w = 0;
                        item.h = 200;
                        out.put(key, item);
                    }
                }
                continue;
            }

            JSONObject s = entry.optJSONObject("s");
            if (s == null) continue;
            String e = entry.optString("e", "Image");
            String id = entry.optString("id", key);

            Media item = new Media();
            item.key = id;
            item.emote = id.startsWith("emote|") || "emote".equalsIgnoreCase(entry.optString("t"));
            item.gif = !"Image".equalsIgnoreCase(e) && !"Video".equalsIgnoreCase(e);
            item.w = s.optInt("x");
            item.h = s.optInt("y");

            String original = clean(item.gif ? s.optString("gif", s.optString("u")) : s.optString("u"));
            if (original.isEmpty()) continue;
            item.full = item.gif ? clean(s.optString("mp4", original)) : original;
            if (item.full.isEmpty()) item.full = original;
            item.thumb = original;

            if (!item.gif && !item.emote) {
                JSONArray p = entry.optJSONArray("p");
                if (p != null && p.length() > 0) {
                    JSONObject pick = null;
                    for (int i = 0; i < p.length(); i++) {
                        JSONObject candidate = p.optJSONObject(i);
                        if (candidate != null && candidate.optInt("x") >= 320) {
                            pick = candidate;
                            break;
                        }
                    }
                    if (pick == null) pick = p.optJSONObject(p.length() - 1);
                    String u = pick == null ? "" : clean(pick.optString("u"));
                    if (!u.isEmpty()) item.thumb = u;
                }
            }
            if (key.startsWith("giphy|")) {
                String[] parts = key.split("\\|");
                if (parts.length >= 2) item.full = "https://giphy.com/gifs/" + parts[1];
            }
            item.key = key;
            out.put(key, item);
        }
        return out;
    }

    private static String clean(String url) {
        return url == null ? "" : url.replace("&amp;", "&");
    }

    // endregion

    // region spans

    /**
     * Replaces placeholder ImageSpans made from our img tags with sized, clickable media.
     * Called at the start of HTMLTextView.setSpannable.
     */
    public static void bind(TextView view, SpannableStringBuilder text) {
        if (view == null || text == null) return;
        try {
            ImageSpan[] spans = text.getSpans(0, text.length(), ImageSpan.class);
            if (spans == null || spans.length == 0) return;
            Resources res = view.getResources();
            float density = res.getDisplayMetrics().density;
            int screen = res.getDisplayMetrics().widthPixels;
            int maxMediaW = Math.min((int) (280 * density), screen - (int) (72 * density));
            int maxMediaH = (int) (160 * density);
            int emoteH = Math.round(view.getTextSize() * 1.35f);

            for (ImageSpan span : spans) {
                String source = span.getSource();
                if (source == null) continue;
                int at = source.indexOf(MARK);
                if (at < 0) continue;
                String url = source.substring(0, at);
                String[] meta = source.substring(at + MARK.length()).split(",", 4);
                if (meta.length < 4) continue;
                boolean emote = "e".equals(meta[0]);
                int w = toInt(meta[1]);
                int h = toInt(meta[2]);
                String href = Uri.decode(meta[3]);

                int start = text.getSpanStart(span);
                int end = text.getSpanEnd(span);
                int flags = text.getSpanFlags(span);
                text.removeSpan(span);

                MediaSpan media = emote
                        ? new MediaSpan(view, url, null, w, h, emoteH, emoteH * 4, emoteH, false)
                        : new MediaSpan(view, url, href.isEmpty() ? null : href, w, h, maxMediaH, maxMediaW, maxMediaH, true);
                text.setSpan(media, start, end, flags);
                media.load();
            }
        } catch (Throwable t) {
            Log.w(TAG, "bind failed", t);
        }
    }

    private static WeakReference<TextView> pressedView;
    private static MediaSpan pressedSpan;
    private static float pressedX;
    private static float pressedY;

    /**
     * Called first in Joey's comment link handler. Returns null when the touch is not on a GIF
     * or image, Boolean.TRUE to consume it, or a URLSpan to open when a tap on media completes.
     */
    public static Object touch(TextView view, Spannable text, MotionEvent event) {
        try {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                MediaSpan hit = hit(view, text, event);
                if (hit == null) {
                    pressedSpan = null;
                    return null;
                }
                pressedView = new WeakReference<>(view);
                pressedSpan = hit;
                pressedX = event.getX();
                pressedY = event.getY();
                return Boolean.TRUE;
            }
            if (pressedSpan == null || pressedView == null || pressedView.get() != view) return null;

            if (action == MotionEvent.ACTION_MOVE) {
                int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                if (Math.abs(event.getX() - pressedX) > slop || Math.abs(event.getY() - pressedY) > slop) {
                    pressedSpan = null;
                }
                return Boolean.TRUE;
            }
            MediaSpan pressed = pressedSpan;
            pressedSpan = null;
            if (action == MotionEvent.ACTION_UP && hit(view, text, event) == pressed) {
                return new URLSpan(pressed.href);
            }
            return Boolean.TRUE;
        } catch (Throwable t) {
            Log.w(TAG, "touch failed", t);
            pressedSpan = null;
            return null;
        }
    }

    private static MediaSpan hit(TextView view, Spannable text, MotionEvent event) {
        Layout layout = view.getLayout();
        if (layout == null || text == null) return null;
        int x = (int) event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
        int line = layout.getLineForVertical(y);
        MediaSpan[] spans = text.getSpans(layout.getLineStart(line), layout.getLineEnd(line), MediaSpan.class);
        int baseline = layout.getLineBaseline(line);
        for (MediaSpan span : spans) {
            if (!span.framed || span.href == null) continue;
            int start = text.getSpanStart(span);
            if (start < layout.getLineStart(line) || start >= layout.getLineEnd(line)) continue;
            float left = layout.getPrimaryHorizontal(start);
            if (x >= left && x <= left + span.width && y >= baseline - span.height && y <= baseline) {
                return span;
            }
        }
        return null;
    }

    private static int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static final class MediaSpan extends ReplacementSpan implements Drawable.Callback {
        private final WeakReference<TextView> view;
        private final String url;
        final String href;
        private final int targetH;
        private final int maxW;
        private final int maxH;
        final boolean framed;
        private final Paint placeholder = new Paint(Paint.ANTI_ALIAS_FLAG);
        int width;
        int height;
        private final boolean fixedHeight;
        private int drawH;
        private Drawable drawable;

        MediaSpan(TextView view, String url, String href, int w, int h, int targetH, int maxW, int maxH, boolean framed) {
            this.view = new WeakReference<>(view);
            this.url = url;
            this.href = href;
            this.targetH = targetH;
            this.maxW = maxW;
            this.maxH = maxH;
            this.framed = framed;
            placeholder.setColor(0x33808080);
            // Unknown size: reserve the full height now so the comment does not jump on load.
            fixedHeight = framed && (w <= 0 || h <= 0);
            fit(w, h);
            drawH = height;
        }

        private void fit(int w, int h) {
            if (w <= 0 || h <= 0) {
                w = 1;
                h = 1;
            }
            float scale = Math.min((float) targetH / h, 1f * maxW / w);
            if (!framed) scale = (float) targetH / h;
            int fw = Math.max(1, Math.round(w * scale));
            int fh = Math.max(1, Math.round(h * scale));
            if (fw > maxW) {
                fh = Math.max(1, Math.round(fh * (1f * maxW / fw)));
                fw = maxW;
            }
            if (fh > maxH) {
                fw = Math.max(1, Math.round(fw * (1f * maxH / fh)));
                fh = maxH;
            }
            width = fw;
            height = fh;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt base = paint.getFontMetricsInt();
                if (framed) {
                    fm.ascent = -height;
                    fm.descent = 0;
                } else {
                    int extra = Math.max(0, height - (base.descent - base.ascent));
                    fm.ascent = base.ascent - extra / 2;
                    fm.descent = base.descent + (extra - extra / 2);
                }
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return width;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            int dy;
            if (framed) {
                dy = y - height;
            } else {
                Paint.FontMetricsInt base = paint.getFontMetricsInt();
                dy = y + (base.ascent + base.descent) / 2 - height / 2;
            }
            if (drawable == null) {
                float r = framed ? 6 * Resources.getSystem().getDisplayMetrics().density : height / 4f;
                canvas.drawRoundRect(new RectF(x, dy, x + width, dy + height), r, r, placeholder);
                return;
            }
            canvas.save();
            canvas.translate(x, dy + (height - drawH) / 2);
            drawable.setBounds(0, 0, width, drawH);
            drawable.draw(canvas);
            canvas.restore();
        }

        void load() {
            final int decodeH = height;
            LOADER.execute(() -> {
                try {
                    byte[] bytes = fetch(url);
                    if (bytes == null) return;
                    Object decoded = decode(bytes, decodeH);
                    MAIN.post(() -> attach(decoded));
                } catch (Throwable t) {
                    Log.d(TAG, "load failed " + url + ": " + t);
                }
            });
        }

        private void attach(Object decoded) {
            TextView tv = view.get();
            if (tv == null || !(decoded instanceof Drawable)) return;
            Drawable d = (Drawable) decoded;
            int oldW = width;
            int oldH = height;
            fit(d.getIntrinsicWidth(), d.getIntrinsicHeight());
            drawH = height;
            if (fixedHeight) height = oldH;
            drawable = d;
            d.setCallback(this);
            if (d instanceof Animatable) ((Animatable) d).start();

            CharSequence current = tv.getText();
            if (current instanceof Spannable) {
                Spannable sp = (Spannable) current;
                int s = sp.getSpanStart(this);
                if (s >= 0 && (oldW != width || oldH != height)) {
                    int e = sp.getSpanEnd(this);
                    int f = sp.getSpanFlags(this);
                    sp.removeSpan(this);
                    sp.setSpan(this, s, e, f);
                }
            }
            tv.invalidate();
        }

        private boolean shown(TextView tv) {
            CharSequence current = tv.getText();
            return current instanceof Spanned && ((Spanned) current).getSpanStart(this) >= 0;
        }

        @Override
        public void invalidateDrawable(Drawable who) {
            TextView tv = view.get();
            if (tv == null || !shown(tv)) {
                if (who instanceof Animatable) ((Animatable) who).stop();
                who.setCallback(null);
                return;
            }
            tv.invalidate();
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            MAIN.postAtTime(what, who, when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            MAIN.removeCallbacks(what, who);
        }
    }

    // endregion

    // region loading

    private static byte[] fetch(String url) throws Exception {
        byte[] cached = CACHE.get(url);
        if (cached != null) return cached;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "Joey");
        try {
            if (conn.getResponseCode() / 100 != 2) {
                Log.d(TAG, "http " + conn.getResponseCode() + " for " + url);
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                byte[] bytes = out.toByteArray();
                CACHE.put(url, bytes);
                return bytes;
            }
        } finally {
            conn.disconnect();
        }
    }

    private static Object decode(byte[] bytes, int targetH) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
            return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                int h = info.getSize().getHeight();
                int w = info.getSize().getWidth();
                int limit = Math.max(targetH * 2, 1);
                if (h > limit) {
                    decoder.setTargetSize(Math.max(1, w * limit / h), limit);
                }
            });
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outHeight / (opts.inSampleSize * 2) >= targetH * 2) opts.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        return bitmap == null ? null : new BitmapDrawable(Resources.getSystem(), bitmap);
    }

    // endregion

    // region text helpers

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&#x27;", "'").replace("&amp;", "&");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String attr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String snippet(String s) {
        return s.length() > 600 ? s.substring(0, 600) + "…" : s;
    }

    // endregion
}
