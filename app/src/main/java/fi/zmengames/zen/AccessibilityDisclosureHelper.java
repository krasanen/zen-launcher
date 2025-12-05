package fi.zmengames.zen;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

import fr.neamar.kiss.R;

/**
 * Helper class to show prominent disclosure for Accessibility Service usage
 * as required by Google Play policy.
 * 
 * The Accessibility Service is used solely for the "Turn Off Screen" feature
 * which allows users to lock their device screen with a gesture.
 */
public class AccessibilityDisclosureHelper {

    private static final String PREF_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted";

    /**
     * Check if the accessibility service is enabled for this app
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getId() != null && service.getId().contains(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user has already accepted the disclosure
     */
    public static boolean hasAcceptedDisclosure(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_DISCLOSURE_ACCEPTED, false);
    }

    /**
     * Mark that user has accepted the disclosure
     */
    public static void setDisclosureAccepted(Context context, boolean accepted) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_DISCLOSURE_ACCEPTED, accepted).apply();
    }

    /**
     * Show the prominent disclosure dialog.
     * This should be called before directing users to enable the accessibility service.
     * 
     * @param context The context to show the dialog in
     * @param onAccept Callback when user accepts and wants to enable the feature
     * @param onDecline Callback when user declines (optional, can be null)
     */
    public static void showDisclosureDialog(Context context, Runnable onAccept, Runnable onDecline) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.accessibility_disclosure_title)
                .setMessage(R.string.accessibility_disclosure_message)
                .setPositiveButton(R.string.accessibility_disclosure_accept, (dialog, which) -> {
                    setDisclosureAccepted(context, true);
                    if (onAccept != null) {
                        onAccept.run();
                    }
                })
                .setNegativeButton(R.string.accessibility_disclosure_decline, (dialog, which) -> {
                    if (onDecline != null) {
                        onDecline.run();
                    }
                })
                .setCancelable(true)
                .show();
    }

    /**
     * Open the accessibility settings screen
     */
    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback to general settings if accessibility settings can't be opened
            Intent fallbackIntent = new Intent(Settings.ACTION_SETTINGS);
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallbackIntent);
        }
    }

    /**
     * Show disclosure and then open accessibility settings if accepted.
     * Use this when the user tries to use a feature that requires accessibility service.
     * 
     * @param context The context
     * @param onDecline Optional callback when user declines
     */
    public static void showDisclosureAndOpenSettings(Context context, Runnable onDecline) {
        showDisclosureDialog(context, 
            () -> openAccessibilitySettings(context),
            onDecline
        );
    }

    /**
     * Check if we need to show disclosure (service not enabled and disclosure not yet accepted)
     * or if we can proceed directly to settings (disclosure already accepted but service disabled)
     */
    public static void requestAccessibilityPermission(Context context, Runnable onDecline) {
        if (isAccessibilityServiceEnabled(context)) {
            // Already enabled, nothing to do
            return;
        }

        if (hasAcceptedDisclosure(context)) {
            // User already accepted disclosure before, go directly to settings
            openAccessibilitySettings(context);
        } else {
            // Show disclosure first
            showDisclosureAndOpenSettings(context, onDecline);
        }
    }
}
