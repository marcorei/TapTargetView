/**
 * Copyright 2016 Keepsafe Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.getkeepsafe.taptargetview;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * TapTargetView implements a feature discovery paradigm following Google's Material Design
 * guidelines.
 * <p>
 * This class should not be instantiated directly. Instead, please use the
 * {@link #showFor(Activity, TapTarget, Listener)} static factory method instead.
 * <p>
 * More information can be found here:
 * https://material.google.com/growth-communications/feature-discovery.html#feature-discovery-design
 */
@SuppressLint("ViewConstructor")
public class TapTargetView extends View {
  private boolean isDismissed = false;
  private boolean isInteractable = true;

  final int TARGET_PADDING;
  final int TARGET_RADIUS;
  final int TARGET_WIDTH;
  final int TARGET_HEIGHT;
  final int TARGET_PULSE_RADIUS;
  final int TEXT_PADDING;
  final int TEXT_SPACING;
  final int TEXT_MAX_WIDTH;
  final int TEXT_POSITIONING_BIAS;
  final int CIRCLE_PADDING;
  final int GUTTER_DIM;
  final int SHADOW_DIM;
  final int SHADOW_JITTER_DIM;

  @Nullable
  final ViewGroup boundingParent;
  final ViewManager parent;
  final TapTarget target;
  final Rect targetBounds;

  final TextPaint titlePaint;
  final TextPaint descriptionPaint;
  final TextPaint confirmlabelPaint;
  final Paint outerCirclePaint;
  final Paint outerCircleShadowPaint;
  final Paint targetCirclePaint;
  final Paint targetCirclePulsePaint;

  final boolean pulseEnabled;

  CharSequence title;
  @Nullable
  StaticLayout titleLayout;
  @Nullable
  CharSequence description;
  @Nullable
  StaticLayout descriptionLayout;
  @Nullable
  CharSequence confirmLabel;
  @Nullable
  StaticLayout confirmLabelLayout;
  boolean isDark;
  boolean debug;
  boolean shouldTintTarget;
  boolean shouldDrawShadow;
  boolean cancelable;
  boolean visible;

  // Debug related variables
  @Nullable
  SpannableStringBuilder debugStringBuilder;
  @Nullable
  DynamicLayout debugLayout;
  @Nullable
  TextPaint debugTextPaint;
  @Nullable
  Paint debugPaint;

  // Drawing properties
  Rect drawingBounds;
  Rect textBounds;
  RectF targetShapeBounds = new RectF();
  RectF targetPulseShapeBounds = new RectF();

  Path outerCirclePath;
  float outerCircleRadius;
  int calculatedOuterCircleRadius;
  int[] outerCircleCenter;
  int outerCircleAlpha;

  float targetCirclePulseRadius;
  float targetCirclePulseWidth;
  float targetCirclePulseHeight;
  int targetCirclePulseAlpha;

  float targetCircleRadius;
  float targetCircleWidth;
  float targetCircleHeight;
  int targetCircleAlpha;

  int textAlpha;
  int dimColor;

  float lastTouchX;
  float lastTouchY;

  int topBoundary;
  int bottomBoundary;

  Bitmap tintedTarget;

  Listener listener;

  @Nullable
  ViewOutlineProvider outlineProvider;

  public static TapTargetView showFor(Activity activity, TapTarget target) {
    return showFor(activity, target, null);
  }

  public static TapTargetView showFor(Activity activity, TapTarget target, Listener listener) {
    if (activity == null) throw new IllegalArgumentException("Activity is null");

    final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
    final ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    final ViewGroup content = (ViewGroup) decor.findViewById(android.R.id.content);
    final TapTargetView tapTargetView = new TapTargetView(activity, decor, content, target, listener);
    decor.addView(tapTargetView, layoutParams);

    return tapTargetView;
  }

  public static TapTargetView showFor(Dialog dialog, TapTarget target) {
    return showFor(dialog, target, null);
  }

  public static TapTargetView showFor(Dialog dialog, TapTarget target, Listener listener) {
    if (dialog == null) throw new IllegalArgumentException("Dialog is null");

    final Context context = dialog.getContext();
    final WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
    params.type = WindowManager.LayoutParams.TYPE_APPLICATION;
    params.format = PixelFormat.RGBA_8888;
    params.flags = 0;
    params.gravity = Gravity.START | Gravity.TOP;
    params.x = 0;
    params.y = 0;
    params.width = WindowManager.LayoutParams.MATCH_PARENT;
    params.height = WindowManager.LayoutParams.MATCH_PARENT;

    final TapTargetView tapTargetView = new TapTargetView(context, windowManager, null, target, listener);
    windowManager.addView(tapTargetView, params);

    return tapTargetView;
  }

  public static class Listener {
    /** Signals that the user has clicked inside of the target **/
    public void onTargetClick(TapTargetView view) {
      view.dismiss(true);
    }

    /** Signals that the user has long clicked inside of the target **/
    public void onTargetLongClick(TapTargetView view) {
      onTargetClick(view);
    }

    /** If cancelable, signals that the user has clicked outside of the outer circle **/
    public void onTargetCancel(TapTargetView view) {
      view.dismiss(false);
    }

    /** Signals that the user clicked on the outer circle portion of the tap target **/
    public void onOuterCircleClick(TapTargetView view) {
      view.dismiss(true);
    }

    /**
     * Signals that the tap target has been dismissed
     * @param userInitiated Whether the user caused this action
     */
    public void onTargetDismissed(TapTargetView view, boolean userInitiated) {
    }
  }

  final FloatValueAnimatorBuilder.UpdateListener expandContractUpdateListener = new FloatValueAnimatorBuilder.UpdateListener() {
    @Override
    public void onUpdate(float lerpTime) {
      final float newOuterCircleRadius = calculatedOuterCircleRadius * lerpTime;
      final boolean expanding = newOuterCircleRadius > outerCircleRadius;
      if (!expanding) {
        // When contracting we need to invalidate the old drawing bounds. Otherwise
        // you will see artifacts as the circle gets smaller
        calculateDrawingBounds();
      }

      final float targetAlpha = target.outerCircleAlpha * 255;
      outerCircleRadius = newOuterCircleRadius;
      outerCircleAlpha = (int) Math.min(targetAlpha, (lerpTime * 1.5f * targetAlpha));
      outerCirclePath.reset();
      if (outerCircleCenter != null) {
        outerCirclePath.addCircle(outerCircleCenter[0], outerCircleCenter[1], outerCircleRadius, Path.Direction.CW);
      }

      targetCircleAlpha = (int) Math.min(255.0f, (lerpTime * 1.5f * 255.0f));

      if (expanding) {
        float scaleFactor = Math.min(1.0f, lerpTime * 1.5f);
        targetCircleRadius = TARGET_RADIUS * scaleFactor;
        targetCircleWidth = TARGET_WIDTH * scaleFactor;
        targetCircleHeight = TARGET_HEIGHT * scaleFactor;
      } else {
        targetCircleRadius = TARGET_RADIUS * lerpTime;
        targetCircleWidth = TARGET_WIDTH * lerpTime;
        targetCircleHeight = TARGET_HEIGHT * lerpTime;
        targetCirclePulseRadius *= lerpTime;
        targetCirclePulseWidth *= lerpTime;
        targetCirclePulseHeight *= lerpTime;
      }

      textAlpha = (int) (delayedLerp(lerpTime, 0.7f) * 255);

      if (expanding) {
        calculateDrawingBounds();
      }

      invalidateViewAndOutline(drawingBounds);
    }
  };

  final ValueAnimator expandAnimation = new FloatValueAnimatorBuilder()
      .duration(250)
      .delayBy(250)
      .interpolator(new AccelerateDecelerateInterpolator())
      .onUpdate(new FloatValueAnimatorBuilder.UpdateListener() {
        @Override
        public void onUpdate(float lerpTime) {
          expandContractUpdateListener.onUpdate(lerpTime);
        }
      })
      .onEnd(new FloatValueAnimatorBuilder.EndListener() {
        @Override
        public void onEnd() {
          pulseAnimation.start();
        }
      })
      .build();

  final ValueAnimator pulseAnimation = new FloatValueAnimatorBuilder()
      .duration(1000)
      .repeat(ValueAnimator.INFINITE)
      .interpolator(new AccelerateDecelerateInterpolator())
      .onUpdate(new FloatValueAnimatorBuilder.UpdateListener() {
        @Override
        public void onUpdate(float lerpTime) {
          final float pulseLerp = delayedLerp(lerpTime, 0.5f);
          targetCirclePulseRadius = (1.0f + pulseLerp) * TARGET_RADIUS;
          float pulseDr = targetCirclePulseRadius - TARGET_RADIUS;
          targetCirclePulseWidth = TARGET_WIDTH + 2 * pulseDr;
          targetCirclePulseHeight = TARGET_HEIGHT + 2 * pulseDr;
          targetCirclePulseAlpha = (int) ((1.0f - pulseLerp) * 255);

          float targetDr = halfwayLerp(lerpTime) * TARGET_PULSE_RADIUS;

          targetCircleRadius = TARGET_RADIUS + targetDr ;
          targetCircleWidth = TARGET_WIDTH + targetDr * 2;
          targetCircleHeight = TARGET_HEIGHT + targetDr * 2;

          if (outerCircleRadius != calculatedOuterCircleRadius) {
            outerCircleRadius = calculatedOuterCircleRadius;
          }

          calculateDrawingBounds();
          invalidateViewAndOutline(drawingBounds);
        }
      })
      .build();

  final ValueAnimator dismissAnimation = new FloatValueAnimatorBuilder(true)
      .duration(250)
      .interpolator(new AccelerateDecelerateInterpolator())
      .onUpdate(new FloatValueAnimatorBuilder.UpdateListener() {
        @Override
        public void onUpdate(float lerpTime) {
          expandContractUpdateListener.onUpdate(lerpTime);
        }
      })
      .onEnd(new FloatValueAnimatorBuilder.EndListener() {
        @Override
        public void onEnd() {
          ViewUtil.removeView(parent, TapTargetView.this);
          onDismiss();
        }
      })
      .build();

  private final ValueAnimator dismissConfirmAnimation = new FloatValueAnimatorBuilder()
      .duration(250)
      .interpolator(new AccelerateDecelerateInterpolator())
      .onUpdate(new FloatValueAnimatorBuilder.UpdateListener() {
        @Override
        public void onUpdate(float lerpTime) {
          final float spedUpLerp = Math.min(1.0f, lerpTime * 2.0f);
          outerCircleRadius = calculatedOuterCircleRadius * (1.0f + (spedUpLerp * 0.2f));
          outerCircleAlpha = (int) ((1.0f - spedUpLerp) * target.outerCircleAlpha * 255.0f);
          outerCirclePath.reset();
          if (outerCircleCenter != null) {
            outerCirclePath.addCircle(outerCircleCenter[0], outerCircleCenter[1], outerCircleRadius, Path.Direction.CW);
          }
          targetCircleRadius = (1.0f - lerpTime) * TARGET_RADIUS;
          targetCircleWidth = (1.0f - lerpTime) * TARGET_WIDTH;
          targetCircleHeight = (1.0f - lerpTime) * TARGET_HEIGHT;
          targetCircleAlpha = (int) ((1.0f - lerpTime) * 255.0f);
          targetCirclePulseRadius = (1.0f + lerpTime) * TARGET_RADIUS;
          targetCirclePulseWidth = (1.0f + lerpTime) * TARGET_WIDTH;
          targetCirclePulseHeight = (1.0f + lerpTime) * TARGET_HEIGHT;
          targetCirclePulseAlpha = (int) ((1.0f - lerpTime) * targetCirclePulseAlpha);
          textAlpha = (int) ((1.0f - spedUpLerp) * 255.0f);
          calculateDrawingBounds();
          invalidateViewAndOutline(drawingBounds);
        }
      })
      .onEnd(new FloatValueAnimatorBuilder.EndListener() {
        @Override
        public void onEnd() {
          ViewUtil.removeView(parent, TapTargetView.this);
          onDismiss();
        }
      })
      .build();

  private ValueAnimator[] animators = new ValueAnimator[]
      {expandAnimation, pulseAnimation, dismissConfirmAnimation, dismissAnimation};

  private final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;

  /**
   * This constructor should only be used directly for very specific use cases not covered by
   * the static factory methods.
   *
   * @param context The host context
   * @param parent The parent that this TapTargetView will become a child of. This parent should
   *               allow the largest possible area for this view to utilize
   * @param boundingParent Optional. Will be used to calculate boundaries if needed. For example,
   *                       if your view is added to the decor view of your Window, then you want
   *                       to adjust for system ui like the navigation bar or status bar, and so
   *                       you would pass in the content view (which doesn't include system ui)
   *                       here.
   * @param target The {@link TapTarget} to target
   * @param userListener Optional. The {@link Listener} instance for this view
   */
  public TapTargetView(final Context context,
                       final ViewManager parent,
                       @Nullable final ViewGroup boundingParent,
                       final TapTarget target,
                       @Nullable final Listener userListener) {
    super(context);
    if (target == null) throw new IllegalArgumentException("Target cannot be null");

    this.target = target;
    this.parent = parent;
    this.boundingParent = boundingParent;
    this.listener = userListener != null ? userListener : new Listener();
    this.title = target.title;
    this.description = target.description;
    this.confirmLabel = target.confirmLabel;

    TARGET_PADDING = UiUtil.dp(context, 20);
    CIRCLE_PADDING = UiUtil.dp(context, 40);
    TARGET_RADIUS = UiUtil.dp(context, target.targetRadius);
    TARGET_WIDTH = UiUtil.dp(context, Math.max(target.targetRadius * 2, target.targetWidth));
    TARGET_HEIGHT = UiUtil.dp(context, Math.max(target.targetRadius * 2, target.targetHeight));
    TEXT_PADDING = UiUtil.dp(context, 40);
    TEXT_SPACING = UiUtil.dp(context, 8);
    TEXT_MAX_WIDTH = UiUtil.dp(context, 360);
    TEXT_POSITIONING_BIAS = UiUtil.dp(context, 20);
    GUTTER_DIM = UiUtil.dp(context, 88);
    SHADOW_DIM = UiUtil.dp(context, 8);
    SHADOW_JITTER_DIM = UiUtil.dp(context, 1);
    TARGET_PULSE_RADIUS = (int) (0.1f * TARGET_RADIUS);

    pulseEnabled = target.targetPulseEnabled;

    outerCirclePath = new Path();
    targetBounds = new Rect();
    drawingBounds = new Rect();

    titlePaint = new TextPaint();
    titlePaint.setTextSize(target.titleTextSizePx(context));
    titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    titlePaint.setAntiAlias(true);

    descriptionPaint = new TextPaint();
    descriptionPaint.setTextSize(target.descriptionTextSizePx(context));
    descriptionPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
    descriptionPaint.setAntiAlias(true);
//    descriptionPaint.setAlpha((int) (0.54f * 255.0f));

    confirmlabelPaint = new TextPaint();
    confirmlabelPaint.setTextSize(target.confirmLabelTextSizePx(context));
    confirmlabelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
    confirmlabelPaint.setAntiAlias(true);

    outerCirclePaint = new Paint();
    outerCirclePaint.setAntiAlias(true);
    outerCirclePaint.setAlpha((int) (target.outerCircleAlpha * 255.0f));

    outerCircleShadowPaint = new Paint();
    outerCircleShadowPaint.setAntiAlias(true);
    outerCircleShadowPaint.setAlpha(50);
    outerCircleShadowPaint.setStyle(Paint.Style.STROKE);
    outerCircleShadowPaint.setStrokeWidth(SHADOW_JITTER_DIM);
    outerCircleShadowPaint.setColor(Color.BLACK);

    targetCirclePaint = new Paint();
    targetCirclePaint.setAntiAlias(true);

    targetCirclePulsePaint = new Paint();
    targetCirclePulsePaint.setAntiAlias(true);

    applyTargetOptions(context);

    globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        updateTextLayouts();
        target.onReady(new Runnable() {
          @Override
          public void run() {
            final int[] offset = new int[2];

            targetBounds.set(target.bounds());

            getLocationOnScreen(offset);
            targetBounds.offset(-offset[0], -offset[1]);

            if (boundingParent != null) {
              final WindowManager windowManager
                  = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
              final DisplayMetrics displayMetrics = new DisplayMetrics();
              windowManager.getDefaultDisplay().getMetrics(displayMetrics);

              final Rect rect = new Rect();
              boundingParent.getWindowVisibleDisplayFrame(rect);

              // We bound the boundaries to be within the screen's coordinates to
              // handle the case where the layout bounds do not match
              // (like when FLAG_LAYOUT_NO_LIMITS is specified)
              topBoundary = Math.max(0, rect.top);
              bottomBoundary = Math.min(rect.bottom, displayMetrics.heightPixels);
            }

            drawTintedTarget();
            requestFocus();
            calculateDimensions();
            if (!visible) {
              expandAnimation.start();
              visible = true;
            }
          }
        });
      }
    };

    getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);

    setFocusableInTouchMode(true);
    setClickable(true);
    setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (listener == null || outerCircleCenter == null || !isInteractable) return;

        final boolean clickedInTarget = Math.abs((int) lastTouchX - targetBounds.centerX()) < targetCircleWidth / 2
                && Math.abs((int) lastTouchY - targetBounds.centerY()) < targetCircleHeight / 2;
        final double distanceToOuterCircleCenter = distance(outerCircleCenter[0], outerCircleCenter[1],
            (int) lastTouchX, (int) lastTouchY);
        final boolean clickedInsideOfOuterCircle = distanceToOuterCircleCenter <= outerCircleRadius;

        if (clickedInTarget) {
          isInteractable = false;
          listener.onTargetClick(TapTargetView.this);
        } else if (clickedInsideOfOuterCircle) {
          listener.onOuterCircleClick(TapTargetView.this);
        } else if (cancelable) {
          isInteractable = false;
          listener.onTargetCancel(TapTargetView.this);
        }
      }
    });

    setOnLongClickListener(new OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        if (listener == null) return false;

        if (targetBounds.contains((int) lastTouchX, (int) lastTouchY)) {
          listener.onTargetLongClick(TapTargetView.this);
          return true;
        }

        return false;
      }
    });
  }

  protected void applyTargetOptions(Context context) {
    shouldTintTarget = target.tintTarget;
    shouldDrawShadow = target.drawShadow;
    cancelable = target.cancelable;

    // We can't clip out portions of a view outline, so if the user specified a transparent
    // target, we need to fallback to drawing a jittered shadow approximation
    if (shouldDrawShadow && Build.VERSION.SDK_INT >= 21 && !target.transparentTarget) {
      outlineProvider = new ViewOutlineProvider() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline(View view, Outline outline) {
          if (outerCircleCenter == null) return;
          outline.setOval(
              (int) (outerCircleCenter[0] - outerCircleRadius), (int) (outerCircleCenter[1] - outerCircleRadius),
              (int) (outerCircleCenter[0] + outerCircleRadius), (int) (outerCircleCenter[1] + outerCircleRadius));
          outline.setAlpha(outerCircleAlpha / 255.0f);
          if (Build.VERSION.SDK_INT >= 22) {
            outline.offset(0, SHADOW_DIM);
          }
        }
      };

      setOutlineProvider(outlineProvider);
      setElevation(SHADOW_DIM);
    }

    if (shouldDrawShadow && outlineProvider == null && Build.VERSION.SDK_INT < 18) {
      setLayerType(LAYER_TYPE_SOFTWARE, null);
    } else {
      setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    final Resources.Theme theme = context.getTheme();
    isDark = UiUtil.themeIntAttr(context, "isLightTheme") == 0;

    final Integer outerCircleColor = target.outerCircleColorInt(context);
    if (outerCircleColor != null) {
      outerCirclePaint.setColor(outerCircleColor);
    } else if (theme != null) {
      outerCirclePaint.setColor(UiUtil.themeIntAttr(context, "colorPrimary"));
    } else {
      outerCirclePaint.setColor(Color.WHITE);
    }

    final Integer targetCircleColor = target.targetCircleColorInt(context);
    if (targetCircleColor != null) {
      targetCirclePaint.setColor(targetCircleColor);
    } else {
      targetCirclePaint.setColor(isDark ? Color.BLACK : Color.WHITE);
    }

    if (target.transparentTarget) {
      targetCirclePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    targetCirclePulsePaint.setColor(targetCirclePaint.getColor());

    final Integer targetDimColor = target.dimColorInt(context);
    if (targetDimColor != null) {
      dimColor = UiUtil.setAlpha(targetDimColor, 0.3f);
    } else {
      dimColor = -1;
    }

    final Integer titleTextColor = target.titleTextColorInt(context);
    if (titleTextColor != null) {
      titlePaint.setColor(titleTextColor);
    } else {
      titlePaint.setColor(isDark ? Color.BLACK : Color.WHITE);
    }

    final Integer descriptionTextColor = target.descriptionTextColorInt(context);
    if (descriptionTextColor != null) {
      descriptionPaint.setColor(descriptionTextColor);
    } else {
      descriptionPaint.setColor(titlePaint.getColor());
    }

    final Integer confirmLabelTextColor = target.confirmLabelTextColorInt(context);
    if (confirmLabelTextColor != null) {
      confirmlabelPaint.setColor(confirmLabelTextColor);
    } else {
      confirmlabelPaint.setColor(titlePaint.getColor());
    }

    if (target.titleTypeface != null) {
      titlePaint.setTypeface(target.titleTypeface);
    }

    if (target.descriptionTypeface != null) {
      descriptionPaint.setTypeface(target.descriptionTypeface);
    }

    if (target.confirmLabelTypeface != null) {
      confirmlabelPaint.setTypeface(target.confirmLabelTypeface);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    onDismiss(false);
  }

  void onDismiss() {
    onDismiss(true);
  }

  void onDismiss(boolean userInitiated) {
    if (isDismissed) return;

    isDismissed = true;

    for (final ValueAnimator animator : animators) {
      animator.cancel();
      animator.removeAllUpdateListeners();
    }

    ViewUtil.removeOnGlobalLayoutListener(getViewTreeObserver(), globalLayoutListener);
    visible = false;

    if (listener != null) {
      listener.onTargetDismissed(this, userInitiated);
    }
  }

  @Override
  protected void onDraw(Canvas c) {
    if (isDismissed || outerCircleCenter == null) return;

    if (topBoundary > 0 && bottomBoundary > 0) {
      c.clipRect(0, topBoundary, getWidth(), bottomBoundary);
    }

    if (dimColor != -1) {
      c.drawColor(dimColor);
    }

    int saveCount;
    outerCirclePaint.setAlpha(outerCircleAlpha);
    if (shouldDrawShadow && outlineProvider == null) {
      saveCount = c.save();
      {
        c.clipPath(outerCirclePath, Region.Op.DIFFERENCE);
        drawJitteredShadow(c);
      }
      c.restoreToCount(saveCount);
    }
    c.drawCircle(outerCircleCenter[0], outerCircleCenter[1], outerCircleRadius, outerCirclePaint);

    targetCirclePaint.setAlpha(targetCircleAlpha);
    if (pulseEnabled && targetCirclePulseAlpha > 0) {
      targetCirclePulsePaint.setAlpha(targetCirclePulseAlpha);
//      c.drawCircle(targetBounds.centerX(), targetBounds.centerY(),
//          targetCirclePulseRadius, targetCirclePulsePaint);
      targetPulseShapeBounds.set(targetBounds.centerX() - targetCirclePulseWidth / 2,
              targetBounds.centerY() - targetCirclePulseHeight / 2,
              targetBounds.centerX() + targetCirclePulseWidth / 2,
              targetBounds.centerY() + targetCirclePulseHeight / 2);
      c.drawRoundRect(targetPulseShapeBounds, targetCirclePulseRadius, targetCirclePulseRadius, targetCirclePulsePaint);
    }
    targetShapeBounds.set(targetBounds.centerX() - targetCircleWidth / 2,
            targetBounds.centerY() - targetCircleHeight / 2,
            targetBounds.centerX() + targetCircleWidth / 2,
            targetBounds.centerY() + targetCircleHeight / 2);
    c.drawRoundRect(targetShapeBounds, targetCircleRadius, targetCircleRadius, targetCirclePaint);
//    c.drawCircle(targetBounds.centerX(), targetBounds.centerY(),
//        targetCircleRadius, targetCirclePaint);

    saveCount = c.save();
    {
      c.translate(textBounds.left, textBounds.top);
      titlePaint.setAlpha(textAlpha);
      if (titleLayout != null) {
        titleLayout.draw(c);
      }

      if (descriptionLayout != null && titleLayout != null) {
        c.translate(0, titleLayout.getHeight() + TEXT_SPACING);
//        descriptionPaint.setAlpha((int) (0.54f * textAlpha));
        descriptionPaint.setAlpha(textAlpha);
        descriptionLayout.draw(c);
      }

      if (confirmLabelLayout != null && descriptionLayout != null) {
        c.translate(0, descriptionLayout.getHeight() + TEXT_SPACING);
        confirmlabelPaint.setAlpha(textAlpha);
        confirmLabelLayout.draw(c);
      }
    }
    c.restoreToCount(saveCount);

    saveCount = c.save();
    {
      if (tintedTarget != null) {
        c.translate(targetBounds.centerX() - tintedTarget.getWidth() / 2,
            targetBounds.centerY() - tintedTarget.getHeight() / 2);
        c.drawBitmap(tintedTarget, 0, 0, targetCirclePaint);
      } else if (target.icon != null) {
        c.translate(targetBounds.centerX() - target.icon.getBounds().width() / 2,
            targetBounds.centerY() - target.icon.getBounds().height() / 2);
        target.icon.setAlpha(targetCirclePaint.getAlpha());
        target.icon.draw(c);
      }
    }
    c.restoreToCount(saveCount);

    if (debug) {
      drawDebugInformation(c);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    lastTouchX = e.getX();
    lastTouchY = e.getY();
    return super.onTouchEvent(e);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (isVisible() && cancelable && keyCode == KeyEvent.KEYCODE_BACK) {
      event.startTracking();
      return true;
    }

    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (isVisible() && isInteractable && cancelable
        && keyCode == KeyEvent.KEYCODE_BACK && event.isTracking() && !event.isCanceled()) {
      isInteractable = false;

      if (listener != null) {
        listener.onTargetCancel(this);
      } else {
        new Listener().onTargetCancel(this);
      }

      return true;
    }

    return false;
  }

  /**
   * Dismiss this view
   * @param tappedTarget If the user tapped the target or not
   *                     (results in different dismiss animations)
   */
  public void dismiss(boolean tappedTarget) {
    pulseAnimation.cancel();
    expandAnimation.cancel();
    if (tappedTarget) {
      dismissConfirmAnimation.start();
    } else {
      dismissAnimation.start();
    }
  }

  /** Specify whether to draw a wireframe around the view, useful for debugging **/
  public void setDrawDebug(boolean status) {
    if (debug != status) {
      debug = status;
      postInvalidate();
    }
  }

  /** Returns whether this view is visible or not **/
  public boolean isVisible() {
    return !isDismissed && visible;
  }

  void drawJitteredShadow(Canvas c) {
    final float baseAlpha = 0.20f * outerCircleAlpha;
    outerCircleShadowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    outerCircleShadowPaint.setAlpha((int) baseAlpha);
    if (outerCircleCenter != null) {
      c.drawCircle(outerCircleCenter[0], outerCircleCenter[1] + SHADOW_DIM, outerCircleRadius, outerCircleShadowPaint);
    }
    outerCircleShadowPaint.setStyle(Paint.Style.STROKE);
    final int numJitters = 7;
    for (int i = numJitters - 1; i > 0; --i) {
      outerCircleShadowPaint.setAlpha((int) ((i / (float) numJitters) * baseAlpha));
      if (outerCircleCenter != null) {
        c.drawCircle(outerCircleCenter[0], outerCircleCenter[1] + SHADOW_DIM,
                outerCircleRadius + (numJitters - i) * SHADOW_JITTER_DIM, outerCircleShadowPaint);
      }
    }
  }

  void drawDebugInformation(Canvas c) {
    if (debugPaint == null) {
      debugPaint = new Paint();
      debugPaint.setARGB(255, 255, 0, 0);
      debugPaint.setStyle(Paint.Style.STROKE);
      debugPaint.setStrokeWidth(UiUtil.dp(getContext(), 1));
    }

    if (debugTextPaint == null) {
      debugTextPaint = new TextPaint();
      debugTextPaint.setColor(0xFFFF0000);
      debugTextPaint.setTextSize(UiUtil.sp(getContext(), 16));
    }

    // Draw wireframe
    debugPaint.setStyle(Paint.Style.STROKE);
    c.drawRect(textBounds, debugPaint);
    c.drawRect(targetBounds, debugPaint);
    c.drawCircle(outerCircleCenter[0], outerCircleCenter[1], 10, debugPaint);
    c.drawCircle(outerCircleCenter[0], outerCircleCenter[1], calculatedOuterCircleRadius - CIRCLE_PADDING, debugPaint);
    c.drawCircle(targetBounds.centerX(), targetBounds.centerY(), TARGET_RADIUS + TARGET_PADDING, debugPaint);

    // Draw positions and dimensions
    debugPaint.setStyle(Paint.Style.FILL);
    final String debugText =
            "Text bounds: " + textBounds.toShortString() + "\n" +
            "Target bounds: " + targetBounds.toShortString() + "\n" +
            "Center: " + outerCircleCenter[0] + " " + outerCircleCenter[1] + "\n" +
            "View size: " + getWidth() + " " + getHeight() + "\n" +
            "Target bounds: " + targetBounds.toShortString();

    if (debugStringBuilder == null) {
      debugStringBuilder = new SpannableStringBuilder(debugText);
    } else {
      debugStringBuilder.clear();
      debugStringBuilder.append(debugText);
    }

    if (debugLayout == null) {
      debugLayout = new DynamicLayout(debugText, debugTextPaint, getWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    final int saveCount = c.save();
    {
      debugPaint.setARGB(220, 0, 0, 0);
      c.translate(0.0f, topBoundary);
      c.drawRect(0.0f, 0.0f, debugLayout.getWidth(), debugLayout.getHeight(), debugPaint);
      debugPaint.setARGB(255, 255, 0, 0);
      debugLayout.draw(c);
    }
    c.restoreToCount(saveCount);
  }

  void drawTintedTarget() {
    final Drawable icon = target.icon;
    if (!shouldTintTarget || icon == null) {
      tintedTarget = null;
      return;
    }

    if (tintedTarget != null) return;

    tintedTarget = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(),
        Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(tintedTarget);
    icon.setColorFilter(new PorterDuffColorFilter(
        outerCirclePaint.getColor(), PorterDuff.Mode.SRC_ATOP));
    icon.draw(canvas);
    icon.setColorFilter(null);
  }

  void updateTextLayouts() {
    final int textWidth = Math.min(getWidth(), TEXT_MAX_WIDTH) - TEXT_PADDING * 2;
    if (textWidth <= 0) {
      return;
    }

    titleLayout = new StaticLayout(title, titlePaint, textWidth,
            Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

    if (description != null) {
      descriptionLayout = new StaticLayout(description, descriptionPaint, textWidth,
              Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    } else {
      descriptionLayout = null;
    }

    if (confirmLabel != null) {
      confirmLabelLayout = new StaticLayout(confirmLabel, confirmlabelPaint, textWidth,
              Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    } else {
      confirmLabelLayout = null;
    }
  }

  float halfwayLerp(float lerp) {
    if (lerp < 0.5f) {
      return lerp / 0.5f;
    }

    return (1.0f - lerp) / 0.5f;
  }

  float delayedLerp(float lerp, float threshold) {
    if (lerp < threshold) {
      return 0.0f;
    }

    return (lerp - threshold) / (1.0f - threshold);
  }

  void calculateDimensions() {
    textBounds = getTextBounds();
    outerCircleCenter = getOuterCircleCenterPoint();
    calculatedOuterCircleRadius = getOuterCircleRadius(outerCircleCenter[0], outerCircleCenter[1], textBounds, targetBounds);
  }

  void calculateDrawingBounds() {
    if (outerCircleCenter == null) {
      return;
    }
    drawingBounds.left = (int) Math.max(0, outerCircleCenter[0] - outerCircleRadius);
    drawingBounds.top = (int) Math.min(0, outerCircleCenter[1] - outerCircleRadius);
    drawingBounds.right = (int) Math.min(getWidth(),
        outerCircleCenter[0] + outerCircleRadius + CIRCLE_PADDING);
    drawingBounds.bottom = (int) Math.min(getHeight(),
        outerCircleCenter[1] + outerCircleRadius + CIRCLE_PADDING);
  }

  int getOuterCircleRadius(int centerX, int centerY, Rect textBounds, Rect targetBounds) {
    final int targetCenterX = targetBounds.centerX();
    final int targetCenterY = targetBounds.centerY();
    final int expandedRadius = (int) (1.1f * TARGET_RADIUS);
    final Rect expandedBounds = new Rect(targetCenterX, targetCenterY, targetCenterX, targetCenterY);
    expandedBounds.inset(-expandedRadius, -expandedRadius);

    final int textRadius = maxDistanceToPoints(centerX, centerY, textBounds);
    final int targetRadius = maxDistanceToPoints(centerX, centerY, expandedBounds);
    return Math.max(textRadius, targetRadius) + CIRCLE_PADDING;
  }

  Rect getTextBounds() {
    final int totalTextHeight = getTotalTextHeight();
    final int totalTextWidth = getTotalTextWidth();

    final int possibleTop = targetBounds.centerY() - TARGET_RADIUS - TARGET_PADDING - totalTextHeight;
    final int top;
    if (possibleTop > topBoundary) {
      top = possibleTop;
    } else {
      top = targetBounds.centerY() + TARGET_RADIUS + TARGET_PADDING;
    }

    final int relativeCenterDistance = (getWidth() / 2) - targetBounds.centerX();
    final int bias = relativeCenterDistance < 0 ? -TEXT_POSITIONING_BIAS : TEXT_POSITIONING_BIAS;
    final int left = Math.max(TEXT_PADDING, targetBounds.centerX() - bias - totalTextWidth);
    final int right = Math.min(getWidth() - TEXT_PADDING, left + totalTextWidth);
    return new Rect(left, top, right, top + totalTextHeight);
  }

  int[] getOuterCircleCenterPoint() {
    if (inGutter(targetBounds.centerY())) {
      return new int[]{targetBounds.centerX(), targetBounds.centerY()};
    }

    final int targetRadius = Math.max(targetBounds.width(), targetBounds.height()) / 2 + TARGET_PADDING;
    final int totalTextHeight = getTotalTextHeight();

    final boolean onTop = targetBounds.centerY() - TARGET_RADIUS - TARGET_PADDING - totalTextHeight > 0;

    final int left = Math.min(textBounds.left, targetBounds.left - targetRadius);
    final int right = Math.max(textBounds.right, targetBounds.right + targetRadius);
    final int titleHeight = titleLayout == null ? 0 : titleLayout.getHeight();
    final int centerY = onTop ?
        targetBounds.centerY() - TARGET_RADIUS - TARGET_PADDING - totalTextHeight + titleHeight
        :
        targetBounds.centerY() + TARGET_RADIUS + TARGET_PADDING + titleHeight;

    return new int[] { (left + right) / 2, centerY };
  }

  int getTotalTextHeight() {
    if (titleLayout == null) {
      return 0;
    }

    if (descriptionLayout == null) {
      return titleLayout.getHeight() + TEXT_SPACING;
    }

    if (confirmLabelLayout == null) {
      return titleLayout.getHeight() + descriptionLayout.getHeight() + TEXT_SPACING;
    }

    return titleLayout.getHeight() + descriptionLayout.getHeight() + confirmLabelLayout.getHeight() + TEXT_SPACING * 2;
  }

  int getTotalTextWidth() {
    if (titleLayout == null) {
      return 0;
    }

    if (descriptionLayout == null) {
      return titleLayout.getWidth();
    }

    int maxTitleDescriptionWidth = Math.max(titleLayout.getWidth(), descriptionLayout.getWidth());
    if (confirmLabelLayout == null) {
      return maxTitleDescriptionWidth;
    }

    return Math.max(maxTitleDescriptionWidth, confirmLabelLayout.getWidth());
  }

  boolean inGutter(int y) {
    if (bottomBoundary > 0) {
      return y < GUTTER_DIM || y > bottomBoundary - GUTTER_DIM;
    } else {
      return y < GUTTER_DIM || y > getHeight() - GUTTER_DIM;
    }
  }

  int maxDistanceToPoints(int x1, int y1, Rect bounds) {
    final double tl = distance(x1, y1, bounds.left, bounds.top);
    final double tr = distance(x1, y1, bounds.right, bounds.top);
    final double bl = distance(x1, y1, bounds.left, bounds.bottom);
    final double br = distance(x1, y1, bounds.right, bounds.bottom);
    return (int) Math.max(tl, Math.max(tr, Math.max(bl, br)));
  }

  double distance(int x1, int y1, int x2, int y2) {
    return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
  }

  void invalidateViewAndOutline(Rect bounds) {
    invalidate(bounds);
    if (outlineProvider != null && Build.VERSION.SDK_INT >= 21) {
      invalidateOutline();
    }
  }
}
