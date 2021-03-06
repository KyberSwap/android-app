package com.kyberswap.android.domain.model

import com.kyberswap.android.data.api.chart.ChartResponseEntity
import java.math.BigDecimal

data class Chart(
    val t: List<BigDecimal> = listOf(),
    val o: List<BigDecimal> = listOf(),
    val h: List<BigDecimal> = listOf(),
    val l: List<BigDecimal> = listOf(),
    val c: List<BigDecimal> = listOf(),
    val v: List<BigDecimal> = listOf(),
    val s: String = ""

) {
    constructor(chartResponseEntity: ChartResponseEntity) :
        this(
            chartResponseEntity.t ?: listOf(),
            chartResponseEntity.o ?: listOf(),
            chartResponseEntity.h ?: listOf(),
            chartResponseEntity.l ?: listOf(),
            chartResponseEntity.c ?: listOf(),
            chartResponseEntity.v ?: listOf(),
            chartResponseEntity.s
        )
}