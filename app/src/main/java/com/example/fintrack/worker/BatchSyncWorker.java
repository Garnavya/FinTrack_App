package com.example.fintrack.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.Calendar;
import com.example.fintrack.ui.MainActivity;
import com.example.fintrack.R;

public class BatchSyncWorker extends Worker {

    public BatchSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        // The "Busy Hours" Gate (Do not disturb between 9 AM and 5 PM)
        if (hour >= 9 && hour <= 17) {
            return Result.success();
        }

        sendPromptNotification();
        return Result.success();
    }

    private void sendPromptNotification() {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "clearslate_sync";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Sync Reminders", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("ClearSlate")
                .setContentText("Time to fetch today's financial details.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        manager.notify(1, builder.build());
    }
}
