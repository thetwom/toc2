package toc2.toc2;

import android.content.res.Resources;

class Utilities {

    static int dp_to_px(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    static int sp_to_px(int sp) {
        return (int) (sp * Resources.getSystem().getDisplayMetrics().scaledDensity);
    }
}
