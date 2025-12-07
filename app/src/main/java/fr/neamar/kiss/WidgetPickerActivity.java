package fr.neamar.kiss;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.neamar.kiss.utils.UserHandle;
import fr.neamar.kiss.utils.Utilities;

/**
 * Modern widget picker with preview images
 */
public class WidgetPickerActivity extends Activity {
    private static final String TAG = "WidgetPicker";
    public static final String EXTRA_WIDGET_BIND_ALLOWED = "widgetBindAllowed";

    private WidgetAdapter adapter;
    private List<ListItem> allItems = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply theme based on preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = prefs.getString("theme", "transparent");
        switch (theme) {
            case "dark":
                setTheme(R.style.AppThemeDark);
                break;
            case "transparent":
                setTheme(R.style.AppThemeTransparent);
                break;
            case "semi-transparent":
                setTheme(R.style.AppThemeSemiTransparent);
                break;
            case "semi-transparent-dark":
                setTheme(R.style.AppThemeSemiTransparentDark);
                break;
            case "transparent-dark":
                setTheme(R.style.AppThemeTransparentDark);
                break;
            case "amoled-dark":
                setTheme(R.style.AppThemeAmoledDark);
                break;
        }
        
        setContentView(R.layout.activity_widget_picker);
        UIColors.updateThemePrimaryColor(this);

        View loadingContainer = findViewById(R.id.loading_container);
        RecyclerView recyclerView = findViewById(R.id.widget_list);
        EditText searchBox = findViewById(R.id.widget_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WidgetAdapter(this::onWidgetSelected);
        recyclerView.setAdapter(adapter);

        // Setup search
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterWidgets(s.toString());
            }
        });

        // Load widgets
        final Context context = getApplicationContext();
        final List<ListItem>[] loadedItems = new List[1];
        Utilities.runAsync(t -> {
            loadedItems[0] = loadWidgets(context);
        }, t -> {
            if (loadedItems[0] != null) {
                allItems = loadedItems[0];
                loadingContainer.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setItems(allItems);
            }
        });
    }

    private void filterWidgets(String query) {
        if (TextUtils.isEmpty(query)) {
            adapter.setItems(allItems);
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.getDefault());
        List<ListItem> filtered = new ArrayList<>();

        String currentApp = null;
        List<WidgetItem> currentWidgets = new ArrayList<>();

        for (ListItem item : allItems) {
            if (item instanceof HeaderItem) {
                // Add previous app's widgets if any matched
                if (!currentWidgets.isEmpty()) {
                    filtered.add(new HeaderItem(currentApp, currentWidgets.get(0).appIcon, currentWidgets.size()));
                    filtered.addAll(currentWidgets);
                }
                currentApp = ((HeaderItem) item).appName;
                currentWidgets.clear();
            } else if (item instanceof WidgetItem) {
                WidgetItem widget = (WidgetItem) item;
                if (widget.widgetName.toLowerCase(Locale.getDefault()).contains(lowerQuery) ||
                    widget.appName.toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                    currentWidgets.add(widget);
                }
            }
        }

        // Add last app's widgets
        if (!currentWidgets.isEmpty()) {
            filtered.add(new HeaderItem(currentApp, currentWidgets.get(0).appIcon, currentWidgets.size()));
            filtered.addAll(currentWidgets);
        }

        adapter.setItems(filtered);
    }

    private void onWidgetSelected(WidgetItem widget) {
        Context context = getApplicationContext();
        Intent intent = getIntent();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);

        if (appWidgetId != 0) {
            boolean bindAllowed = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bindAllowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, 
                    widget.providerInfo.getProfile(), widget.providerInfo.provider, null);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                bindAllowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, 
                    widget.providerInfo.provider);
            }

            intent.putExtra(EXTRA_WIDGET_BIND_ALLOWED, bindAllowed);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widget.providerInfo.provider);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, widget.providerInfo.getProfile());
            }
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED, intent);
        }
        finish();
    }

    @WorkerThread
    private List<ListItem> loadWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        PackageManager pm = context.getPackageManager();

        // Group widgets by app
        Map<String, List<WidgetItem>> widgetsByApp = new HashMap<>();
        Map<String, Drawable> appIcons = new HashMap<>();

        for (AppWidgetProviderInfo info : providers) {
            String packageName = info.provider.getPackageName();
            String appName = packageName;
            Drawable appIcon = null;

            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                appName = appInfo.loadLabel(pm).toString();
                appIcon = appInfo.loadIcon(pm);
            } catch (Exception e) {
                Log.w(TAG, "Failed to get app info for " + packageName, e);
            }

            // Get widget name
            String widgetName = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                widgetName = info.loadLabel(pm);
            }
            if (widgetName == null) {
                widgetName = info.label;
            }
            if (widgetName == null) {
                widgetName = "Widget";
            }

            // Get widget description
            String description = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                CharSequence desc = info.loadDescription(context);
                if (desc != null) {
                    description = desc.toString();
                }
            }

            // Get widget size
            int minWidth = info.minWidth;
            int minHeight = info.minHeight;
            String sizeText = getSizeText(context, minWidth, minHeight);

            WidgetItem widget = new WidgetItem(appName, widgetName, description, sizeText, appIcon, info);

            if (!widgetsByApp.containsKey(appName)) {
                widgetsByApp.put(appName, new ArrayList<>());
                appIcons.put(appName, appIcon);
            }
            widgetsByApp.get(appName).add(widget);
        }

        // Sort apps alphabetically
        List<String> sortedApps = new ArrayList<>(widgetsByApp.keySet());
        Collections.sort(sortedApps, String::compareToIgnoreCase);

        // Build final list with headers
        List<ListItem> items = new ArrayList<>();
        for (String appName : sortedApps) {
            List<WidgetItem> widgets = widgetsByApp.get(appName);
            items.add(new HeaderItem(appName, appIcons.get(appName), widgets.size()));
            items.addAll(widgets);
        }

        return items;
    }

    private String getSizeText(Context context, int minWidth, int minHeight) {
        float density = context.getResources().getDisplayMetrics().density;
        int cellWidth = (int) ((minWidth / density + 30) / 70);
        int cellHeight = (int) ((minHeight / density + 30) / 70);
        cellWidth = Math.max(1, cellWidth);
        cellHeight = Math.max(1, cellHeight);
        return cellWidth + "×" + cellHeight;
    }

    // Data classes
    interface ListItem {
        int getType();
    }

    static class HeaderItem implements ListItem {
        final String appName;
        final Drawable appIcon;
        final int widgetCount;

        HeaderItem(String appName, Drawable appIcon, int count) {
            this.appName = appName;
            this.appIcon = appIcon;
            this.widgetCount = count;
        }

        @Override
        public int getType() {
            return 0;
        }
    }

    static class WidgetItem implements ListItem {
        final String appName;
        final String widgetName;
        final String description;
        final String sizeText;
        final Drawable appIcon;
        final AppWidgetProviderInfo providerInfo;
        Drawable previewDrawable;
        boolean previewLoaded = false;

        WidgetItem(String appName, String widgetName, String description, String sizeText, 
                   Drawable appIcon, AppWidgetProviderInfo providerInfo) {
            this.appName = appName;
            this.widgetName = widgetName;
            this.description = description;
            this.sizeText = sizeText;
            this.appIcon = appIcon;
            this.providerInfo = providerInfo;
        }

        @Override
        public int getType() {
            return 1;
        }
    }

    // Click listener interface (must be outside inner class)
    interface OnWidgetClickListener {
        void onClick(WidgetItem widget);
    }

    // Adapter
    class WidgetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_WIDGET = 1;

        private List<ListItem> items = new ArrayList<>();
        private final OnWidgetClickListener clickListener;

        WidgetAdapter(OnWidgetClickListener listener) {
            this.clickListener = listener;
        }

        void setItems(List<ListItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).getType();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View view = inflater.inflate(R.layout.widget_picker_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = inflater.inflate(R.layout.widget_picker_item_modern, parent, false);
                return new WidgetViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem item = items.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind((HeaderItem) item);
            } else if (holder instanceof WidgetViewHolder) {
                ((WidgetViewHolder) holder).bind((WidgetItem) item, clickListener);
            }
        }
    }

    // ViewHolders
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;
        private final TextView count;

        HeaderViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.header_icon);
            title = itemView.findViewById(R.id.header_title);
            count = itemView.findViewById(R.id.header_count);
        }

        void bind(HeaderItem item) {
            title.setText(item.appName);
            count.setText(String.valueOf(item.widgetCount));
            if (item.appIcon != null) {
                icon.setImageDrawable(item.appIcon);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }
        }
    }

    class WidgetViewHolder extends RecyclerView.ViewHolder {
        private final ImageView preview;
        private final ProgressBar previewLoading;
        private final ImageView appIcon;
        private final TextView widgetName;
        private final TextView appName;
        private final TextView widgetSize;
        private final TextView description;
        private Utilities.AsyncRun loadTask;

        WidgetViewHolder(View itemView) {
            super(itemView);
            preview = itemView.findViewById(R.id.widget_preview);
            previewLoading = itemView.findViewById(R.id.preview_loading);
            appIcon = itemView.findViewById(R.id.app_icon);
            widgetName = itemView.findViewById(R.id.widget_name);
            appName = itemView.findViewById(R.id.app_name);
            widgetSize = itemView.findViewById(R.id.widget_size);
            description = itemView.findViewById(R.id.widget_description);
        }

        void bind(WidgetItem item, OnWidgetClickListener listener) {
            widgetName.setText(item.widgetName);
            appName.setText(item.appName);
            widgetSize.setText(item.sizeText);

            if (item.appIcon != null) {
                appIcon.setImageDrawable(item.appIcon);
            }

            if (!TextUtils.isEmpty(item.description)) {
                description.setText(item.description);
                description.setVisibility(View.VISIBLE);
            } else {
                description.setVisibility(View.GONE);
            }

            // Load preview
            if (loadTask != null) {
                loadTask.cancel();
            }

            if (item.previewLoaded) {
                preview.setImageDrawable(item.previewDrawable);
                previewLoading.setVisibility(View.GONE);
            } else {
                preview.setImageDrawable(null);
                previewLoading.setVisibility(View.VISIBLE);

                Context context = itemView.getContext();
                final Drawable[] loadedPreview = new Drawable[1];
                loadTask = Utilities.runAsync(t -> {
                    loadedPreview[0] = loadWidgetPreview(context, item.providerInfo);
                }, t -> {
                    item.previewDrawable = loadedPreview[0];
                    item.previewLoaded = true;
                    preview.setImageDrawable(loadedPreview[0]);
                    previewLoading.setVisibility(View.GONE);
                });
            }

            itemView.setOnClickListener(v -> listener.onClick(item));
        }

        @WorkerThread
        private Drawable loadWidgetPreview(Context context, AppWidgetProviderInfo info) {
            Drawable preview = null;
            final int density = context.getResources().getDisplayMetrics().densityDpi;

            // Try to load preview image
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                preview = info.loadPreviewImage(context, density);
            }
            if (preview != null) return preview;

            // Try to load icon
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                preview = info.loadIcon(context, density);
            }
            if (preview != null) return preview;

            // Try from resources
            try {
                Resources resources = context.getPackageManager()
                    .getResourcesForApplication(info.provider.getPackageName());
                
                if (info.previewImage != 0) {
                    try {
                        preview = resources.getDrawableForDensity(info.previewImage, density, null);
                    } catch (Resources.NotFoundException ignored) {}
                }
                
                if (preview == null && info.icon != 0) {
                    try {
                        preview = resources.getDrawableForDensity(info.icon, density, null);
                    } catch (Resources.NotFoundException ignored) {}
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Failed to load preview for " + info.provider.getPackageName(), e);
            }

            // Fallback to app icon
            if (preview == null) {
                UserHandle userHandle;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    userHandle = new UserHandle(0, info.getProfile());
                } else {
                    userHandle = new UserHandle();
                }
                preview = KissApplication.getApplication(context).getIconsHandler()
                    .getDrawableIconForPackage(info.provider, userHandle);
            }

            return preview;
        }
    }
}
