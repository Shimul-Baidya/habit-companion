package com.example.habit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.DocumentsContract;

/** Protected by the target APK's existing signature permission; affects only private test fixtures. */
public final class ExportQaControlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.example.habit.test.QA_EXPORT_ROOT".equals(intent.getAction())) return;
        boolean saved = context.getSharedPreferences("qa-control", Context.MODE_PRIVATE).edit()
                .putBoolean("active", intent.getBooleanExtra("active", false)).commit();
        if (saved) {
            context.getContentResolver().notifyChange(DocumentsContract.buildRootsUri("com.example.habit.test.exports"), null);
            setResultCode(Activity.RESULT_OK);
        }
    }
}
