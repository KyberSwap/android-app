package com.kyberswap.android.presentation.main.balance.chart


import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.R
import com.kyberswap.android.databinding.FragmentLineChartBinding
import com.kyberswap.android.domain.model.Chart
import com.kyberswap.android.domain.model.Token
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.presentation.base.BaseFragment
import com.kyberswap.android.presentation.helper.Navigator
import com.kyberswap.android.presentation.splash.GetWalletState
import com.kyberswap.android.util.di.ViewModelFactory
import com.kyberswap.android.util.ext.toDisplayNumber
import kotlinx.android.synthetic.main.fragment_line_chart.*
import java.math.BigDecimal
import java.util.ArrayList
import javax.inject.Inject

class LineChartFragment : BaseFragment() {

    private lateinit var binding: FragmentLineChartBinding

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var appExecutors: AppExecutors

    private var token: Token? = null

    private var chartType: ChartType? = null

    var changedRate: BigDecimal = BigDecimal.ZERO

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(LineChartViewModel::class.java)
    }

    private val lineWidth by lazy {
        resources.getDimension(R.dimen.line_chart_width)
    }

    private val lineColor by lazy {
        ContextCompat.getColor(context!!, R.color.limit_line_color)
    }

    private val typeFace by lazy {
        Typeface.createFromAsset(activity!!.assets, "fonts/Roboto-Medium.ttf")
    }

    private val lineLimitTextColor by lazy {
        ContextCompat.getColor(context!!, R.color.limit_line_text_color)
    }
    private var wallet: Wallet? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = arguments!!.getParcelable(TOKEN_PARAM)
        chartType = arguments!!.getParcelable(CHART_TYPE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentLineChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.lineChart.setNoDataText(getString(R.string.chart_updating_data))
        viewModel.getSelectedWallet()
        viewModel.getSelectedWalletCallback.observe(parentFragment!!.viewLifecycleOwner, Observer {
            it?.peekContent()?.let { state ->
                when (state) {
                    is GetWalletState.Success -> {
                        viewModel.getChartData(
                            if (state.wallet.unit == getString(R.string.unit_usd)) token?.symbol + "_USDC" else token?.symbol + "_ETH",
                            chartType
                        )
                    }
                    is GetWalletState.ShowError -> {

                    }
                }
            }
        })

        viewModel.getChartCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetChartState.Success -> {
                        configChart(state.chart)
                        setData(state.chart, binding.lineChart)
                    }

                    is GetChartState.ShowError -> {
                        binding.lineChart.setNoDataText(getString(R.string.something_wrong))
                        binding.lineChart.invalidate()
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })
    }

    private fun configChart(chart: Chart) {
        binding.lineChart.setTouchEnabled(true)
        binding.lineChart.isDragEnabled = true
        binding.lineChart.isScaleXEnabled = true
        binding.lineChart.setPinchZoom(true)
        lineChart.animateX(1500)
        lineChart.xAxis.isEnabled = false
        lineChart.axisRight.isEnabled = false
        lineChart.axisLeft.setDrawAxisLine(false)
        lineChart.axisLeft.setDrawGridLines(false)
        lineChart.axisLeft.setDrawLabels(false)
        lineChart.description.isEnabled = false
        lineChart.setViewPortOffsets(0f, 0f, 0f, 0f)
        val formatter =
            XAxisValueFormatter(chart)
        val markerView = CustomMarkerView(
            context!!,
            formatter
        )
        markerView.chartView = lineChart
        lineChart.marker = markerView
        lineChart.invalidate()
    }


    private fun updateChangeRate() {
        val parent = parentFragment
        if (parent is ChartFragment) {
            chartType?.let { parent.updateChangeRate(changedRate, it) }
        }
    }

    private fun setData(chart: Chart, lineChart: LineChart) {
        val chartData = if (chart.c.isEmpty() &&
            (token?.isETH == true || token?.isWETH == true || token?.isETHWETH == true)
        ) {
            listOf(BigDecimal.ONE, BigDecimal.ONE)
        } else {
            chart.c
        }
        val chartEntries = mutableListOf<Entry>()
        if (chartData.isEmpty()) {
            if (token?.isOther == true) {
                lineChart.setNoDataText(getString(R.string.unsupported_token))
            } else {
                lineChart.setNoDataText(getString(R.string.chart_no_token_data))
            }
            return
        }

        lineChart.setNoDataText("")
        val last = chartData.last()
        val first = chartData.first()

        if (first > BigDecimal.ZERO) {
            changedRate = (last - first) / first * BigDecimal(100)
        }
        updateChangeRate()

        chartData.forEachIndexed { index, bigDecimal ->
            chartEntries.add(Entry(index.toFloat(), bigDecimal.toFloat()))
        }

        val dataSet: LineDataSet
        if (lineChart.data != null && lineChart.data.dataSetCount > 0) {
            dataSet = lineChart.data.getDataSetByIndex(0) as LineDataSet
            dataSet.values = chartEntries
            dataSet.notifyDataSetChanged()
            lineChart.data.notifyDataChanged()
            lineChart.notifyDataSetChanged()
        } else {
            dataSet = LineDataSet(chartEntries, token!!.tokenSymbol)
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            dataSet.setDrawIcons(false)
            dataSet.color = ContextCompat.getColor(activity!!, R.color.line_chart_color)
            dataSet.lineWidth = lineWidth
            dataSet.setDrawValues(false)
            dataSet.setDrawCircles(false)
            dataSet.setDrawCircleHole(false)
            dataSet.setDrawFilled(true)
            dataSet.highLightColor = lineLimitTextColor
            dataSet.fillColor = ContextCompat.getColor(activity!!, R.color.line_chart_fill_color)
            val dataSets = ArrayList<ILineDataSet>()
            dataSets.add(dataSet) // add the data sets
            val data = LineData(dataSets)
            lineChart.data = data
            lineChart.notifyDataSetChanged()

            val l = lineChart.legend
            l.isEnabled = false

            val max = chartData.max() ?: BigDecimal.ZERO
            val min = chartData.min() ?: BigDecimal.ZERO

            val ll1 = LimitLine(max.toFloat(), max.toDisplayNumber())
            ll1.lineWidth = lineWidth
            ll1.typeface = typeFace
            ll1.lineColor = lineColor
            ll1.textColor = lineLimitTextColor
            ll1.enableDashedLine(10f, 10f, 0f)
            ll1.labelPosition = LimitLine.LimitLabelPosition.LEFT_TOP

            val ll2 = LimitLine(min.toFloat(), min.toDisplayNumber())
            ll2.labelPosition = LimitLine.LimitLabelPosition.LEFT_BOTTOM
            ll2.typeface = typeFace
            ll2.lineColor = Color.TRANSPARENT
            ll2.textColor = lineLimitTextColor
            lineChart.axisLeft.setDrawLimitLinesBehindData(true)

            // add limit lines
            lineChart.axisLeft.addLimitLine(ll1)
            lineChart.axisLeft.addLimitLine(ll2)
        }
    }

    companion object {
        private const val TOKEN_PARAM = "token_param"
        private const val CHART_TYPE = "chart_type"
        fun newInstance(token: Token?, type: ChartType) =
            LineChartFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(TOKEN_PARAM, token)
                    putParcelable(CHART_TYPE, type)
                }
            }
    }
}
