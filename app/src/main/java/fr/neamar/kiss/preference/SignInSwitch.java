package fi.zmengames.zlauncher.preference;

import android.content.Context;

import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;


import fi.zmengames.zlauncher.KissApplication;
import fi.zmengames.zlauncher.MainActivity;
import fi.zmengames.zlauncher.SwitchPreference;

public class SignInSwitch extends SwitchPreference {
    public SignInSwitch(Context context) {
        this(context, null);
    }

    public SignInSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.switchPreferenceStyle);
    }

    public SignInSwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }



    @Override
    protected void onClick() {
        Log.d("SignInSwitch","isChecked:"+isChecked());

        if (!isChecked()){
            Log.d("SignInSwitch","SIGN_IN");
            Intent i = new Intent(MainActivity.SIGN_IN);
            this.getContext().sendBroadcast(i);
        } else {
            Log.d("SignInSwitch","SIGN_OUT");
            Intent i = new Intent(MainActivity.SIGN_OUT);
            this.getContext().sendBroadcast(i);
        }
        super.onClick();

    }
}