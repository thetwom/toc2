package toc2.toc2;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {

    public static final String CHANNEL_ID = "toc2";
    private static final String CHANNEL_NAME = "toc2";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );

            serviceChannel.setSound(null,null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if(manager != null)
                manager.createNotificationChannel(serviceChannel);
        }
    }
}
