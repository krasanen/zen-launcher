package fr.neamar.kiss.preference;

import android.app.Dialog;
import android.os.Build;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toolbar;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayDeque;

public final class PreferenceScreenHelper {

	/**
	 * Fix the dialog ListView to not be hidden behind the toolbar.
	 * On Android 15+ (API 35+), edge-to-edge display causes the ListView content 
	 * to start at y=0 and get clipped by the toolbar. This adds top padding to fix it.
	 */
	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public static void fixDialogListViewPadding(Dialog dialog, @Nullable Toolbar toolbar) {
		// Only apply fix on API 35+ where edge-to-edge causes the issue
		if (Build.VERSION.SDK_INT < 35) {
			return;
		}
		
		if (dialog == null || dialog.getWindow() == null) {
			return;
		}

		final ListView listView = dialog.findViewById(android.R.id.list);
		if (listView == null) {
			return;
		}

		// Always post to ensure layout is complete
		listView.post(() -> {
			int requiredTopPadding = 0;
			
			// Get toolbar position (bottom of toolbar is where content should start)
			if (toolbar != null) {
				int[] toolbarLocation = new int[2];
				toolbar.getLocationInWindow(toolbarLocation);
				requiredTopPadding = toolbarLocation[1] + toolbar.getHeight();
				
				// Also get listview position to calculate relative offset
				int[] listLocation = new int[2];
				listView.getLocationInWindow(listLocation);
				requiredTopPadding = requiredTopPadding - listLocation[1];
			}
			
			// Fallback if calculation failed
			if (requiredTopPadding <= 0) {
				float density = listView.getContext().getResources().getDisplayMetrics().density;
				requiredTopPadding = (int) (56 * density);
			}

			if (listView.getPaddingTop() < requiredTopPadding) {
				listView.setPadding(listView.getPaddingLeft(), requiredTopPadding, listView.getPaddingRight(), listView.getPaddingBottom());
				listView.setClipToPadding(false);
			}
		});
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public static @Nullable Toolbar findToolbar(PreferenceScreen preference) {
		final Dialog dialog = preference.getDialog();
		if (dialog != null) {
			ViewGroup root = (ViewGroup) dialog.getWindow().getDecorView();

			ArrayDeque<ViewGroup> viewGroups = new ArrayDeque<>();
			viewGroups.push(root);

			while (!viewGroups.isEmpty()) {
				ViewGroup e = viewGroups.removeFirst();

				for (int i = 0; i < e.getChildCount(); i++) {
					View child = e.getChildAt(i);

					if (child instanceof Toolbar) {
						// Only in LOLLIPOP or higher you're going to find a Toolbar
						return (Toolbar) child;
					}

					if (child instanceof ViewGroup) {
						viewGroups.addFirst((ViewGroup) child);
					}
				}
			}
		}
		return null;
	}
}
