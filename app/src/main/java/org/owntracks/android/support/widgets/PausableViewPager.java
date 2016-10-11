package org.owntracks.android.support.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;


public class PausableViewPager  extends android.support.v4.view.ViewPager{

    public PausableViewPager(Context context, AttributeSet attrs) {
            super(context, attrs);
        }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

}
