package app.cavla.extension.wolt;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Wolt "Export cart" extension. Fully reflective so the extension compiles against only the
 * Android SDK (no Wolt/androidx/kotlin compile deps). Called by ExportCartPatch from
 * CartController's toolbar-setup method with the CartController instance.
 *
 * Runtime descriptors (Wolt 26.40.1), verified by decompilation:
 *   CartController.getRecyclerView$new_order_release() : androidx RecyclerView (a View)
 *   RegularToolbar (Lcom/wolt/android/core_ui/widget/RegularToolbar;).setEndIcon(Integer,String,Function0)
 *   CartAdapter.getItems() : List<ItemModel>
 *   DishItemModel (Lcom/wolt/android/new_order/controllers/misc/DishItemModel;)
 *       .getName():String .getCountText():String .getNetPriceTotalFormatted():StringType
 *   StringType (Lcom/wolt/android/app_resources/StringType;).resolve(Context):CharSequence
 */
public final class CartExporter {

    private static final String TAG = "CavlaCartExporter";
    private static final String TOOLBAR_CLASS = "com.wolt.android.core_ui.widget.RegularToolbar";
    private static final String DISH_CLASS = "com.wolt.android.new_order.controllers.misc.DishItemModel";

    private CartExporter() {}

    /** Entry point injected into CartController's toolbar-setup method. */
    public static void installExportButton(Object controller) {
        try {
            final View recycler = (View) controller.getClass()
                    .getMethod("getRecyclerView$new_order_release").invoke(controller);
            if (recycler == null) return;

            final View toolbar = findByClassName(recycler.getRootView(), TOOLBAR_CLASS);
            final String venueName = venueName(controller);

            if (toolbar != null && addToolbarEndIcon(toolbar, recycler, venueName)) {
                return; // toolbar button installed
            }
            // Fallback: long-press the cart list to export.
            recycler.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { doExport(recycler, venueName); return true; }
            });
        } catch (Throwable t) {
            Log.e(TAG, "installExportButton failed", t);
        }
    }

    /** setEndIcon(Integer icon, String desc, Function0 onClick) via a reflective Function0 proxy. */
    private static boolean addToolbarEndIcon(final View toolbar, final View recycler, final String venueName) {
        try {
            ClassLoader cl = toolbar.getClass().getClassLoader();
            Class<?> function0 = cl.loadClass("kotlin.jvm.functions.Function0");
            final Object unit = cl.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            Object onClick = Proxy.newProxyInstance(cl, new Class[]{function0}, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String n = method.getName();
                    if ("invoke".equals(n)) { doExport(recycler, venueName); return unit; }
                    if ("toString".equals(n)) return "CavlaExport";
                    if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                    if ("equals".equals(n)) return proxy == (args != null ? args[0] : null);
                    return null;
                }
            });
            Integer icon = android.R.drawable.ic_menu_save;
            toolbar.getClass()
                    .getMethod("setEndIcon", Integer.class, String.class, function0)
                    .invoke(toolbar, icon, "Export cart", onClick);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "toolbar end-icon not installed, using long-press fallback", t);
            return false;
        }
    }

    private static void doExport(View recycler, String venueName) {
        Context ctx = recycler.getContext();
        try {
            Object adapter = recycler.getClass().getMethod("getAdapter").invoke(recycler);
            if (adapter == null) { toast(ctx, "Cart not ready"); return; }
            Object itemsObj = adapter.getClass().getMethod("getItems").invoke(adapter);
            if (!(itemsObj instanceof List)) { toast(ctx, "No cart items"); return; }

            StringBuilder csv = new StringBuilder("Name,Qty,Price\n");
            int rows = 0;
            for (Object item : (List<?>) itemsObj) {
                if (item == null || !DISH_CLASS.equals(item.getClass().getName())) continue;
                String name = str(call(item, "getName"));
                String qty = str(call(item, "getCountText"));
                String price = resolve(ctx, call(item, "getNetPriceTotalFormatted"));
                if (price.isEmpty()) price = resolve(ctx, call(item, "getNetPriceFormatted"));
                csv.append(csv(name)).append(',').append(csv(qty)).append(',').append(csv(price)).append('\n');
                rows++;
            }
            if (rows == 0) { toast(ctx, "Cart is empty"); return; }

            String fileName = sanitize(venueName) + "-cart.csv";
            if (saveToDownloads(ctx, fileName, csv.toString())) {
                toast(ctx, "Saved " + fileName + " to Downloads (" + rows + " items)");
            } else {
                shareText(ctx, csv.toString(), fileName);
            }
        } catch (Throwable t) {
            Log.e(TAG, "doExport failed", t);
            toast(ctx, "Export failed: " + t.getClass().getSimpleName());
        }
    }

    // --- sinks ---

    private static boolean saveToDownloads(Context ctx, String fileName, String content) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false; // older: fall back to share
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            if (os == null) return false;
            try { os.write(content.getBytes("UTF-8")); } finally { os.close(); }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "saveToDownloads failed", t);
            return false;
        }
    }

    private static void shareText(Context ctx, String content, String fileName) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/csv");
            i.putExtra(Intent.EXTRA_SUBJECT, fileName);
            i.putExtra(Intent.EXTRA_TEXT, content);
            Intent chooser = Intent.createChooser(i, "Export cart");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooser);
        } catch (Throwable t) {
            Log.e(TAG, "shareText failed", t);
            toast(ctx, "Export failed");
        }
    }

    // --- reflection helpers ---

    private static Object call(Object o, String method) {
        try { return o.getClass().getMethod(method).invoke(o); } catch (Throwable t) { return null; }
    }

    private static String resolve(Context ctx, Object stringType) {
        if (stringType == null) return "";
        try {
            Object cs = stringType.getClass().getMethod("resolve", Context.class).invoke(stringType, ctx);
            return cs == null ? "" : cs.toString();
        } catch (Throwable t) { return ""; }
    }

    private static String venueName(Object controller) {
        try {
            Object args = controller.getClass().getMethod("getArgs").invoke(controller);
            Object vn = args.getClass().getMethod("getVenueName").invoke(args);
            if (vn != null && !vn.toString().isEmpty()) return vn.toString();
        } catch (Throwable ignored) {}
        return "wolt";
    }

    private static View findByClassName(View root, String className) {
        if (root == null) return null;
        if (className.equals(root.getClass().getName())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findByClassName(g.getChildAt(i), className);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static String csv(String s) {
        if (s == null) s = "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "wolt";
        String out = s.replaceAll("[^a-zA-Z0-9-_ ]", "").trim().replaceAll("\\s+", "_");
        return out.isEmpty() ? "wolt" : out;
    }

    private static void toast(Context ctx, String msg) {
        try { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
    }
}
