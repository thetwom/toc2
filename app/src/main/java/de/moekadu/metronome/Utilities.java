/*
 * Copyright 2019 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.metronome;

import android.content.res.Resources;

import java.text.DecimalFormat;

class Utilities {

    private static DecimalFormat bpmFormat = null;
    private static float sensitivityMin = 0.5f; // steps per cm
    private static float sensitivityMax = 10.0f; // steps per cm

    static float[] speedIncrements = {0.125f, 0.25f, 0.5f, 1f, 2f, 5f , 10f};

    static float dp_to_px(float dp) {
        return dp * Resources.getSystem().getDisplayMetrics().density;
    }

    static float px_to_dp(float px) {
        return (px / Resources.getSystem().getDisplayMetrics().density);
    }

    static int sp_to_px(int sp) {
        return (int) (sp * Resources.getSystem().getDisplayMetrics().scaledDensity);
    }

    static String getBpmString(float bpm){
        float tolerance = 1e-6f;

        int i = 1;
        while(i < speedIncrements.length && Math.abs(bpm / speedIncrements[i] - Math.round(bpm/speedIncrements[i])) < tolerance) {
            ++i;
        }
        float speedIncrement = speedIncrements[i-1];

        return getBpmString(bpm, speedIncrement);
    }

    static String getBpmString(float bpm, float speedIncrement){
        int digits = 0;
        float tolerance = 1e-6f;
        if(Math.abs(speedIncrement-0.125f) < tolerance){
            digits = 3;
        }
        else if(Math.abs(speedIncrement-0.25f) < tolerance){
            digits = 2;
        }
        else if(Math.abs(speedIncrement-0.5f) < tolerance){
            digits = 1;
        }

        if(bpmFormat == null) {
            bpmFormat = new DecimalFormat();

        }
        bpmFormat.setMinimumFractionDigits(digits);
        bpmFormat.setMaximumFractionDigits(digits);

        return bpmFormat.format(bpm);
    }

    static float sensitivity2percentage(float sensitivity){
        return (sensitivity - sensitivityMin) / (sensitivityMax - sensitivityMin) * 100.0f;
    }

    static float percentage2sensitivity(float percentage){
        return sensitivityMin + percentage / 100.0f * (sensitivityMax - sensitivityMin);
    }

    static float px2cm(float px){
        return px_to_dp(px) / 160.0f * 2.54f;
    }

    static float cm2px(float cm){
        return dp_to_px(cm * 160.0f / 2.54f);
    }

    static long speed2dt(float speed) {
        return Math.round(1000.0 * 60.0 / speed);
    }
}
