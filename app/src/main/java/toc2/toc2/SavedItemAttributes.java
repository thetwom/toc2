package toc2.toc2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;


public class SavedItemAttributes extends View {

    public int deleteColor;
    public final int onDeleteColor;

    public SavedItemAttributes(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SavedItemAttributes);
        deleteColor = ta.getColor(R.styleable.SavedItemAttributes_deleteColor, Color.RED);
        onDeleteColor = ta.getColor(R.styleable.SavedItemAttributes_onDeleteColor, Color.WHITE);

        ta.recycle();
    }
}
