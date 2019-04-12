package toc2.toc2;

import android.content.Context;
import android.support.animation.DynamicAnimation;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.v7.widget.AppCompatImageButton;

public class PlusButton extends AppCompatImageButton {

       final SpringAnimation springAnimationX = new SpringAnimation(this, DynamicAnimation.X).setSpring(
         new SpringForce()
                 .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                 .setStiffness(SpringForce.STIFFNESS_HIGH));
       final SpringAnimation springAnimationY = new SpringAnimation(this, DynamicAnimation.Y).setSpring(
         new SpringForce()
                 .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                 .setStiffness(SpringForce.STIFFNESS_HIGH));


     PlusButton(Context context) {
         super(context);
     }


}
