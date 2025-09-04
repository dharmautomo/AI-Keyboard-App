/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.setup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import org.dslul.openboard.inputmethod.latin.R;

import androidx.core.view.ViewCompat;
import android.util.Log;

public final class SetupStepIndicatorView extends View {
    private final Path mIndicatorPath = new Path();
    private final Paint mIndicatorPaint = new Paint();
    private float mXRatio;

    public SetupStepIndicatorView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mIndicatorPaint.setColor(getResources().getColor(R.color.setup_step_background));
        mIndicatorPaint.setStyle(Paint.Style.FILL);
    }

    public void setIndicatorPosition(final int stepPos, final int totalStepNum) {
        try {
            if (totalStepNum <= 0) {
                Log.e("SetupStepIndicatorView", "setIndicatorPosition: totalStepNum <= 0: " + totalStepNum);
                return;
            }
            if (stepPos < 0 || stepPos >= totalStepNum) {
                Log.e("SetupStepIndicatorView", "setIndicatorPosition: stepPos out of range: " + stepPos + ", totalStepNum: " + totalStepNum);
                return;
            }
            
            final int layoutDirection = ViewCompat.getLayoutDirection(this);
            // The indicator position is the center of the partition that is equally divided into
            // the total step number.
            final float partionWidth = 1.0f / totalStepNum;
            final float pos = stepPos * partionWidth + partionWidth / 2.0f;
            mXRatio = (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) ? 1.0f - pos : pos;
            
            try {
                invalidate();
            } catch (Exception e) {
                Log.e("SetupStepIndicatorView", "Error calling invalidate: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e("SetupStepIndicatorView", "Error in setIndicatorPosition: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        try {
            super.onDraw(canvas);
            
            if (canvas == null) {
                Log.e("SetupStepIndicatorView", "onDraw: canvas is null");
                return;
            }
            
            final int width = getWidth();
            final int height = getHeight();
            
            if (width <= 0 || height <= 0) {
                Log.w("SetupStepIndicatorView", "onDraw: width or height is 0: width=" + width + ", height=" + height);
                return;
            }
            
            final int xPos = (int)(width * mXRatio);
            
            // Validate xPos to prevent drawing outside bounds
            if (xPos < 0 || xPos >= width) {
                Log.w("SetupStepIndicatorView", "onDraw: xPos out of bounds: " + xPos + ", width: " + width);
                return;
            }
            
            mIndicatorPath.rewind();
            mIndicatorPath.moveTo(xPos, 0);
            mIndicatorPath.lineTo(xPos + height, height);
            mIndicatorPath.lineTo(xPos - height, height);
            mIndicatorPath.close();
            
            try {
                canvas.drawPath(mIndicatorPath, mIndicatorPaint);
            } catch (Exception e) {
                Log.e("SetupStepIndicatorView", "Error drawing path: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e("SetupStepIndicatorView", "Error in onDraw: " + e.getMessage(), e);
        }
    }
}
