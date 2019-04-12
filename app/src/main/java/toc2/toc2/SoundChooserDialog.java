package toc2.toc2;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SoundChooserDialog {

    public interface NewButtonPropertiesListener {
        void onNewButtonProperties(Bundle properties);
    }

    NewButtonPropertiesListener newButtonPropertiesListener = null;

    Bundle newProperties = null;

    public SoundChooserDialog(Activity activity) {
        if (activity != null) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
            dialogBuilder.setTitle("Sound properties");

            dialogBuilder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //if(newButtonPropertiesListener != null && newProperties != null){
                    if(newButtonPropertiesListener != null){
                        newButtonPropertiesListener.onNewButtonProperties(newProperties);
                    }
                }
            });

            dialogBuilder.setNegativeButton("dismiss", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            ViewGroup viewGroup = activity.findViewById(android.R.id.content);
            View dialogView = LayoutInflater.from(activity).inflate(R.layout.sound_chooser_dialog, viewGroup, false);
            dialogBuilder.setView(dialogView);
            AlertDialog dialog = dialogBuilder.create();
            dialog.show();
        }
    }

    public void setNewButtonPropertiesListener(NewButtonPropertiesListener newButtonPropertiesListener) {
        this.newButtonPropertiesListener = newButtonPropertiesListener;
    }
}
