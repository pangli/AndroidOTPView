package com.zorro.optview

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.MovementMethod
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import com.zorro.optview.DefaultMovementMethod.Companion.instance
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.graphics.withSave
import androidx.core.graphics.drawable.toDrawable

class OtpView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.otpViewStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    private val viewTypeRectangle = 0
    private val viewTypeLine = 1
    private val viewTypeNone = 2
    private val delayMillis = 500L
    private val noFilters = arrayOfNulls<InputFilter>(0)
    private val selectedState = intArrayOf(android.R.attr.state_selected)
    private val filledState = intArrayOf(R.attr.OtpState_filled)
    private var viewType: Int = viewTypeNone
    private var otpViewItemCount: Int = 6
    private var otpViewItemWidth: Int = 48
    private var otpViewItemHeight: Int = 48
    private var otpViewItemRadius: Int = 0
    private var otpViewItemSpacing: Int = 5
    private var paintC: Paint? = null
    private val animatorTextPaint: TextPaint = TextPaint()

    /**
     * Gets the line colors for the different states (normal, selected, focused) of the OtpView.
     *
     * @attr ref R.styleable#OtpView_lineColor
     * @see .setLineColor
     * @see .setLineColor
     */
    private var lineColors: ColorStateList? = null

    /**
     *
     * Return the current color selected for normal line.
     *
     * @return Returns the current item's line color.
     */
    private var currentLineColor: Int = Color.BLACK
    private var lineWidth: Int = 2
    private val textRect = Rect()
    private val itemBorderRect = RectF()
    private val itemLineRect = RectF()
    private val path = Path()
    private val itemCenterPoint = PointF()
    private var defaultAddAnimator: ValueAnimator? = null
    private var isAnimationEnable = false
    private var blink: Blink? = null
    private var isCursorVisible: Boolean = false
    private var drawCursor = false
    private var cursorHeight = 0f
    private var cursorWidth: Int = 2
    private var cursorColor: Int = Color.BLACK
    private var itemBackgroundResource: Int = 0
    private var itemBackground: Drawable? = null
    private var hideLineWhenFilled: Boolean = false
    private var rtlTextDirection: Boolean = false
    private var maskingChar: String? = null
    private var isAllCaps = false
    private var onOtpStateListener: OnOtpStateListener? = null

    init {
        super.setCursorVisible(false)
        val res = resources
        paintC = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        animatorTextPaint.set(paint)
        context.theme.obtainStyledAttributes(attrs, R.styleable.OtpView, defStyleAttr, 0)
            .apply {
                try {
                    viewType = getInt(R.styleable.OtpView_OtpViewType, viewTypeNone)
                    otpViewItemCount = getInt(R.styleable.OtpView_OtpItemCount, otpViewItemCount)
                    otpViewItemHeight = getDimension(
                        R.styleable.OtpView_OtpItemHeight,
                        res.getDimensionPixelSize(R.dimen.otp_view_item_size).toFloat()
                    ).toInt()
                    otpViewItemWidth = getDimension(
                        R.styleable.OtpView_OtpItemWidth,
                        res.getDimensionPixelSize(R.dimen.otp_view_item_size).toFloat()
                    ).toInt()
                    otpViewItemSpacing = getDimensionPixelSize(
                        R.styleable.OtpView_OtpItemSpacing,
                        res.getDimensionPixelSize(R.dimen.otp_view_item_spacing)
                    )
                    otpViewItemRadius = getDimension(R.styleable.OtpView_OtpItemRadius, 0f).toInt()
                    lineWidth = getDimension(
                        R.styleable.OtpView_OtpLineWidth,
                        res.getDimensionPixelSize(R.dimen.otp_view_item_line_width).toFloat()
                    ).toInt()
                    lineColors = getColorStateList(R.styleable.OtpView_OtpLineColor)
                    isCursorVisible = getBoolean(R.styleable.OtpView_android_cursorVisible, false)
                    cursorColor = getColor(R.styleable.OtpView_OtpCursorColor, currentTextColor)
                    cursorWidth = getDimensionPixelSize(
                        R.styleable.OtpView_OtpCursorWidth,
                        res.getDimensionPixelSize(R.dimen.otp_view_cursor_width)
                    )
                    itemBackground = getDrawable(R.styleable.OtpView_android_itemBackground)
                    hideLineWhenFilled =
                        getBoolean(R.styleable.OtpView_OtpHideLineWhenFilled, false)
                    rtlTextDirection = getBoolean(R.styleable.OtpView_OtpRtlTextDirection, false)
                    maskingChar = getString(R.styleable.OtpView_OtpMaskingChar)
                    isAllCaps = getBoolean(R.styleable.OtpView_android_textAllCaps, false)
                } finally {
                    recycle()
                }
            }
        if (lineColors != null) {
            currentLineColor = lineColors?.defaultColor ?: Color.BLACK
        }
        updateCursorHeight()
        checkItemRadius()
        setMaxLength(otpViewItemCount)
        paintC?.strokeWidth = lineWidth.toFloat()
        setupAnimator()
        setTextIsSelectable(false)
    }

    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        paint?.also {
            animatorTextPaint?.set(it)
        }
    }

    private fun setMaxLength(maxLength: Int) {
        setFilters(if (maxLength >= 0) arrayOf<InputFilter>(LengthFilter(maxLength)) else noFilters)
    }

    private fun setupAnimator() {
        defaultAddAnimator = ValueAnimator.ofFloat(0.5f, 1f)
        defaultAddAnimator?.setDuration(150)
        defaultAddAnimator?.interpolator = DecelerateInterpolator()
        defaultAddAnimator?.addUpdateListener { animation ->
            val scale = animation.getAnimatedValue() as Float
            val alpha = (255 * scale).toInt()
            animatorTextPaint.textSize = textSize * scale
            animatorTextPaint.setAlpha(alpha)
            postInvalidate()
        }
    }

    private fun checkItemRadius() {
        when (viewType) {
            viewTypeLine -> {
                val halfOfLineWidth = lineWidth / 2.0f
                require(!(otpViewItemRadius > halfOfLineWidth)) {
                    "The itemRadius can not be greater than lineWidth when viewType is line"
                }
            }

            viewTypeRectangle -> {
                val halfOfItemWidth = otpViewItemWidth / 2.0f
                require(!(otpViewItemRadius > halfOfItemWidth)) {
                    "The itemRadius can not be greater than itemWidth"
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val paddingH = ViewCompat.getPaddingStart(this) + ViewCompat.getPaddingEnd(this)
        val paddingV = paddingTop + paddingBottom

        // ---- width ----
        val desiredContentWidth =
            otpViewItemCount * otpViewItemWidth + (otpViewItemCount - 1) * otpViewItemSpacing

        val desiredWidth = desiredContentWidth + paddingH
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> {
                // 父布局给定宽度 → 必须用
                recalcItemWidthIfNeeded(widthSize, paddingH)
                widthSize
            }

            MeasureSpec.AT_MOST -> {
                // wrap_content → 不能超过父布局给的最大宽度
                if (desiredWidth <= widthSize) {
                    desiredWidth
                } else {
                    // 超了 → 用最大宽度，并反算 itemWidth
                    recalcItemWidthIfNeeded(widthSize, paddingH)
                    widthSize
                }
            }

            else -> {
                // UNSPECIFIED（极少）
                desiredWidth
            }
        }
        // ---- height ----
        val height = if (heightMode == MeasureSpec.EXACTLY)
            heightSize
        else
            otpViewItemHeight + paddingV
        setMeasuredDimension(width, height)
    }

    private fun recalcItemWidthIfNeeded(totalWidth: Int, paddingH: Int) {
        val availableWidth = totalWidth - paddingH
        val totalSpacing = (otpViewItemCount - 1) * otpViewItemSpacing

        val calculatedItemWidth =
            (availableWidth - totalSpacing) / otpViewItemCount

        if (calculatedItemWidth > 0) {
            otpViewItemWidth = calculatedItemWidth
        }
    }

    override fun onTextChanged(
        text: CharSequence,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        onOtpStateListener?.onTextChanged(text)
        if (start != text.length) {
            moveSelectionToEnd()
        }
        if (text.length == otpViewItemCount && onOtpStateListener != null) {
            onOtpStateListener?.onOtpCompleted(text.toString())
        }
        makeBlink()
        if (isAnimationEnable) {
            val isAdd = lengthAfter - lengthBefore > 0
            if (isAdd && defaultAddAnimator != null) {
                defaultAddAnimator?.end()
                defaultAddAnimator?.start()
            }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        onOtpStateListener?.onFocusChanged(focused)
        if (focused) {
            moveSelectionToEnd()
            makeBlink()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        text?.also {
            if (selEnd != it.length) {
                moveSelectionToEnd()
            }
        }
    }

    private fun moveSelectionToEnd() {
        text?.also {
            setSelection(it.length)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (lineColors == null || lineColors?.isStateful == true) {
            updateColors()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withSave {
            updatePaints()
            drawOtpView(this)
        }
    }

    private fun updatePaints() {
        paintC?.apply {
            setColor(currentLineColor)
            style = Paint.Style.STROKE
            strokeWidth = lineWidth.toFloat()
        }
        paint.setColor(currentTextColor)
    }

    private fun drawOtpView(canvas: Canvas) {
        val nextItemToFill: Int = if (rtlTextDirection) {
            otpViewItemCount - 1
        } else {
            text?.length ?: 0
        }
        for (i in 0..<otpViewItemCount) {
            val itemSelected = isFocused && nextItemToFill == i
            val itemFilled = i < nextItemToFill
            var itemState: IntArray? = null
            if (itemFilled) {
                itemState = filledState
            } else if (itemSelected) {
                itemState = selectedState
            }
            paintC?.setColor(if (itemState != null) getLineColorForState(*itemState) else currentLineColor)
            updateItemRectF(i)
            updateCenterPoint()
            canvas.withSave {
                if (viewType == viewTypeRectangle) {
                    updateOtpViewBoxPath(i)
                    clipPath(path)
                }
                drawItemBackground(this, itemState)
            }
            if (itemSelected) {
                drawCursor(canvas)
            }
            if (viewType == viewTypeRectangle) {
                drawOtpBox(canvas, i)
            } else if (viewType == viewTypeLine) {
                drawOtpLine(canvas, i)
            }
            if (rtlTextDirection) {
                val reversedPosition = otpViewItemCount - i
                text?.let {
                    if (it.length >= reversedPosition) {
                        drawInput(canvas, i)
                    }
                } ?: {
                    if (!TextUtils.isEmpty(hint) && hint.length == otpViewItemCount) {
                        drawHint(canvas, i)
                    }
                }
            } else {
                text?.let {
                    if (it.length > i) {
                        drawInput(canvas, i)
                    }
                } ?: {
                    if (!TextUtils.isEmpty(hint) && hint.length == otpViewItemCount) {
                        drawHint(canvas, i)
                    }
                }
            }
        }
        if (isFocused && text != null && text?.length != otpViewItemCount && viewType == viewTypeRectangle) {
            val index = text?.length ?: 0
            updateItemRectF(index)
            updateCenterPoint()
            updateOtpViewBoxPath(index)
            paintC?.setColor(getLineColorForState(*selectedState))
            drawOtpBox(canvas, index)
        }
    }

    private fun drawInput(canvas: Canvas, i: Int) {
        //allows masking for all number keyboard
        if (maskingChar != null && (isNumberInputType(inputType) || isPasswordInputType(inputType))) {
            drawMaskingText(canvas, i, maskingChar?.get(0).toString())
        } else if (isPasswordInputType(inputType)) {
            drawCircle(canvas, i)
        } else {
            drawText(canvas, i)
        }
    }

    private fun getLineColorForState(vararg states: Int): Int {
        return lineColors?.getColorForState(
            states,
            currentLineColor
        ) ?: currentLineColor
    }

    private fun drawItemBackground(canvas: Canvas, backgroundState: IntArray?) {
        if (itemBackground == null) {
            return
        }
        val delta = lineWidth / 2.0f
        val left = (itemBorderRect.left - delta).roundToInt()
        val top = (itemBorderRect.top - delta).roundToInt()
        val right = (itemBorderRect.right + delta).roundToInt()
        val bottom = (itemBorderRect.bottom + delta).roundToInt()
        itemBackground?.setBounds(left, top, right, bottom)
        if (viewType != viewTypeNone) {
            itemBackground?.setState(backgroundState ?: drawableState)
        }
        itemBackground?.draw(canvas)
    }

    private fun updateOtpViewBoxPath(i: Int) {
        var drawRightCorner = false
        var drawLeftCorner = false
        if (otpViewItemSpacing != 0) {
            drawRightCorner = true
            drawLeftCorner = true
        } else {
            if (i == 0 && 0 != otpViewItemCount - 1) {
                drawLeftCorner = true
            }
            if (i == otpViewItemCount - 1 && i != 0) {
                drawRightCorner = true
            }
        }
        updateRoundRectPath(
            itemBorderRect,
            otpViewItemRadius.toFloat(),
            otpViewItemRadius.toFloat(),
            drawLeftCorner,
            drawRightCorner
        )
    }

    private fun drawOtpBox(canvas: Canvas, i: Int) {
        text?.let {
            if (hideLineWhenFilled && i < it.length) {
                return
            }
        }
        paintC?.also {
            canvas.drawPath(path, it)
        }
    }

    private fun drawOtpLine(canvas: Canvas, i: Int) {
        if (text != null && hideLineWhenFilled && text?.length?.let { i < it } == true) {
            return
        }
        var drawLeft = true
        var drawRight = true
        if (otpViewItemSpacing == 0 && otpViewItemCount > 1) {
            when (i) {
                0 -> {
                    drawRight = false
                }

                otpViewItemCount - 1 -> {
                    drawLeft = false
                }

                else -> {
                    drawRight = false
                    drawLeft = false
                }
            }
        }
        paintC?.apply {
            style = Paint.Style.FILL
            strokeWidth = lineWidth / 10.0f
        }
        val halfLineWidth = lineWidth / 2.0f
        itemLineRect.set(
            itemBorderRect.left - halfLineWidth,
            itemBorderRect.bottom - halfLineWidth,
            itemBorderRect.right + halfLineWidth,
            itemBorderRect.bottom + halfLineWidth
        )

        updateRoundRectPath(
            itemLineRect,
            otpViewItemRadius.toFloat(),
            otpViewItemRadius.toFloat(),
            drawLeft,
            drawRight
        )
        paintC?.also {
            canvas.drawPath(path, it)
        }
    }

    private fun drawCursor(canvas: Canvas) {
        if (drawCursor) {
            val cx = itemCenterPoint.x
            val cy = itemCenterPoint.y
            val y = cy - cursorHeight / 2
            paintC?.apply {
                val color = color
                val width = strokeWidth
                setColor(cursorColor)
                strokeWidth = cursorWidth.toFloat()
                canvas.drawLine(cx, y, cx, y + cursorHeight, this)
                setColor(color)
                strokeWidth = width
            }
        }
    }

    private fun updateRoundRectPath(rectF: RectF, rx: Float, ry: Float, l: Boolean, r: Boolean) {
        updateRoundRectPath(rectF, rx, ry, l, r, r, l)
    }

    private fun updateRoundRectPath(
        rectF: RectF, rx: Float, ry: Float,
        tl: Boolean, tr: Boolean, br: Boolean, bl: Boolean
    ) {
        path.reset()
        val l = rectF.left
        val t = rectF.top
        val r = rectF.right
        val b = rectF.bottom
        val w = r - l
        val h = b - t
        val lw = w - 2 * rx
        val lh = h - 2 * ry
        path.moveTo(l, t + ry)
        if (tl) {
            path.rQuadTo(0f, -ry, rx, -ry)
        } else {
            path.rLineTo(0f, -ry)
            path.rLineTo(rx, 0f)
        }
        path.rLineTo(lw, 0f)
        if (tr) {
            path.rQuadTo(rx, 0f, rx, ry)
        } else {
            path.rLineTo(rx, 0f)
            path.rLineTo(0f, ry)
        }
        path.rLineTo(0f, lh)
        if (br) {
            path.rQuadTo(0f, ry, -rx, ry)
        } else {
            path.rLineTo(0f, ry)
            path.rLineTo(-rx, 0f)
        }
        path.rLineTo(-lw, 0f)
        if (bl) {
            path.rQuadTo(-rx, 0f, -rx, -ry)
        } else {
            path.rLineTo(-rx, 0f)
            path.rLineTo(0f, -ry)
        }
        path.rLineTo(0f, -lh)
        path.close()
    }

    private fun updateItemRectF(i: Int) {
        val halfLineWidth = lineWidth / 2.0f
        var left =
            scrollX + ViewCompat.getPaddingStart(this) + i * (otpViewItemSpacing + otpViewItemWidth) + halfLineWidth
        if (otpViewItemSpacing == 0 && i > 0) {
            left -= (lineWidth) * i
        }
        val right = left + otpViewItemWidth - lineWidth
        val top = scrollY + paddingTop + halfLineWidth
        val bottom = top + otpViewItemHeight - lineWidth
        itemBorderRect.set(left, top, right, bottom)
    }

    private fun drawText(canvas: Canvas, i: Int) {
        val paint = getPaintByIndex(i)
        paint.setColor(currentTextColor)
        if (rtlTextDirection) {
            val reversedPosition = otpViewItemCount - i
            var reversedCharPosition = 0
            if (text == null) {
                reversedCharPosition = reversedPosition
            } else {
                text?.length?.let { reversedCharPosition = reversedPosition - it }
            }
            text?.also {
                if (reversedCharPosition <= 0) {
                    drawTextAtBox(canvas, paint, it, abs(reversedCharPosition))
                }
            }
        } else if (text != null) {
            text?.also {
                drawTextAtBox(canvas, paint, it, i)
            }
        }
    }

    private fun drawMaskingText(canvas: Canvas, i: Int, maskingChar: String) {
        val paint = getPaintByIndex(i)
        paint.setColor(currentTextColor)
        if (rtlTextDirection) {
            val reversedPosition = otpViewItemCount - i
            val reversedCharPosition = if (text == null) {
                reversedPosition
            } else {
                text?.length?.let { reversedPosition - it } ?: 0
            }
            if (reversedCharPosition <= 0 && text != null) {
                drawTextAtBox(
                    canvas, paint, text.toString().replace(".".toRegex(), maskingChar),
                    abs(reversedCharPosition)
                )
            }
        } else if (text != null) {
            drawTextAtBox(
                canvas,
                paint,
                text.toString().replace(".".toRegex(), maskingChar),
                i
            )
        }
    }

    private fun drawHint(canvas: Canvas, i: Int) {
        val paint = getPaintByIndex(i)
        paint.setColor(currentHintTextColor)
        if (rtlTextDirection) {
            val reversedPosition = otpViewItemCount - i
            val reversedCharPosition = reversedPosition - hint.length
            if (reversedCharPosition <= 0) {
                drawTextAtBox(canvas, paint, hint, abs(reversedCharPosition))
            }
        } else {
            drawTextAtBox(canvas, paint, hint, i)
        }
    }

    private fun drawTextAtBox(canvas: Canvas, paint: Paint, text: CharSequence, charAt: Int) {
        paint.getTextBounds(text.toString(), charAt, charAt + 1, textRect)
        val cx = itemCenterPoint.x
        val cy = itemCenterPoint.y
        val x = cx - textRect.width() / 2.0f - textRect.left
        val y = cy + textRect.height() / 2.0f - textRect.bottom
        if (isAllCaps) {
            canvas.drawText(
                text.toString().uppercase(Locale.getDefault()),
                charAt,
                charAt + 1,
                x,
                y,
                paint
            )
        } else {
            canvas.drawText(text, charAt, charAt + 1, x, y, paint)
        }
    }

    private fun drawCircle(canvas: Canvas, i: Int) {
        val paint = getPaintByIndex(i)
        val cx = itemCenterPoint.x
        val cy = itemCenterPoint.y
        if (rtlTextDirection) {
            val reversedItemPosition = otpViewItemCount - i
            val reversedCharPosition = reversedItemPosition - hint.length
            if (reversedCharPosition <= 0) {
                canvas.drawCircle(cx, cy, paint.textSize / 2, paint)
            }
        } else {
            canvas.drawCircle(cx, cy, paint.textSize / 2, paint)
        }
    }

    private fun getPaintByIndex(i: Int): Paint {
        if (text != null && isAnimationEnable && i == text?.length?.minus(1)) {
            animatorTextPaint.setColor(paint.color)
            return animatorTextPaint
        } else {
            return paint
        }
    }

    private fun updateColors() {
        var shouldInvalidate = false
        val color = lineColors?.getColorForState(drawableState, 0) ?: currentTextColor
        if (color != currentLineColor) {
            currentLineColor = color
            shouldInvalidate = true
        }
        if (shouldInvalidate) {
            invalidate()
        }
    }

    private fun updateCenterPoint() {
        val cx = itemBorderRect.left + itemBorderRect.width() / 2.0f
        val cy = itemBorderRect.top + itemBorderRect.height() / 2.0f
        itemCenterPoint.set(cx, cy)
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val variation =
            inputType and (EditorInfo.TYPE_MASK_CLASS or EditorInfo.TYPE_MASK_VARIATION)
        return (variation == (EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD))
                || (variation == (EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (variation == (EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    private fun isNumberInputType(inputType: Int): Boolean {
        return inputType == EditorInfo.TYPE_CLASS_NUMBER
    }

    override fun getDefaultMovementMethod(): MovementMethod {
        return instance
    }

    /**
     * Sets the line color for all the states (normal, selected,
     * focused) to be this color.
     *
     * @param color A color value in the form 0xAARRGGBB.
     * Do not pass a resource ID. To get a color value from a resource ID, call
     * [getColor][androidx.core.content.ContextCompat.getColor].
     * @attr ref R.styleable#OtpView_lineColor
     * @see .setLineColor
     * @see .getLineColors
     */
    fun setLineColor(@ColorInt color: Int) {
        lineColors = ColorStateList.valueOf(color)
        updateColors()
    }

    /**
     * Sets the line color.
     *
     * @attr ref R.styleable#OtpView_lineColor
     * @see .setLineColor
     * @see .getLineColors
     */
    fun setLineColor(colors: ColorStateList) {
        lineColors = colors
        updateColors()
    }

    /**
     * Sets the line width.
     *
     * @attr ref R.styleable#OtpView_lineWidth
     * @see .getLineWidth
     */
    fun setLineWidth(@Px borderWidth: Int) {
        lineWidth = borderWidth
        checkItemRadius()
        requestLayout()
    }

    /**
     * @return Returns the width of the item's line.
     * @see .setLineWidth
     */
    fun getLineWidth(): Int {
        return lineWidth
    }

    var itemCount: Int
        /**
         * @return Returns the count of items.
         * @see .setItemCount
         */
        get() = otpViewItemCount
        /**
         * Sets the count of items.
         *
         * @attr ref R.styleable#OtpView_itemCount
         * @see .getItemCount
         */
        set(count) {
            otpViewItemCount = count
            setMaxLength(count)
            requestLayout()
        }

    var itemRadius: Int
        /**
         * @return Returns the radius of square.
         * @see .setItemRadius
         */
        get() = otpViewItemRadius
        /**
         * Sets the radius of square.
         *
         * @attr ref R.styleable#OtpView_itemRadius
         * @see .getItemRadius
         */
        set(itemRadius) {
            otpViewItemRadius = itemRadius
            checkItemRadius()
            requestLayout()
        }

    @get:Px
    var itemSpacing: Int
        /**
         * @return Returns the spacing between two items.
         * @see .setItemSpacing
         */
        get() = otpViewItemSpacing
        /**
         * Specifies extra space between two items.
         *
         * @attr ref R.styleable#OtpView_itemSpacing
         * @see .getItemSpacing
         */
        set(itemSpacing) {
            otpViewItemSpacing = itemSpacing
            requestLayout()
        }

    var itemHeight: Int
        /**
         * @return Returns the height of item.
         * @see .setItemHeight
         */
        get() = otpViewItemHeight
        /**
         * Sets the height of item.
         *
         * @attr ref R.styleable#OtpView_itemHeight
         * @see .getItemHeight
         */
        set(itemHeight) {
            otpViewItemHeight = itemHeight
            updateCursorHeight()
            requestLayout()
        }

    var itemWidth: Int
        /**
         * @return Returns the width of item.
         * @see .setItemWidth
         */
        get() = otpViewItemWidth
        /**
         * Sets the width of item.
         *
         * @attr ref R.styleable#OtpView_itemWidth
         * @see .getItemWidth
         */
        set(itemWidth) {
            otpViewItemWidth = itemWidth
            checkItemRadius()
            requestLayout()
        }

    /**
     * Specifies whether the text animation should be enabled or disabled.
     * By the default, the animation is disabled.
     *
     * @param enable True to start animation when adding text, false to transition immediately
     */
    fun setAnimationEnable(enable: Boolean) {
        isAnimationEnable = enable
    }

    /**
     * Specifies whether the line (border) should be hidden or visible when text entered.
     * By the default, this flag is false and the line is always drawn.
     *
     * @param hideLineWhenFilled true to hide line on a position where text entered,
     * false to always show line
     * @attr ref R.styleable#OtpView_hideLineWhenFilled
     */
    fun setHideLineWhenFilled(hideLineWhenFilled: Boolean) {
        this.hideLineWhenFilled = hideLineWhenFilled
    }

    override fun setTextSize(size: Float) {
        super.setTextSize(size)
        updateCursorHeight()
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        updateCursorHeight()
    }

    fun setOtpCompletionListener(otpCompletionListener: OnOtpStateListener) {
        this.onOtpStateListener = otpCompletionListener
    }

    //region ItemBackground
    /**
     * Set the item background to a given resource. The resource should refer to
     * a Drawable object or 0 to remove the item background.
     *
     * @param resId The identifier of the resource.
     * @attr ref R.styleable#OtpView_android_itemBackground
     */
    fun setItemBackgroundResources(@DrawableRes resId: Int) {
        if (resId != 0 && itemBackgroundResource != resId) {
            return
        }
        itemBackground = ResourcesCompat.getDrawable(resources, resId, context.theme)
        setItemBackground(itemBackground)
        itemBackgroundResource = resId
    }

    /**
     * Sets the item background color for this view.
     *
     * @param color the color of the item background
     */
    fun setItemBackgroundColor(@ColorInt color: Int) {
        if (itemBackground is ColorDrawable) {
            ((itemBackground as ColorDrawable).mutate() as ColorDrawable).color = color
            itemBackgroundResource = 0
        } else {
            setItemBackground(color.toDrawable())
        }
    }

    /**
     * Set the item background to a given Drawable, or remove the background.
     *
     * @param background The Drawable to use as the item background, or null to remove the
     * item background
     */
    fun setItemBackground(background: Drawable?) {
        itemBackgroundResource = 0
        itemBackground = background
        invalidate()
    }

    //endregion
    //region Cursor
    /**
     * Sets the width (in pixels) of cursor.
     *
     * @attr ref R.styleable#OtpView_cursorWidth
     * @see .getCursorWidth
     */
    fun setCursorWidth(@Px width: Int) {
        cursorWidth = width
        if (isCursorVisible) {
            invalidateCursor(true)
        }
    }

    /**
     * @return Returns the width (in pixels) of cursor.
     * @see .setCursorWidth
     */
    fun getCursorWidth(): Int {
        return cursorWidth
    }

    /**
     * Sets the cursor color.
     *
     * @param color A color value in the form 0xAARRGGBB.
     * Do not pass a resource ID. To get a color value from a resource ID, call
     * [getColor][androidx.core.content.ContextCompat.getColor].
     * @attr ref R.styleable#OtpView_cursorColor
     * @see .getCursorColor
     */
    fun setCursorColor(@ColorInt color: Int) {
        cursorColor = color
        if (isCursorVisible) {
            invalidateCursor(true)
        }
    }

    /**
     * Gets the cursor color.
     *
     * @return Return current cursor color.
     * @see .setCursorColor
     */
    fun getCursorColor(): Int {
        return cursorColor
    }

    fun setMaskingChar(maskingChar: String?) {
        this.maskingChar = maskingChar
        requestLayout()
    }

    fun getMaskingChar(): String? {
        return maskingChar
    }

    override fun setCursorVisible(visible: Boolean) {
        if (isCursorVisible != visible) {
            isCursorVisible = visible
            invalidateCursor(isCursorVisible)
            makeBlink()
        }
    }

    override fun isCursorVisible(): Boolean {
        return isCursorVisible
    }

    override fun onScreenStateChanged(screenState: Int) {
        super.onScreenStateChanged(screenState)
        if (screenState == SCREEN_STATE_ON) {
            resumeBlink()
        } else if (screenState == SCREEN_STATE_OFF) {
            suspendBlink()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resumeBlink()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        suspendBlink()
    }

    private fun shouldBlink(): Boolean {
        return isCursorVisible && isFocused && isEnabled
    }

    private fun makeBlink() {
        if (shouldBlink()) {
            if (blink == null) {
                blink = Blink()
            }
            removeCallbacks(blink)
            drawCursor = false
            postDelayed(blink, delayMillis)
        } else {
            if (blink != null) {
                removeCallbacks(blink)
            }
        }
    }

    private fun suspendBlink() {
        if (blink != null) {
            blink?.cancel()
            invalidateCursor(false)
        }
    }

    private fun resumeBlink() {
        if (blink != null) {
            blink?.unCancel()
            makeBlink()
        }
    }

    private fun invalidateCursor(showCursor: Boolean) {
        if (drawCursor != showCursor) {
            drawCursor = showCursor
            invalidate()
        }
    }

    private fun updateCursorHeight() {
        val delta = 2 * dpToPx()
        cursorHeight = if (otpViewItemHeight - textSize > delta) textSize + delta else textSize
    }

    private inner class Blink : Runnable {
        private var cancelled = false

        override fun run() {
            if (cancelled) {
                return
            }

            removeCallbacks(this)

            if (shouldBlink()) {
                invalidateCursor(!drawCursor)
                postDelayed(this, delayMillis)
            }
        }

        fun cancel() {
            if (!cancelled) {
                removeCallbacks(this)
                cancelled = true
            }
        }

        fun unCancel() {
            cancelled = false
        }
    }

    private fun dpToPx(): Int {
        return (2f * resources.displayMetrics.density + 0.5f).toInt()
    }
}