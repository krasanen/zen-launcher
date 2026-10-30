package fr.neamar.kiss.loader;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;
import fr.neamar.kiss.TagsHandler;
import fr.neamar.kiss.db.DBHelper;
import fr.neamar.kiss.db.ShortcutRecord;
import fr.neamar.kiss.pojo.ShortcutPojo;
import fr.neamar.kiss.preference.DefaultLauncherPreference;
import fr.neamar.kiss.utils.ShortcutUtil;

import static fi.zmengames.zen.ZEvent.State.ACTION_SET_DEFAULT_LAUNCHER;

public class LoadShortcutsPojos extends LoadPojos<ShortcutPojo> {

    private static final String TAG = LoadShortcutsPojos.class.getSimpleName();

    public LoadShortcutsPojos(Context context) {
        super(context, ShortcutPojo.SCHEME);
    }

    @Override
    protected ArrayList<ShortcutPojo> doInBackground(Void... arg0) {
        Context context = this.context.get();
        if (context == null) {
            return new ArrayList<>();
        }

        TagsHandler tagsHandler = KissApplication.getApplication(context).getDataHandler().getTagsHandler();
        List<ShortcutRecord> records = includeDefaultLauncher(context, DBHelper.getShortcuts(context));
        ArrayList<ShortcutPojo> pojos = new ArrayList<>(records.size());

        for (ShortcutRecord shortcutRecord : records) {
            String id = ShortcutUtil.generateShortcutId(shortcutRecord.name);

            ShortcutPojo pojo = new ShortcutPojo(id, shortcutRecord.packageName, shortcutRecord.intentUri);

            pojo.setName(shortcutRecord.name);
            pojo.setTags(tagsHandler.getTags(pojo.id));

            pojos.add(pojo);
        }

        return pojos;
    }

    static List<ShortcutRecord> includeDefaultLauncher(Context context, List<ShortcutRecord> saved) {
        // This built-in action must survive shortcut resets and losing HOME role.
        // Older installs may still have a stored copy; replace it to avoid duplicates
        // and refresh its translated name after a language change.
        ArrayList<ShortcutRecord> records = new ArrayList<>(saved.size() + 1);
        for (ShortcutRecord record : saved) {
            if (!ACTION_SET_DEFAULT_LAUNCHER.toString().equals(record.intentUri)) {
                records.add(record);
            }
        }
        ShortcutRecord action = new ShortcutRecord();
        action.name = context.getString(R.string.default_launcher_title);
        action.packageName = "zen";
        action.intentUri = ACTION_SET_DEFAULT_LAUNCHER.toString();
        records.add(action);
        return records;
    }

}
