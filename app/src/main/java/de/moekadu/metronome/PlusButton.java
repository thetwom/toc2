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

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.appcompat.widget.AppCompatImageButton;

public class PlusButton extends AppCompatImageButton {

       private final SpringAnimation springAnimationX = new SpringAnimation(this, DynamicAnimation.X).setSpring(
         new SpringForce()
                 .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                 .setStiffness(SpringForce.STIFFNESS_HIGH));
       private final SpringAnimation springAnimationY = new SpringAnimation(this, DynamicAnimation.Y).setSpring(
         new SpringForce()
                 .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                 .setStiffness(SpringForce.STIFFNESS_HIGH));


     PlusButton(Context context) {
         super(context);
         setScaleType(ScaleType.CENTER_CROP);
         setImageResource(R.drawable.ic_add);
         setBackground(null);
     }

    void reposition(float posX, float posY) {
        springAnimationX.getSpring().setFinalPosition(posX);
        springAnimationY.getSpring().setFinalPosition(posY);
        springAnimationX.start();
        springAnimationY.start();
    }

    void resetAppearance(){
         setImageResource(R.drawable.ic_add);
         setBackground(null);
    }

//    boolean contains(float posX, float posY) {
//         return contains(posX, posY, 0, 0);
//    }

    boolean contains(float posX, float posY, float xTol, float yTol) {

//        Log.v("Metronome", "getTop()=" +getTop() + "  getY()="+getY()+" getWidth()="+getWidth()+ "  getHeight()="+getHeight() + " posX="+posX + " posY="+posY);
        return (posX > getX()-xTol && posX < getX() + getWidth()+xTol
                && posY > getY()-yTol && posY < getY() + getHeight() +yTol);


    }
}
