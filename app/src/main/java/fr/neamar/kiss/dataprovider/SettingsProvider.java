package fr.neamar.kiss.dataprovider;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Locale;

import fr.neamar.kiss.R;
import fr.neamar.kiss.loader.LoadSettingsPojos;
import fr.neamar.kiss.normalizer.StringNormalizer;
import fr.neamar.kiss.pojo.SettingsPojo;
import fr.neamar.kiss.searcher.Searcher;
import fr.neamar.kiss.utils.FuzzyScore;

public class SettingsProvider extends Provider<SettingsPojo> {
    private String settingName;
    private SharedPreferences prefs;

    @Override
    public void reload() {
        super.reload();
        this.initialize(new LoadSettingsPojos(this));
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        settingName = this.getString(R.string.settings_prefix).toLowerCase(Locale.ROOT);
    }

    @Override
    public void requestResults(String query, Searcher searcher) {
        if (!prefs.getBoolean("enable-settings", true)) {
            return;
        }

        StringNormalizer.Result queryNormalized = StringNormalizer.normalizeWithResult(query, true);

        if (queryNormalized.codePoints.length == 0) {
            return;
        }

        FuzzyScore fuzzyScore = new FuzzyScore(queryNormalized.codePoints);
        FuzzyScore.MatchInfo matchInfo;
        boolean match;

        for (SettingsPojo pojo : pojos) {
            matchInfo = fuzzyScore.match(pojo.normalizedName.codePoints);
            match = matchInfo.match;
            if (query.toLowerCase().contains("set")){
                match = true;
            }
            pojo.relevance = matchInfo.score;

            if (!match) {
                // Match localized setting name
                matchInfo = fuzzyScore.match(settingName);
                match = matchInfo.match;
                pojo.relevance = matchInfo.score;
            }

            if (match && !searcher.addResult(pojo)) {
                return;
            }
        }
    }
}
