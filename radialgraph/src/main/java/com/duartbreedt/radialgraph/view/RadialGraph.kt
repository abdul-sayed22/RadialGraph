package com.duartbreedt.radialgraph.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.LEFT
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.RIGHT
import androidx.constraintlayout.widget.ConstraintSet.TOP
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.duartbreedt.radialgraph.R
import com.duartbreedt.radialgraph.drawable.GraphDrawable
import com.duartbreedt.radialgraph.drawable.RadialGraphDrawable
import com.duartbreedt.radialgraph.drawable.TrackDrawable
import com.duartbreedt.radialgraph.extensions.addIf
import com.duartbreedt.radialgraph.extensions.toFormattedDecimal
import com.duartbreedt.radialgraph.model.AnimationDirection
import com.duartbreedt.radialgraph.model.Cap
import com.duartbreedt.radialgraph.model.Data
import com.duartbreedt.radialgraph.model.GraphConfig
import com.duartbreedt.radialgraph.model.GraphNode
import com.duartbreedt.radialgraph.model.Section
import java.math.BigDecimal
import java.math.RoundingMode

class RadialGraph : ConstraintLayout {

    //region Properties
    private var graphView: AppCompatImageView? = null
    private var graphDrawable: RadialGraphDrawable? = null
    private val labelViews: MutableList<LabelView> = mutableListOf()
    private val graphConfig: GraphConfig
    //endregion

    companion object {
        private val TAG = RadialGraph::class.simpleName!!

        private const val ANIMATION_DIRECTION_CLOCKWISE = 0
        private const val CAP_STYLE_BUTT = 0
        private const val GRAPH_NODE_NONE = 0

        private const val DEFAULT_ANIMATION_DIRECTION = ANIMATION_DIRECTION_CLOCKWISE
        private const val DEFAULT_CAP_STYLE = CAP_STYLE_BUTT
        private const val DEFAULT_GRAPH_NODE = GRAPH_NODE_NONE
        private const val DEFAULT_ANIMATION_DURATION = 1000
    }

    init {
        clipChildren = false
        clipToPadding = false
    }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.RadialGraph)

        val animationDirectionOrdinal: Int =
            attributes.getInt(R.styleable.RadialGraph_animationDirection, DEFAULT_ANIMATION_DIRECTION)
        val animationDirection: AnimationDirection = AnimationDirection.values()[animationDirectionOrdinal]

        val animationDuration: Long =
            attributes.getInt(R.styleable.RadialGraph_animationDuration, DEFAULT_ANIMATION_DURATION).toLong()

        val labelsEnabled: Boolean = attributes.getBoolean(R.styleable.RadialGraph_labelsEnabled, false)

        @ColorInt val labelsColor: Int? = if (attributes.hasValue(R.styleable.RadialGraph_labelsColor)) {
            attributes.getColor(
                R.styleable.RadialGraph_labelsColor,
                ContextCompat.getColor(context, R.color.label_defaultColor)
            )
        } else null

        val strokeWidth: Float = attributes.getDimension(R.styleable.RadialGraph_strokeWidth, 0f)

        val capStyleOrdinal: Int = attributes.getInt(R.styleable.RadialGraph_capStyle, DEFAULT_CAP_STYLE)
        val capStyle = Cap.values()[capStyleOrdinal]

        val backgroundTrackColor = attributes.getColor(R.styleable.RadialGraph_backgroundTrackColor, View.NO_ID)
        val backgroundTrackDrawable = attributes.getDrawable(R.styleable.RadialGraph_backgroundTrackDrawable)

        val graphNodeOrdinal: Int = attributes.getInt(R.styleable.RadialGraph_graphNode, DEFAULT_GRAPH_NODE)
        val graphNode = GraphNode.values()[graphNodeOrdinal]

        val graphNodeColor: Int = if (attributes.hasValue(R.styleable.RadialGraph_graphNodeColor)) {
            attributes.getColor(
                R.styleable.RadialGraph_graphNodeColor,
                ContextCompat.getColor(context, R.color.node_defaultColor)
            )
        } else {
            if (graphNode != GraphNode.NONE) {
                Log.w(TAG, "No value passed for the `app:graphNodeColor` attribute. It will default to Magenta")
            }

            ContextCompat.getColor(context, R.color.node_defaultColor)
        }

        graphConfig = GraphConfig(
            animationDirection,
            animationDuration,
            labelsEnabled,
            labelsColor,
            strokeWidth,
            capStyle,
            backgroundTrackColor,
            backgroundTrackDrawable,
            graphNode,
            graphNodeColor,
            context.resources.getDimension(R.dimen.node_defaultTextSize)
        )

        attributes.recycle()
    }

    //region Android Lifecycle
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        labelViews.forEach { it.setPosition(graphView!!.height / 2f) }
    }
    //endregion

    //region Public API

    fun setData(data: Data) {
        setGraphView()
        createDrawables(data)
        if (graphConfig.labelsEnabled) {
            addLabelViewsToLayout(data)
        }
    }

    fun setGraphNode(newGraphNodeType: GraphNode) {
        graphConfig.graphNodeType = newGraphNodeType
    }

    /**
     * Set the background track of the graph programmatically.
     *
     * @param [newColor] sets the background track to a solid color.
     * Passing `null` will result in the background track being removed: e.g. `setBackgroundTrack(newColor = null)`
     */
    fun setBackgroundTrack(newColor: Int?) {
        graphConfig.backgroundTrackDrawable = null
        graphConfig.backgroundTrackColor = newColor ?: NO_ID
    }

    /**
     * Set the background track of the graph programmatically.
     *
     * @param [newDrawable] sets the background track to a drawable asset.
     * Passing `null` will result in the background track being removed: e.g. `setBackgroundTrack(newDrawable = null)`
     */
    fun setBackgroundTrack(newDrawable: Drawable?) {
        graphConfig.backgroundTrackColor = NO_ID
        graphConfig.backgroundTrackDrawable = newDrawable
    }

    fun animateIn() {
        graphDrawable!!.animateIn()
    }

    fun animateOut() {
        graphDrawable!!.animateOut()
    }

    //endregion

    //region Helper Functions
    private fun setGraphView() {
        removeGraphView()

        graphView = AppCompatImageView(context).apply { id = ViewCompat.generateViewId() }

        addView(graphView)

        graphView!!.layoutParams = (graphView!!.layoutParams as LayoutParams).apply {
            width = 0
            height = 0
        }

        setConstraints(graphView)
    }

    private fun removeGraphView() {
        if (graphView != null) {
            removeView(graphView)
            graphView = null
        }
    }

    private fun createDrawables(data: Data) {
        graphDrawable = RadialGraphDrawable(graphConfig, data.toSectionStates().reversed())

        val layers = mutableListOf<Drawable>().apply {
            addIf(graphConfig.isBackgroundTrackEnabled, TrackDrawable(graphConfig, graphConfig.backgroundTrackColor, graphConfig.backgroundTrackDrawable))
            add(graphDrawable!!)
        }

        graphView!!.setImageDrawable(LayerDrawable(layers.toTypedArray()))
    }

    private fun addLabelViewsToLayout(data: Data) {
        removeAllLabels()

        var labelStartPositionValue =
            if (graphConfig.isClockwise()) BigDecimal.ONE
            else BigDecimal.ZERO

        for (section in data.sections) {
            context?.let { context ->

                val sectionNormalizedSize: BigDecimal = section.normalizedValue

                val labelPositionValue: Float = calculateLabelPositionValue(section, labelStartPositionValue)

                val labelValue: String = section.label ?: when (section.displayMode) {
                    Section.DisplayMode.PERCENT ->
                        resources.getString(R.string.label_percentPattern, section.percent.toFormattedDecimal())
                    Section.DisplayMode.VALUE ->
                        section.value.toFormattedDecimal()
                }

                @ColorInt
                val labelColor: Int = graphConfig.labelsColor ?: section.color

                val labelView = LabelView(context, labelValue, labelColor, labelPositionValue)

                labelViews.add(labelView)
                addView(labelView)
                setConstraints(labelView)

                labelStartPositionValue =
                    if (graphConfig.isClockwise()) labelStartPositionValue - sectionNormalizedSize
                    else labelStartPositionValue + sectionNormalizedSize
            }
        }
    }

    private fun calculateLabelPositionValue(section: Section, portionStartPositionValue: BigDecimal): Float {
        val halfSectionSize = section.normalizedValue.divide(BigDecimal("2"), 2, RoundingMode.HALF_EVEN)
        val sectionMidpointPosition =
            if (graphConfig.isClockwise()) (portionStartPositionValue - halfSectionSize)
            else (portionStartPositionValue + halfSectionSize)
        return sectionMidpointPosition.toFloat()
    }

    private fun removeAllLabels() {
        labelViews.forEach { removeView(it) }
        labelViews.clear()
    }

    private fun setConstraints(view: View?) {
        ConstraintSet().apply {
            clone(this@RadialGraph)

            view?.id?.let { viewId ->
                setDimensionRatio(viewId, "1:1")
                connect(viewId, LEFT, PARENT_ID, LEFT)
                connect(viewId, TOP, PARENT_ID, TOP)
                connect(viewId, RIGHT, PARENT_ID, RIGHT)
                connect(viewId, BOTTOM, PARENT_ID, BOTTOM)
            }

            applyTo(this@RadialGraph)
        }
    }
    //endregion
}