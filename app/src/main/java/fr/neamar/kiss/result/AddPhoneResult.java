package fr.neamar.kiss.result;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;

import fi.zmengames.zen.ZEvent;
import fr.neamar.kiss.R;
import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.forwarder.Permission;
import fr.neamar.kiss.pojo.PhoneAddPojo;
import fr.neamar.kiss.pojo.PhonePojo;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.ui.ListPopup;
import fr.neamar.kiss.utils.FuzzyScore;

public class AddPhoneResult extends Result {
    private final PhoneAddPojo phonePojo;

    AddPhoneResult(PhoneAddPojo phonePojo) {
        super(phonePojo);
        this.phonePojo = phonePojo;
    }

    @NonNull
    @Override
    public View display(Context context, int position, View v, @NonNull ViewGroup parent, FuzzyScore fuzzyScore) {
        if (v == null)
            v = inflateFromId(context, R.layout.item_addphone, parent);

        TextView phoneText = v.findViewById(R.id.item_phone_text);

        String text = String.format(context.getString(R.string.menu_phone_create) + " “%s“", phonePojo.phone);
        int pos = text.indexOf(phonePojo.phone);
        int len = phonePojo.phone.length();
        displayHighlighted(text, Collections.singletonList(new Pair<Integer, Integer>(pos, pos + len)), phoneText, context);

        ((ImageView) v.findViewById(R.id.item_phone_icon)).setColorFilter(getThemeFillColor(context), PorterDuff.Mode.SRC_IN);

        return v;
    }

    @Override
    protected ListPopup buildPopupMenu(Context context, ArrayAdapter<ListPopup.Item> adapter, final RecordAdapter parent, View parentView) {
        adapter.add(new ListPopup.Item(context, R.string.menu_remove));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_add));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_remove));
        adapter.add(new ListPopup.Item(context, R.string.menu_phone_create));
        adapter.add(new ListPopup.Item(context, R.string.ui_item_contact_hint_message));

        return inflatePopupMenu(adapter, context);
    }

    @Override
    protected boolean popupMenuClickHandler(Context context, RecordAdapter parent, int stringId, View parentView) {
        switch (stringId) {
            case R.string.menu_phone_create:
                // Create a new contact with this phone number
                createContact(pojo,parentView.getContext());
                return true;
            case R.string.ui_item_contact_hint_message:
                String url = "sms:" + phonePojo.phone;
                Intent messageIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
                messageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(messageIntent);
                return true;
        }

        return super.popupMenuClickHandler(context, parent, stringId, parentView);
    }

    private void createContact(Pojo pojo, Context context) {
        // Create a new contact with this phone number
        Intent createIntent = new Intent(Intent.ACTION_INSERT);
        createIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);
        createIntent.putExtra(ContactsContract.Intents.Insert.PHONE, phonePojo.phone);
        createIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(createIntent);
        } catch ( ActivityNotFoundException e){
            EventBus.getDefault().post(new ZEvent(ZEvent.State.SHOW_TOAST, context.getString(R.string.application_not_found)));
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void doLaunch(Context context, View v) {
        createContact(pojo,v.getContext());
    }

    @Override
    public Drawable getDrawable(Context context) {
        //noinspection deprecation: getDrawable(int, Theme) requires SDK 21+
        return context.getResources().getDrawable(android.R.drawable.ic_menu_call);
    }
}
