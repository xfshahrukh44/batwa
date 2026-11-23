package com.example.dailybalance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DailyCreditReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Store.dailyEnabled(context)) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(Store.lastCreditDate(context))) {
            Store.addTransaction(context, "Daily income", Store.dailyAmount(context), System.currentTimeMillis());
            Store.setLastCreditDate(context, today);
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null)
                SyncManager.sync(context);
        }
        Alarm.schedule(context);
    }
}
